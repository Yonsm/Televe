package io.library.net;

import android.util.JsonReader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.ResponseCache;
import java.net.URI;

import javax.net.ssl.HttpsURLConnection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class Request {

	public interface Parser<T> {
		T onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception;
	}

	protected Map<String, String> mHeads = new HashMap<>();
	protected String mUrl;
	protected String mMethod;
	protected boolean mCompress;
	protected PostBody mPostBody;

	public Request(String url) {
		mUrl = url;
		mMethod = "GET";
	}

	public Request(String url, String method) {
		mUrl = url;
		mMethod = method;
	}

	public Request(String url, String method, boolean compress) {
		mUrl = url;
		mMethod = method;
		mCompress = compress;
	}

	public Request(String url, String method, boolean compress, PostBody postBody) {
		mUrl = url;
		mMethod = method;
		mCompress = compress;
		mPostBody = postBody;
	}

	public String getUrl() {
		return mUrl;
	}

	public Request setHeader(String name, String value) {
		mHeads.put(name, value);
		return this;
	}

	public Request setHeaders(Map<String, String> heads) {
		mHeads.putAll(heads);
		return this;
	}

	public <T> T execute(Parser<T> parser) throws Exception {
		return execute(parser, null, false);
	}

	public <T> T execute(Parser<T> parser, HttpClient.Callback callback) throws Exception {
		return execute(parser, callback, false);
	}

	public <T> T execute(Parser<T> parser, HttpClient.Callback callback, boolean forceCache) throws Exception {
		return execute(parser, callback, forceCache, false);
	}

	public <T> T execute(Parser<T> parser, HttpClient.Callback callback, boolean forceCache, boolean insecureSSL) throws Exception {
		fixHeadsIfNeeded();

		if (forceCache) {
			InputStream stream = null;
			try {
				ResponseCache cache = ResponseCache.getDefault();
				CacheResponse response = cache != null ? cache.get(new URI(mUrl), mMethod, convertHeads(mHeads)) : null;
				if (response != null) {
					Map<String, List<String>> heads = response.getHeaders();
					String contentEncoding = getFirstValue(heads, "Content-Encoding");
					String contentType = getFirstValue(heads, "Content-Type");
					String mimeType = HttpClient.parseMimetype(contentType);
					String charset = HttpClient.parseCharset(contentType);
					stream = HttpClient.wrapInputStream(response.getBody(), contentEncoding);
					T result = parser.onParse(mimeType, charset, stream, true);
					if (result != null)
						return result;
				}
			} finally {
				HttpClient.closeSilently(stream);
			}
		}

		final boolean useInsecureSSL = insecureSSL;
		return HttpClient.execute(mUrl, mMethod, mHeads, new HttpClient.HttpHandler<T>(callback) {
			@Override
			public void onCheckSSL(HttpsURLConnection conn) throws Exception {
				if (useInsecureSSL) {
					HttpClient.setInsecureSSLConnection(conn);
				}
			}
			@Override
			public void onUpload(HttpURLConnection conn) throws Exception {
				if (mPostBody != null) {
					OutputStream os = null;
					try {
						long length = mPostBody.contentLength();
						if (length > 0)
							HttpClient.setContentLength(conn, length);
						else
							conn.setChunkedStreamingMode(1024 * 1024);
						os = conn.getOutputStream();
						mPostBody.writeTo(os);
					} finally {
						HttpClient.closeSilently(os);
					}
				}
			}
			@Override
			public T onDownload(int statusCode, String mimeType, String charset, long length, InputStream stream) throws Exception {
				return parser.onParse(mimeType, charset, stream, false);
			}
		});
	}

	protected void fixHeadsIfNeeded() {
		if (mCompress) {
			String encoding = HttpClient.findField(mHeads, "Accept-Encoding");
			if (encoding == null)
				encoding = "gzip, identity";
			else if (! encoding.contains("gzip"))
				encoding = "gzip, " + encoding;
			mHeads.put("Accept-Encoding", encoding);
		}
	}

	public static Map<String, List<String>> convertHeads(Map<String, String> heads) {
		if (heads == null || heads.isEmpty())
			return Collections.emptyMap();

		final HashMap<String, List<String>> hashMap = new HashMap<>(heads.size());
		Set<Map.Entry<String, String>> entries = heads.entrySet();
		for (Map.Entry<String, String> entry : entries)
			hashMap.put(entry.getKey(), Collections.singletonList(entry.getValue()));
		return hashMap;
	}

	public static String getFirstValue(Map<String, List<String>> map, String key) {
		List<String> list = HttpClient.findField(map, key);
		return list != null && list.size() > 0 ? list.get(0) : null;
	}

	public static class NullParser implements Parser<Void> {
		@Override
		public Void onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
			return null;
		}
	}

	public static class StringParser implements Parser<String> {
		@Override
		public String onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
			return HttpClient.readStream(stream, charset, null);
		}
	}

	public static class JsonArrayParser implements Parser<JSONArray> {
		@Override
		public JSONArray onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
			return new JSONArray(HttpClient.readStream(stream, charset, null));
		}
	}

	public static class JsonObjectParser implements Parser<JSONObject> {
		@Override
		public JSONObject onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
			return new JSONObject(HttpClient.readStream(stream, charset, null));
		}
	}

	public static abstract class JsonReaderParser<T> implements Request.Parser<T> {
		@Override
		public T onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
			return onParse(new JsonReader(new InputStreamReader(stream, charset)), fromCache);
		}

		protected abstract T onParse(JsonReader response, boolean fromCache) throws Exception;
	}

	public static abstract class XmlReaderParser<T> implements Request.Parser<T> {
		@Override
		public T onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
			XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
			parser.setInput(new InputStreamReader(stream, charset));
			return onParse(parser, fromCache);
		}

		protected abstract T onParse(XmlPullParser response, boolean fromCache) throws Exception;
	}
}
