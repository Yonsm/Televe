package io.library.client;

import java.io.IOException;
import java.net.URI;

public class SimpleHttpClient extends SimpleBaseClient {
    protected static final String PROTOCOL_VERSION = "HTTP/1.1";
    protected static final String TRANSFER_ENCODING = "transfer-encoding";
    protected static final String CHUNKED = "chunked";

    public static final String USER_AGENT = "stagefright/1.2 (Linux;Android 9)";

    public static class InvalidResponseException extends IOException {
        public final Response response;
        InvalidResponseException(Response response) {
            super(response.statusLine.toString());
            this.response = response;
        }
    }

    public Response open(Request request) throws IOException {
        return open(request, 0);
    }

    protected Response open(Request request, int redirects) throws IOException {
        super.connect(request);

        sendMethod(request.method);
        if (request.body != null)
            send(request.body);

        mChunkTransfer = false;

        Response response = receiveHeaders();
        if (response.shouldRedirect()) {
            String location = findLocationOrThrow(response);
            if (redirects >= 5) // 防止源站重定向成环导致递归溢出
                throw new IOException("Too many redirects: " + request.url);
            try {
                request.url = URI.create(request.url).resolve(location).toString();
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid redirect location: " + location, error);
            }
            close();
            return open(request, redirects + 1);
        }
        if (response.statusLine.code < 200 || response.statusLine.code >= 300)
            throw new InvalidResponseException(response);

        mHasMoreChunks = mChunkTransfer;
        mBytesInChunk = NO_CHUNK_YET;
        return response;
    }

    private static final int NO_CHUNK_YET = -1;
    private boolean mChunkTransfer;
    private boolean mHasMoreChunks;
    private int mBytesInChunk;

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (!mChunkTransfer)
            return readFully(mInStream, buffer, offset, length);

        if (!mHasMoreChunks)
            return -1;

        if (mBytesInChunk == 0 || mBytesInChunk == NO_CHUNK_YET) {
            readChunkSize();
            if (!mHasMoreChunks)
                return -1;
        }

        int bytes = readFully(mInStream, buffer, offset, Math.min(length, mBytesInChunk));
        if (bytes == -1)
            throw new IOException("socket closed");

        mBytesInChunk -= bytes;
        return bytes;
    }

    private void readChunkSize() throws IOException {
        // Read the suffix of the previous chunk.
        if (mBytesInChunk != NO_CHUNK_YET)
            readUtf8LineStrictly(mInStream, mLineBuffer);

        mBytesInChunk = (int) readHexadecimalUnsignedLong();
        readUtf8LineStrictly(mInStream, mLineBuffer);

        if (mBytesInChunk == 0L)
            mHasMoreChunks = false;
    }

    @Override
    protected boolean isSecure(String scheme) {
        return "https".equalsIgnoreCase(scheme);
    }

    @Override
    protected int getDefaultPort(String scheme) {
        return isSecure(scheme) ? 443 : 80;
    }

    @Override
    protected String getProtocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    protected void onParseHeader(String key, String value) {
        if (TRANSFER_ENCODING.equalsIgnoreCase(key))
            mChunkTransfer = CHUNKED.equalsIgnoreCase(value);
    }

    protected void sendMethod(String method) throws IOException {
        String path = mUri.getRawPath();
        if (isEmpty(path)) path = "/";
        String query = mUri.getRawQuery();
        if (!isEmpty(query))
            path += '?' + query;
        StringBuilder builder = super.makeBaseMessage(method, path)
                .append("Host: ").append(mHost)
                .append(mPort == -1 ? "" : ":" + mPort).append(CRLF)
                .append("Accept-Encoding: identity").append(CRLF)
                .append("Connection: Keep-Alive").append(CRLF)
                .append("User-Agent: ").append(USER_AGENT).append(CRLF);
        send(builder.append(CRLF).toString());
    }
}
