package io.library.net;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/** Downloads playlist documents, including redirects between HTTP and HTTPS. */
public final class HttpClient {
    private static SSLContext sslContext;

    public interface Parser<T> {
        T onParse(String url, String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception;
    }

    public static HttpURLConnection makeConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        return connection;
    }

    public static <T> T get(String url, Parser<T> parser) throws Exception {
        String cookies = null;
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection connection = makeConnection(url);
            try {
                connection.setInstanceFollowRedirects(false);
                if (cookies != null) connection.setRequestProperty("Cookie", cookies.split(";", 2)[0]);
                if (connection instanceof HttpsURLConnection) allowLegacyCertificates((HttpsURLConnection) connection);
                int status;
                try {
                    status = connection.getResponseCode();
                } catch (IOException error) {
                    // Some Android HTTP stacks require a second read after an authentication challenge.
                    if (error.getMessage() == null || !error.getMessage().contains("authentication challenge")) throw error;
                    status = connection.getResponseCode();
                }
                String location = connection.getHeaderField("Location");
                if ((status == 301 || status == 302 || status == 303 || status == 307 || status == 308)
                        && location != null) {
                    url = new URL(connection.getURL(), location).toExternalForm();
                    cookies = connection.getHeaderField("Set-Cookie");
                    continue;
                }
                String contentType = connection.getContentType();
                String charset = parseCharset(contentType);
                try (InputStream stream = decode(status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                        connection.getContentEncoding())) {
                    if (status >= 200 && status < 300) {
                        String mimeType = contentType == null ? "*/*" : contentType.split(";", 2)[0].trim();
                        return parser.onParse(connection.getURL().toExternalForm(), mimeType, charset, stream, false);
                    }
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    if (stream != null) {
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = stream.read(buffer)) != -1) content.write(buffer, 0, count);
                    }
                    String message = content.toString(charset);
                    if (status == 404) throw new FileNotFoundException(message);
                    throw new IOException("HTTP " + status + ": " + message);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    private static InputStream decode(InputStream stream, String encoding) throws IOException {
        return stream != null && "gzip".equalsIgnoreCase(encoding) ? new GZIPInputStream(stream) : stream;
    }

    public static String parseCharset(String contentType) {
        if (contentType != null) {
            for (String parameter : contentType.split(";")) {
                String[] pair = parameter.trim().split("=", 2);
                if (pair.length == 2 && "charset".equalsIgnoreCase(pair[0]))
                    return pair[1].trim().replace("\"", "");
            }
        }
        return StandardCharsets.UTF_8.name();
    }

    private static synchronized void allowLegacyCertificates(HttpsURLConnection connection) throws Exception {
        if (sslContext == null) {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new X509TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
        }
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier((host, session) -> true);
    }
}
