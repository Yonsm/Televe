package io.library.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

public abstract class SimpleBaseClient implements Closeable {
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String LOCATION = "Location";
    public static final String CRLF = "\r\n";

    public static final int SO_TIMEOUT = 15000;
    private static final int IPV6_CONNECT_TIMEOUT = 3000;
    public static final int BUFFER_SIZE = 16384;

    public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    public static class Headers extends HashMap<String, String> {
        public String find(String key) {
            String value = super.get(key);
            if (value != null)
                return value;

            for (Entry<String, String> entry : entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key))
                    return entry.getValue();
            }
            return null;
        }
    }

    public static class Request extends SimpleBaseClient.Headers {
        public String method;
        public String url;
        public ByteBuffer body;

        public Request(String url) {
            this("GET", url);
        }

        public Request(String method, String url) {
            this(method, url, null);
        }

        public Request(String method, String url, ByteBuffer body) {
            this.method = method;
            this.url = url;
            this.body = body;
        }
    }

    public static class Response extends Headers {
        public StatusLine statusLine;
        public long contentLength;

        public boolean shouldRedirect() {
            return statusLine == StatusLine.MOVED_PERMANENTLY
                    || statusLine == StatusLine.MOVED_TEMPORARILY
                    || statusLine == StatusLine.TEMPORARY_REDIRECT;
        }

        public Map<String, List<String>> toMultiMap() {
            final HashMap<String, List<String>> headers = new HashMap<>(size());
            for (Map.Entry<String, String> entry : entrySet())
                headers.put(entry.getKey(), Collections.singletonList(entry.getValue()));
            return headers;
        }

        public void throwIfFailed() throws IOException {
            int code = statusLine.code;
            if (code < 200 || code >= 300)
                throw new IOException(statusLine.toString());
        }

        @Override
        public String toString() {
            return statusLine.toString() + ' ' + super.toString();
        }
    }

    private Socket mSocket;
    protected BufferedInputStream mInStream;
    protected BufferedOutputStream mOutStream;
    protected Request mRequest;
    protected URI mUri;
    protected final byte[] mLineBuffer = new byte[BUFFER_SIZE];

    public abstract Response open(Request request) throws IOException;
    protected abstract boolean isSecure(String scheme);
    protected abstract int getDefaultPort(String scheme);
    protected abstract String getProtocolVersion();
    protected abstract void onParseHeader(String key, String value);

    public String getUrl() {
        return mRequest != null ? mRequest.url : null;
    }

    public long open(String url) throws IOException {
        return open(new Request(url)).contentLength;
    }

    public URI connect(Request request) throws IOException {
        URI uri = URI.create(request.url);
        String scheme = uri.getScheme();
        int port = uri.getPort();
        if (port == -1)
            port = getDefaultPort(scheme);

        SocketFactory factory = isSecure(scheme) ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
        IOException lastError = null;
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        for (int family = 0; family < 2 && mSocket == null; family++) {
            boolean ipv6 = family == 0;
            for (InetAddress address : addresses) {
                if ((address instanceof Inet6Address) != ipv6)
                    continue;
                Socket socket = null;
                try {
                    socket = factory.createSocket();
                    socket.setSoTimeout(SO_TIMEOUT);
                    socket.connect(new InetSocketAddress(address, port),
                            ipv6 ? IPV6_CONNECT_TIMEOUT : SO_TIMEOUT);
                    mSocket = socket;
                    break;
                } catch (IOException error) {
                    closeSilently(socket);
                    lastError = error;
                }
            }
        }
        if (mSocket == null)
            throw lastError;
        mInStream = new BufferedInputStream(mSocket.getInputStream(), BUFFER_SIZE);
        mOutStream = new BufferedOutputStream(mSocket.getOutputStream(), BUFFER_SIZE);
        mRequest = request;
        mUri = uri;
        return uri;
    }

    @Override
    public void close() {
        closeSilently(mInStream, mOutStream, mSocket);
        mInStream = null;
        mOutStream = null;
        mSocket = null;
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        return mInStream.read(buffer, offset, length);
    }

    protected StringBuilder makeBaseMessage(String method, String path) {
        StringBuilder builder = new StringBuilder(256)
                .append(method).append(' ').append(path).append(' ').append(getProtocolVersion()).append(CRLF);
        for (Map.Entry<String, String> entry : mRequest.entrySet())
            builder.append(entry.getKey()).append(':').append(entry.getValue()).append(CRLF);
        return builder;
    }

    protected Response receiveHeaders() throws IOException {
        String line = readUtf8Line(mInStream, mLineBuffer);
        System.out.println(line);

        String protocol = getProtocolVersion();
//        if (!line.startsWith(protocol))
//            throw new IOException("bad protocol: " + line);

        String statusLine = line.substring(protocol.length()).trim();
        Response response = new Response();
        response.statusLine = StatusLine.parse(statusLine);

        for (;;) {
            line = readUtf8Line(mInStream, mLineBuffer);
            if (isEmpty(line))
                break;
            System.out.println(line);

            int pos = line.indexOf(':');
            if (pos > 0) {
                String key = line.substring(0, pos);
                String value = line.substring(pos + 1).trim();
                response.put(key, value);
                if (CONTENT_LENGTH.equalsIgnoreCase(key)) {
                    response.contentLength = parseLong(value);
                }
                onParseHeader(key, value);
            }
        }
        return response;
    }

    public byte[] readContent(Response response) throws IOException {
        final byte[] content;
        try {
            if (response.contentLength > 0) {
                content = new byte[(int) response.contentLength];
                readFully(mInStream, content);
            } else {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(BUFFER_SIZE);
                response.contentLength = writeFully(mInStream, bos);
                content = bos.toByteArray();
            }
        } catch (OutOfMemoryError e) {
            throw new IOException(e);
        }
        return content;
    }

    protected long readHexadecimalUnsignedLong() throws IOException {
        final byte[] buf = mLineBuffer;
        int pos = -1, read = 0;
        mInStream.mark(buf.length);
        do {
            int ret = mInStream.read(buf, read, buf.length - read);
            if (ret > 0) {
                pos = findHexadecimalEnd(buf, read, ret);
                read += ret;
            } else if (ret == -1) {
                break;
            }
        } while (pos == -1 && read < buf.length);

        if (pos == -1 && read == 0)
            throw new IOException("closed");

        if (pos >= 0) {
            mInStream.reset();
            mInStream.skip(pos);
        }

        long n = 0;
        for (int i = 0; i < pos; i++) {
            final byte b = buf[i];
            int d = 0;
            if (b >= '0' && b <= '9')
                d = b - '0';
            else if (b >= 'a' && b <= 'f')
                d = b - 'a' + 10;
            else if (b >= 'A' && b <= 'F')
                d = b - 'A' + 10;
            n <<= 4;
            n |= d;
        }
        return n;
    }

    protected void send(String message) throws IOException {
        System.out.println(message);
        mOutStream.write(message.getBytes(UTF8_CHARSET));
        mOutStream.flush();
    }

    protected void send(ByteBuffer data) throws IOException {
        writeFully(data, mOutStream);
        mOutStream.flush();
    }

    public static void closeSilently(Closeable obj) {
        try {
            obj.close();
        } catch (Exception ignored) {
        }
    }

    public static void closeSilently(Closeable... objects) {
        for (Closeable obj : objects)
            closeSilently(obj);
    }

    public static int findHexadecimalEnd(byte[] buf, int off, int len) {
        for (int i = off; i < off + len; i++) {
            final byte b = buf[i];
            if ((b < '0' || b > '9') && (b < 'a' || b > 'f') && (b < 'A' || b > 'F'))
                return i;
        }
        return -1;
    }

    public static int findLineEnd(byte[] buf, int off, int len) {
        for (int i = off; i < off + len; i++) {
            if (buf[i] == '\n')
                return i;
        }
        return -1;
    }

    public static String findLocationOrThrow(Response response) throws IOException {
        String location = response.find(LOCATION);
        if (isEmpty(location))
            response.throwIfFailed();
        return location;
    }

    public static boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    public static int readFully(InputStream stream, byte[] buffer) throws IOException {
        return readFully(stream, buffer, 0, buffer.length);
    }

    public static int readFully(InputStream stream, byte[] buffer, int offset, int length) throws IOException {
        int read = 0;
        do {
            int ret = stream.read(buffer, offset + read, length - read);
            if (ret > 0)
                read += ret;
            else if (ret == -1)
                return read > 0 ? read : -1;
        } while (read < length);
        return read;
    }

    protected static String readUtf8Line(InputStream stream, byte[] buf) throws IOException {
        int pos = readUtf8LineStrictly(stream, buf);
        return new String(buf, 0, pos, UTF8_CHARSET);
    }

    protected static int readUtf8LineStrictly(InputStream stream, byte[] buf) throws IOException {
        int pos = -1, read = 0;
        stream.mark(buf.length);
        do {
            int ret = stream.read(buf, read, buf.length - read);
            if (ret > 0) {
                pos = findLineEnd(buf, read, ret);
                read += ret;
            } else if (ret == -1) {
                break;
            }
        } while (pos == -1 && read < buf.length);

        if (pos == -1 && read == 0)
            throw new IOException("closed");

        if (pos >= 0) {
            stream.reset();
            stream.skip(pos + 1);
            if (pos > 0 && buf[pos - 1] == '\r')
                pos--;
        }
        return pos;
    }

    public static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void writeFully(ByteBuffer data, OutputStream outputStream) throws IOException {
        try {
            outputStream.write(data.array());
        } catch (UnsupportedOperationException ignored) {
            final byte[] buffer = new byte[16384];
            int ret;
            do {
                ret = Math.min(buffer.length, data.remaining());
                data.get(buffer, 0, ret);
                if (ret > 0)
                    outputStream.write(buffer, 0, ret);
            } while (ret >= 0);
        }
    }

    public static long writeFully(InputStream inStream, OutputStream outStream) throws IOException {
        return writeFully(inStream, Long.MAX_VALUE, outStream);
    }

    public static long writeFully(InputStream inStream, long length, OutputStream outStream) throws IOException {
        final byte[] buf = new byte[BUFFER_SIZE];
        long written = 0;
        do {
            int ret = inStream.read(buf, 0, (int) Math.min(buf.length, length - written));
            if (ret > 0) {
                outStream.write(buf, 0, ret);
                written += ret;
            } else if (ret == -1) {
                break;
            }
        } while (written < length);
        return written;
    }
}
