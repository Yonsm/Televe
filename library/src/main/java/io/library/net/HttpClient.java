package io.library.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ProtocolException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

public class HttpClient {
	private static final String TAG = "HttpClient";

	private static final int BLOCK_SIZE = 16 * 1024;
	private static final int MAX_REDIRECTS = 5;
	private static final int TIMEOUT_CONNECT = 15 * 1000;
	private static final int TIMEOUT_READ = 60 * 1000;

	public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

	public interface Callback {
		boolean isCancelled();
		void onProgress(long progress, long total);
	}

	public static class ByteOutputStream extends ByteArrayOutputStream {
		public ByteOutputStream(int capacity) {
			super(capacity);
		}

		public byte[] buffer() {
			return super.buf;
		}
	}

	public static void closeSilently(Closeable c) {
		if (c != null) try {
			c.close();
		} catch (Throwable e) {
			Log.e(TAG, "close: ", e);
		}
	}

	public static void disconnectSilently(HttpURLConnection conn) {
		if (conn != null) try {
			conn.disconnect();
		} catch (Throwable e) {
			Log.e(TAG, "disconnect: ", e);
		}
	}

	// need android.permission.ACCESS_NETWORK_STATE
	public static int getNetworkType(Context context) {
		try {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			return activeNetwork.getType();
		} catch (Throwable e) {
			return -1/*ConnectivityManager.TYPE_NONE*/;
		}
	}

	// need android.permission.ACCESS_NETWORK_STATE
	public static InetAddress getBroadcastAddress(boolean ipv6) {
		Enumeration<NetworkInterface> eni;
		try {
			eni = NetworkInterface.getNetworkInterfaces();
		} catch (Exception e) {
			Log.e(TAG, "get broadcast address: ", e);
			return null;
		}

		while (eni.hasMoreElements()) {
			final NetworkInterface ni = eni.nextElement();
			try {
				if (ni.isLoopback() || ni.isPointToPoint())
					continue;
			} catch (Exception e) {
				Log.e(TAG, "check broadcast address: ", e);
				continue;
			}

			final List<InterfaceAddress> eia = ni.getInterfaceAddresses();
			for (InterfaceAddress ia : eia) {
				InetAddress addr = ia.getBroadcast();
				if (addr == null)
					continue;
				if (addr instanceof Inet6Address && !ipv6)
					continue;
				return addr;
			}
		}
		Log.e(TAG, "no broadcast address");
		return null;
	}

	// need android.permission.ACCESS_NETWORK_STATE
	public static InetAddress getLocalAddress(boolean ipv6) {
		Enumeration<NetworkInterface> eni;
		try {
			eni = NetworkInterface.getNetworkInterfaces();
		} catch (Exception e) {
			Log.e(TAG, "get local address: ", e);
			return null;
		}
		while (eni.hasMoreElements()) {
			final NetworkInterface ni = eni.nextElement();
			try {
				if (ni.isLoopback() || ni.isPointToPoint())
					continue;
			} catch (Exception e) {
				Log.e(TAG, "check local address: ", e);
				continue;
			}

			final Enumeration<InetAddress> eia = ni.getInetAddresses();
			while (eia.hasMoreElements()) {
				final InetAddress addr = eia.nextElement();
				if (addr.isLoopbackAddress() || addr.isLinkLocalAddress())
					continue;
				if (addr instanceof Inet6Address && !ipv6)
					continue;
				return addr;
			}
		}
		Log.e(TAG, "no local address");
		return null;
	}

	public static byte[] getHardwareAddress() {
		Enumeration<NetworkInterface> eni;
		try {
			eni = NetworkInterface.getNetworkInterfaces();
		} catch (Exception e) {
			Log.e(TAG, "get MAC address: ", e);
			return null;
		}

		while (eni.hasMoreElements()) {
			try {
				final NetworkInterface ni = eni.nextElement();
				if (ni.isLoopback() || ni.isPointToPoint())
					continue;
				return ni.getHardwareAddress();
			} catch (Exception e) {
				Log.e(TAG, "check MAC address: ", e);
			}
		}
		Log.e(TAG, "no MAC address");
		return null;
	}

	/*	// need android.permission.ACCESS_WIFI_STATE
	public static InetAddress getWifiAddress(Context context) {
		InetAddress addr = null;
		try {
			final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			final WifiInfo info = wifiManager.getConnectionInfo();
			int ipAddress = info.getIpAddress();
			if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN))
				ipAddress = Integer.reverseBytes(ipAddress);
			if (ipAddress != 0) {
				final byte[] a = {(byte) (ipAddress >> 24), (byte) ((ipAddress >> 16) & 0xFF), (byte) ((ipAddress >> 8) & 0xFF), (byte) (ipAddress & 0xFF)};
				addr = InetAddress.getByAddress(a);
			} else {
				addr = InetAddress.getByName("192.168.43.1"); // Address when it is a hotspot
			}
		} catch (Exception e) {
			Log.e(TAG, "get wifi address: ", e);
		}
		if (addr == null || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
			Log.e(TAG, "illegal wifi address " + addr);
			addr = null;
		}
		return addr;
	}*/

