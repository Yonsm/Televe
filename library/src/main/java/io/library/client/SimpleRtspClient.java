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
        super.connect(request);

        sendDescribe();
        Response response = receiveHeaders();
        if (response.shouldRedirect()) {
            String location = findLocationOrThrow(response);
            request.url = URI.create(request.url).resolve(location).toString();
            close();
            return open(request);
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
        mCSeq.set(0);
        mBytesInPacket = 0;
        if (mSetup) try {
            sendTeardown();
            mSetup = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.close();
    }

    private int mBytesInPacket;
    private final byte[] mPacketHeader = new byte[RtpPacket.RTP_MAX_HDR_SIZE];

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int result = 0;
        if (mBytesInPacket > 0) {
            result = readInPacket(buffer, offset, length);
            if (result == length)
                return result;
            offset += result;
            length -= result;
        }

        // Find $ tag
        do {
            if (readFully(mInStream, mPacketHeader, 0, 1) != 1)
                throw new IOException("no more data");
        } while (mPacketHeader[0] != '$');

        // Read 1 byte channel number and 2 bytes
        if (readFully(mInStream, mPacketHeader, 0, 3) != 3)
            throw new IOException("no more data");

        mInStream.mark(mPacketHeader.length);
        int packetLength = ((mPacketHeader[1] & 0xff) << 8) | (mPacketHeader[2] & 0xff);
        int bytes = readFully(mInStream, mPacketHeader);
        if (bytes == -1)
            throw new IOException("socket closed");

        int payloadOffset = RtpPacket.parse(mPacketHeader, bytes);
        if (payloadOffset == -1)
            throw new IOException("no RTP packet");

        mBytesInPacket = packetLength - payloadOffset;
        mInStream.reset();
        mInStream.skip(payloadOffset);
        return result + readInPacket(buffer, offset, length);
    }

    private int readInPacket(byte[] buffer, int offset, int length) throws IOException {
        int bytes = readFully(mInStream, buffer, offset, Math.min(mBytesInPacket, length));
        mBytesInPacket -= bytes;
        return bytes;
    }

    @Override
    protected boolean isSecure(String scheme) {
        return "rtsps".equals(scheme);
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
