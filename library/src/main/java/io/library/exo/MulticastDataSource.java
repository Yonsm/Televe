package io.library.exo;

import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiManager;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Enumeration;

import io.library.client.RtpPacket;

/** 接收 IPTV 组播流：rtp:// 按 RTP 解包，igmp:// 兼容裸 TS。 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class MulticastDataSource extends BaseDataSource {
    private static final int DEFAULT_PORT = 5140;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_PACKET_SIZE = 65535;
    private static final int RECEIVE_BUFFER_SIZE = 2 * 1024 * 1024;

    private final Context mContext;
    private final byte[] mPacket = new byte[MAX_PACKET_SIZE];
    private final DatagramPacket mDatagram = new DatagramPacket(mPacket, mPacket.length);
    private MulticastSocket mSocket;
    private WifiManager.MulticastLock mMulticastLock;
    private DataSpec mDataSpec;
    private int mOffset;
    private int mLength;
    private boolean mOpened;

    public MulticastDataSource(Context context) {
        super(true);
        mContext = context.getApplicationContext();
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        close();
        mDataSpec = dataSpec;
        transferInitializing(dataSpec);
        try {
            openSocket(dataSpec.uri);
            acquireMulticastLock();
        } catch (IOException | RuntimeException error) {
            close();
            throw error instanceof IOException ? (IOException) error : new IOException(error);
        }
        mOpened = true;
        transferStarted(dataSpec);
        return C.LENGTH_UNSET;
    }

    private void openSocket(Uri uri) throws IOException {
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_PORT;
        // 省略主机（udp://@:port）时绑定端口接收任意来源，由负载判断 RTP 或裸 TS。
        InetAddress address = host == null || host.isEmpty() ? null : InetAddress.getByName(host);
        MulticastSocket socket = new MulticastSocket(null);
        try {
            socket.setReuseAddress(true);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.bind(new InetSocketAddress(port));
            try {
                socket.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
            } catch (IOException ignored) {
                // Keep the platform default when the kernel rejects the request.
            }
            if (address != null && address.isMulticastAddress()) joinGroup(socket, address, port);
            mSocket = socket;
        } catch (IOException | RuntimeException error) {
            socket.close();
            throw error instanceof IOException ? (IOException) error : new IOException(error);
        }
    }

    /** 逐接口加入组播组，避免默认路由落在 Wi-Fi 而 IPTV 走有线口时收不到流。 */
    private static void joinGroup(MulticastSocket socket, InetAddress group, int port) throws IOException {
        InetSocketAddress target = new InetSocketAddress(group, port);
        IOException failure = null;
        int joined = 0;
        Enumeration<NetworkInterface> interfaces = null;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (IOException ignored) {
        }
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            try {
                if (network.isUp() && !network.isLoopback() && network.supportsMulticast()) {
                    socket.joinGroup(target, network);
                    joined++;
                }
            } catch (IOException error) {
                failure = error;
            } catch (RuntimeException ignored) {
                // Some vendor interfaces throw while probing; keep the remaining ones.
            }
        }
        if (joined == 0) {
            try {
                socket.joinGroup(group);
            } catch (IOException error) {
                throw failure != null ? failure : error;
            }
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (mSocket == null) return C.RESULT_END_OF_INPUT;
        while (mLength <= 0) receive();
        int count = Math.min(mLength, length);
        System.arraycopy(mPacket, mOffset, buffer, offset, count);
        mOffset += count;
        mLength -= count;
        bytesTransferred(count);
        return count;
    }

    private void receive() throws IOException {
        long deadline = System.nanoTime() + READ_TIMEOUT_MS * 1_000_000L;
        while (true) {
            mDatagram.setLength(mPacket.length);
            try {
                mSocket.receive(mDatagram);
            } catch (SocketTimeoutException error) {
                throw new IOException("Multicast stream timeout: " + mDataSpec.uri, error);
            }
            int size = mDatagram.getLength();
            int payload = 0;
            boolean valid = false;
            if (size >= 12 && (mPacket[0] & 0xC0) == 0x80) {
                payload = RtpPacket.parse(mPacket, size);
                if (payload >= 0) {
                    if ((mPacket[0] & 0x20) != 0) size -= mPacket[size - 1] & 0xFF;
                    valid = size > payload;
                }
            } else if (size > 0 && mPacket[0] == 0x47) {
                valid = true;
            }
            if (valid) {
                mOffset = payload;
                mLength = size - payload;
                return;
            }
            // 端口持续收到无效包时 receive 不会超时，用截止时间兜底。
            if (System.nanoTime() - deadline > 0)
                throw new IOException("Multicast stream timeout: " + mDataSpec.uri);
        }
    }

    @Override
    public void close() {
        mLength = 0;
        mOffset = 0;
        MulticastSocket socket = mSocket;
        mSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (RuntimeException ignored) {
            }
        }
        releaseMulticastLock();
        if (mOpened) {
            mOpened = false;
            transferEnded();
        }
    }

    @Nullable
    @Override
    public Uri getUri() {
        return mDataSpec != null ? mDataSpec.uri : null;
    }

    /** Wi-Fi 驱动默认过滤组播包，需持锁接收；每个实例独立持锁，由系统按标签计数。 */
    private void acquireMulticastLock() {
        if (mMulticastLock != null) return;
        try {
            WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            WifiManager.MulticastLock lock = wifi.createMulticastLock("televe");
            lock.setReferenceCounted(false);
            lock.acquire();
            mMulticastLock = lock;
        } catch (RuntimeException ignored) {
            // Wired multicast can still work when Wi-Fi lock acquisition is unavailable.
        }
    }

    private void releaseMulticastLock() {
        WifiManager.MulticastLock lock = mMulticastLock;
        mMulticastLock = null;
        if (lock != null && lock.isHeld()) {
            try {
                lock.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static final class Factory implements DataSource.Factory {
        private final Context mContext;

        public Factory(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public DataSource createDataSource() {
            return new MulticastDataSource(mContext);
        }
    }
}