	public static void throwIfCancelled(Callback callback) {
		if (callback != null && callback.isCancelled())
			throw new CancellationException();
	}

	/**
	 * Create a conn.
	 */
	public static HttpURLConnection makeConnection(String url, String method, Map<String, String> heads) throws IOException {
		final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setConnectTimeout(TIMEOUT_CONNECT);
		conn.setDoInput(true);
		conn.setReadTimeout(TIMEOUT_READ);
		setConnectionRequestMethod(conn, method);

		if (heads != null) {
			for (Map.Entry<String, String> entry : heads.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				conn.setRequestProperty(key, value);
			}
			if ("no-cache".equals(findField(heads, "Cache-Control")))
				conn.setUseCaches(false);
		}
		return conn;
	}

	public static void setConnectionRequestMethod(HttpURLConnection conn, String method) {
		// set unsupported method like: PROPFIND or MOVE
		try {
			conn.setRequestMethod(method);
		} catch (ProtocolException pe) {
			Class<?> cls = conn.getClass();
			try {
				// for Android 3.0+
				final Field field = cls.getDeclaredField("delegate");
				field.setAccessible(true);
				setConnectionRequestMethod((HttpURLConnection) field.get(conn), method);
			} catch (NoSuchFieldException e) {
				try {
					// for Android 8.x
					final Field field = cls.getDeclaredField("myWrappedHttps");
					field.setAccessible(true);
					setConnectionRequestMethod((HttpURLConnection) field.get(conn), method);
				} catch (Exception ignored) {
				}
			} catch (Exception e) {
				return;
			}
			while (cls != null) {
				try {
					final Field field = cls.getDeclaredField("method");
					field.setAccessible(true);
					field.set(conn, method);
					break;
				} catch (NoSuchFieldException e) {
					cls = cls.getSuperclass();
				} catch (Exception e) {
					return;
				}
			}
		}
	}

	public static InputStream wrapInputStream(InputStream inStream, String encoding) throws Exception {
		if (inStream == null)
			return null;
		else if ("gzip".equals(encoding))
			return new GZIPInputStream(inStream, BLOCK_SIZE);
		else
			return inStream;
	}

	public static InputStream wrapInputStream(HttpURLConnection conn) throws Exception {
		return wrapInputStream(conn.getInputStream(), conn.getContentEncoding());
	}

	public static long copyStream(InputStream is, OutputStream os, long length, Callback callback) throws IOException {
		return copyStream(is, os, 0, length, BLOCK_SIZE, callback);
	}

	public static long copyStream(InputStream is, OutputStream os, long progress, long length,
								  int blockSize, Callback callback) throws IOException {
		if (is instanceof GZIPInputStream)
			length = 0; // gzip's length is compressed size

		final byte[] data = new byte[blockSize];
		int bytes = 0;
		while ((bytes = is.read(data, 0,
				Math.min(blockSize, length > progress ? (int) (length - progress) : blockSize))) >= 0) {
			if (bytes > 0) {
				os.write(data, 0, bytes);
				progress += bytes;
				if (callback != null)
					callback.onProgress(progress, length);
			}
			if (progress >= length && length > 0)
				break;
			else
				throwIfCancelled(callback);
		}
		if (progress != length && length > 0) {
			Log.w(TAG, "copy stream error: " + progress + "/" + length);
			throwIfCancelled(callback);
		}
		return progress;
	}

	public static String readStream(InputStream stream, String charset, Callback callback) throws Exception {
		return readStream(stream, charset, -1, callback);
	}

	public static String readStream(InputStream stream, String charset, long length, Callback callback) throws Exception {
		final ByteOutputStream bos = new ByteOutputStream(stream.available());
		HttpClient.copyStream(stream, bos, length, callback);
		return new String(bos.buffer(), 0, bos.size(), charset);
	}

	/**
	 * Download a url content directly, callback can be null, return the content as string.
	 */
	public static String downloadContent(String url, Callback callback) throws Exception {
		return downloadContent(url, "GET", null, callback);
	}

	public static String downloadContent(String url, String method, Map<String, String> heads, final Callback callback) throws Exception {
		return execute(url, method, heads, new HttpHandler<String>(callback) {
			@Override
			public String onDownload(int statusCode, String mimeType, String charset, long length, InputStream stream) throws Exception {
				return readStream(stream, charset, length, callback);
			}
		});
	}

