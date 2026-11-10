package io.library.client;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleRtspClient extends SimpleBaseClient {
    protected static final String PROTOCOL_VERSION = "RTSP/1.0";
    protected static final String CSEQ = "CSeq";
    protected static final String SESSION = "Session";

    private boolean mSetup;
    private String mSession;
    private final AtomicInteger mCSeq = new AtomicInteger();

    @Override
    public Response open(Request request) throws IOException {
        return open(request, 0);
    }

    private Response open(Request request, int redirects) throws IOException {
        super.connect(request);

        sendDescribe();
        Response response = receiveHeaders();
        if (response.shouldRedirect()) {
            String location = findLocationOrThrow(response);
            if (redirects >= 5) // 防止源站重定向成环导致递归溢出
                throw new IOException("Too many redirects: " + request.url);
            request.url = URI.create(request.url).resolve(location).toString();
            close();
            return open(request, redirects + 1);
        }
        response.throwIfFailed();
        if (response.contentLength > 0) {
            String content = new String(readContent(response));
            System.out.println(content);
        }

        sendSetup();
        response = receiveHeaders();
        response.throwIfFailed();
        if (response.contentLength > 0) {
            String content = new String(readContent(response));
            System.out.println(content);
        }
        mSetup = true;

        sendPlay();
        response = receiveHeaders();
        response.throwIfFailed();
        return response;
    }

    public void close() {
        mBytesInPacket = 0;
        if (mSetup) try {
            sendTeardown();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mSetup = false;
        super.close();
        mCSeq.set(0);
        mSession = null;
    }

    private int mBytesInPacket;
    private int mPacketOffset;
    private final byte[] mPacketHeader = new byte[3];
    private final byte[] mPacket = new byte[65535];

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        while (mBytesInPacket == 0) {
            do {
                if (readFully(mInStream, mPacketHeader, 0, 1) != 1) throw new IOException("socket closed");
            } while (mPacketHeader[0] != '$');
            if (readFully(mInStream, mPacketHeader) != mPacketHeader.length) throw new IOException("Truncated RTSP frame");
            int channel = mPacketHeader[0] & 0xff;
            int packetLength = ((mPacketHeader[1] & 0xff) << 8) | (mPacketHeader[2] & 0xff);
            if (readFully(mInStream, mPacket, 0, packetLength) != packetLength) throw new IOException("Truncated RTP packet");
            if ((channel & 1) != 0 || packetLength == 0) continue;
            int type = packetLength > 1 ? mPacket[1] & 0xff : -1;
            if (type >= RtpPacket.RTCP_PAYLOAD_MIN && type <= RtpPacket.RTCP_PAYLOAD_MAX) continue;
            mPacketOffset = RtpPacket.parse(mPacket, packetLength);
            if (mPacketOffset < 0) throw new IOException("Invalid RTP header");
            int padding = (mPacket[0] & 0x20) != 0 ? mPacket[packetLength - 1] & 0xff : 0;
            mBytesInPacket = packetLength - mPacketOffset - padding;
        }
        int bytes = Math.min(mBytesInPacket, length);
        System.arraycopy(mPacket, mPacketOffset, buffer, offset, bytes);
        mPacketOffset += bytes;
        mBytesInPacket -= bytes;
        return bytes;
    }

    @Override
    protected boolean isSecure(String scheme) {
        return "rtsps".equalsIgnoreCase(scheme);
    }

    @Override
    protected int getDefaultPort(String scheme) {
        return isSecure(scheme) ? 443 : 554;
    }

    @Override
    protected String getProtocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    protected void onParseHeader(String key, String value) {
        if (SESSION.equalsIgnoreCase(key)) {
            int pos = value.indexOf(';');
            mSession = pos > 0 ? value.substring(0, pos) : value;
        }
    }

    private StringBuilder makeBaseMessage(String method, boolean withSession) {
        StringBuilder builder = super.makeBaseMessage(method, mRequest.url)
                .append(CSEQ).append(": ").append(Integer.toString(mCSeq.incrementAndGet())).append(CRLF);
        if (withSession && !isEmpty(mSession))
            builder.append(SESSION).append(": ").append(mSession).append(CRLF);
        return builder;
    }

    private void sendDescribe() throws IOException {
        StringBuilder builder = makeBaseMessage("DESCRIBE", false);
        builder.append("Accept: application/sdp").append(CRLF);
        send(builder.append(CRLF).toString());
    }

    private void sendSetup() throws IOException {
        StringBuilder builder = makeBaseMessage("SETUP", false);
        builder.append("Transport: RTP/AVP/TCP;interleaved=0-1").append(CRLF);
        send(builder.append(CRLF).toString());
    }

    private void sendPlay() throws IOException {
        StringBuilder builder = makeBaseMessage("PLAY", true);
        send(builder.append(CRLF).toString());
    }

    private void sendTeardown() throws IOException {
        StringBuilder builder = makeBaseMessage("TEARDOWN", true);
        send(builder.append(CRLF).toString());
    }
}