	/**
	 * Download a file directly, callback can be null, return the response heads.
	 */
	public static Map<String, List<String>> downloadFile(File file, String url, Callback callback) throws Exception {
		return downloadFile(file, url, null, callback);
	}

	public static Map<String, List<String>> downloadFile(final File file, final String url, Map<String, String> heads,
									final Callback callback) throws Exception {
		final boolean hasRange = findField(heads, "Range") != null;
		final long position = hasRange ? file.length() : 0;
		return execute(url, "GET", heads, new HttpHandler<Map<String, List<String>>>(callback) {
			private Map<String, List<String>> mResponseHeads;

			@Override
			public Map<String, List<String>> onResponse(HttpURLConnection conn, int statusCode) throws Exception {
				mResponseHeads = conn.getHeaderFields();
				return super.onResponse(conn, statusCode);
			}

			@Override
			public Map<String, List<String>> onDownload(int statusCode, String mimeType, String charset, long length,
								   InputStream stream) throws Exception {
				final FileOutputStream fos = new FileOutputStream(file, position > 0);
				try {
					HttpClient.copyStream(stream, fos, position, position + length, BLOCK_SIZE, callback);
				} finally {
					HttpClient.closeSilently(fos);
				}
				return mResponseHeads;
			}
		});
	}


	public static <V> V findField(Map<String, V> heads, String key) {
		if (heads == null)
			return null;

		V value = heads.get(key);
		if (value != null)
			return value;

		Set<Map.Entry<String, V>> entries = heads.entrySet();
		for (Map.Entry<String, V> entry : entries) {
			if (key.equalsIgnoreCase(entry.getKey()))
				return entry.getValue();
		}
		return null;
	}


	/**
	 * Returns the charset specified in the Content-Type of this header,
	 * or the HTTP default (ISO-8859-1) if none can be found.
	 */
	public static String parseCharset(String contentType) {
		if (contentType != null) {
			String[] params = contentType.split(";");
			for (int i = 1; i < params.length; i++) {
				String[] pair = params[i].trim().split("=");
				if (pair.length == 2 && pair[0].equals("charset")) {
					return pair[1];
				}
			}
		}
		return "UTF-8";//HTTP.DEFAULT_CONTENT_CHARSET;
	}

	/**
	 * Return the mimetype from Content-Type.
	 */
	public static String parseMimetype(String contentType) {
		String parseMimetype = "";
		if (contentType != null) {
			final int paramPos = contentType.indexOf(";");
			parseMimetype = paramPos > 0 ? contentType.substring(0, paramPos) : contentType;
			parseMimetype = parseMimetype.toLowerCase().trim();
		}
		return (parseMimetype.length() == 0) ? "*/*" : parseMimetype;
	}

	public static void setContentLength(HttpURLConnection conn, long length) {
		// set the length of internal delegate
		final Class<?> cls = conn.getClass();
		try {
			// for Android 3.0+
			final Field field = cls.getDeclaredField("delegate");
			field.setAccessible(true);
			setContentLength((HttpURLConnection) field.get(conn), length);
		} catch (NoSuchFieldException e) {
			try {
				// for Android 2.x
				final Field field = cls.getDeclaredField("httpsEngine");
				field.setAccessible(true);
				setContentLength((HttpURLConnection) field.get(conn), length);
			} catch (Exception ignored) {
			}
		} catch (Exception ignored) {
		}

		conn.setRequestProperty("Content-Length", Long.toString(length));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
			conn.setFixedLengthStreamingMode(length);
		else if (length <= Integer.MAX_VALUE)
			conn.setFixedLengthStreamingMode((int) length);
	}

	/*
	 * Generic http handler.
	 */
	public static class HttpHandler<T> {

		protected final HttpClient.Callback mCallback;

		public HttpHandler(HttpClient.Callback callback) {
			mCallback = callback;
		}

		public void onCheckSSL(HttpsURLConnection conn) throws Exception {
		}

		public void onUpload(HttpURLConnection conn) throws Exception {
			Log.d(TAG, "default upload");
//			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//				// zero must be set in Android 2.3, can Override it
//				conn.setRequestProperty("Content-Length", "0");
//			}
		}

		public boolean onRedirect(HttpURLConnection conn, String url) throws Exception {
			return true;
		}

		public T onResponse(HttpURLConnection conn, int statusCode) throws Exception {
			InputStream stream = null;
			try {
				if (statusCode >= 200 & statusCode < 300) {
					String contentType = conn.getContentType();
					String mimeType = parseMimetype(contentType);
					String charset = parseCharset(contentType);
					stream = wrapInputStream(conn);
					return onDownload(statusCode, mimeType, charset, conn.getContentLength(), stream);
				} else if (statusCode >= 400 && statusCode < 500) {
					String charset = parseCharset(conn.getContentType());
					stream = wrapInputStream(conn.getErrorStream(), conn.getContentEncoding());
					on4xxError(statusCode, charset, stream);
					return null;
				} else {
					throw new UnknownHostException(Integer.toString(statusCode) + " " + conn.getResponseMessage()); // 5xx server error
				}
			} finally {
				closeSilently(stream);
			}
		}

		public T onDownload(int statusCode, String mimeType, String charset, long length, InputStream stream) throws Exception {
			Log.d(TAG, "default download");
			try {
				final byte[] data = new byte[BLOCK_SIZE];
				while (stream.read(data) >= 0)
					;
			} catch (Throwable e) {
				Log.e(TAG, "default download: ", e);
			} finally {
				closeSilently(stream);
			}
			return null;
		}

		public void on4xxError(int statusCode, String charset, InputStream stream) throws Exception {
			String content = "";
			try {
				content = HttpClient.readStream(stream, charset, mCallback);
			} catch (Throwable e) {
				Log.e(TAG, "Read error stream: ", e);
			} finally {
				closeSilently(stream);
			}
			if (statusCode == 404) {
				throw new FileNotFoundException(content);
			} else {
				throw new ResponseException(statusCode, content);
			}
		}

		public void onDisconnect(HttpURLConnection conn) {
			disconnectSilently(conn);
		}
	}

	public static <T> T execute(String url, String method, Map<String, String> heads, HttpHandler<T> handler) throws Exception {
		int redirectTimes = 0;
		String redirectUrl;
		String cookies = null;
		do {
			HttpURLConnection conn = null;
			try {
				conn = makeConnection(url, method, heads);
				conn.setInstanceFollowRedirects(false);  // Redirect bug before Android 2.3
				if (cookies != null) {
					// from http redirect
					final int i = cookies.indexOf(';');
					conn.setRequestProperty("Cookie", i == -1 ? cookies : cookies.substring(0, i));
				}

				if (conn instanceof HttpsURLConnection) {
					handler.onCheckSSL((HttpsURLConnection) conn);
				}

				handler.onUpload(conn);

				int statusCode = 0;
				try {
					statusCode = conn.getResponseCode();
				} catch (IOException e) {
					final String msg = e.getMessage();
					if (msg != null && msg.contains("authentication challenge"))
						statusCode = conn.getResponseCode();
					else
						throw e;
				}
				T result = null;
				if ((statusCode == HttpURLConnection.HTTP_MOVED_TEMP || statusCode == HttpURLConnection.HTTP_MOVED_PERM ||
						statusCode == HttpURLConnection.HTTP_SEE_OTHER) && (redirectUrl = conn.getHeaderField("Location")) != null) {
					// even getInstanceFollowRedirects() will be true by default, https to http or http to https will not be redirected.
					url = redirectUrl;
					cookies = conn.getHeaderField("Set-Cookie");
					if (handler.onRedirect(conn, url))
						continue;
				} else {
					result = handler.onResponse(conn, statusCode);
				}
				return result;
			} finally {
				handler.onDisconnect(conn);
			}
		} while (++redirectTimes < MAX_REDIRECTS);
		throw new UnknownHostException("HTTP error: redirects=" + redirectTimes);
	}

	/*
	 * HTTPS handler
	*/

	private static SSLContext mAlwaysTrustSSLContext;

	public static SSLContext getAlwaysTrustSSLClient() {
		synchronized (HttpClient.class) {
			if (mAlwaysTrustSSLContext == null) {
				final X509TrustManager tm = new X509TrustManager() {
					public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
						//	Log.d(TAG, "checkClientTrusted:" + chain);
					}

					public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
						//	Log.d(TAG, "checkServerTrusted:" + chain);
					}

					public X509Certificate[] getAcceptedIssuers() {
						//	Log.d(TAG, "getAcceptedIssuers:");
						return new X509Certificate[0];
					}
				};
				try {
					final SSLContext context = SSLContext.getInstance("TLS");
					context.init(null, new X509TrustManager[]{tm}, null);
					mAlwaysTrustSSLContext = context;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return mAlwaysTrustSSLContext;
		}
	}

	public static void setInsecureSSLConnection(HttpsURLConnection conn) {
		conn.setHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				//	Log.d(TAG, "verify:" + hostname);
				return true;
			}
		});

		final SSLContext context = getAlwaysTrustSSLClient();
		conn.setSSLSocketFactory(context.getSocketFactory());
	}
}
