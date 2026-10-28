package io.library.net;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.library.utils.DiskCache;


public class HttpCache extends ResponseCache {
	private static final int MAGIC = 0x68746361; // "htca"
	private static final String METHOD_GET = "GET";

	private final WeakHashMap<String, List<String>> mVaryFieldsMap = new WeakHashMap<>();
	private final DiskCache mDiskCache;

	protected HttpCache(DiskCache cache) {
		mDiskCache = cache;
	}

	@Override
	public CacheResponse get(URI uri, String method, final Map<String, List<String>> heads) throws IOException {
		if (uri == null)
			throw new IllegalArgumentException("uri is null");
		if (!METHOD_GET.equals(method))
			return null;

		if (!mDiskCache.isOpened())
			mDiskCache.open();

		final String url = uri.toString();
		final long key = DiskCache.hash64(url);
		final File cacheFile = mDiskCache.getCacheFile(key);
		if (cacheFile.length() == 0)
			return null;

		List<String> varyFields = HttpClient.findField(heads, "Vary");
		synchronized (mVaryFieldsMap) {
			if (varyFields != null)
				mVaryFieldsMap.put(url, varyFields);
		}

		final File headFile = mDiskCache.getHeadFile(key);
		final Map<String, List<String>> responseHeads = readResponseHeads(url, headFile, varyFields);
		if (responseHeads == null)
			return null;

		try {
			return new CacheResponse() {
				@Override
				public Map<String, List<String>> getHeaders() throws IOException {
					return responseHeads;
				}
				@Override
				public InputStream getBody() throws IOException {
					mDiskCache.accessCache(key);
					return new FileInputStream(cacheFile);
				}
			};
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public CacheRequest put(URI uri, URLConnection conn) throws IOException {
		if (uri == null)
			throw new IllegalArgumentException("uri is null");
		if (!(conn instanceof HttpURLConnection))
			throw new IllegalArgumentException("not http connection");

		if (!conn.getUseCaches()
			|| !METHOD_GET.equals(((HttpURLConnection) conn).getRequestMethod()))
			return null;

		if (!mDiskCache.isOpened())
			mDiskCache.open();

		final String url = uri.toString();
		final List<String> varyFields;
		synchronized (mVaryFieldsMap) {
			varyFields = mVaryFieldsMap.get(url);
		}
		if (varyFields != null && varyFields.contains("*"))
			return null;

		final long key = DiskCache.hash64(url);
		final File filePart = mDiskCache.getPartFile(key);
		final Map<String, List<String>> responseHeads = conn.getHeaderFields();
		final FileOutputStream fos = new FileOutputStream(filePart) {
			@Override
			public void close() throws IOException {
				super.close();

				if (filePart.renameTo(mDiskCache.getCacheFile(key))) try {
					writeResponseHeads(url, mDiskCache.getHeadFile(key), varyFields, responseHeads);
					mDiskCache.updateCache(key);
				} catch (Exception ignored) {
				}
			}
		};
		return new CacheRequest() {
			@Override
			public OutputStream getBody() throws IOException {
				return fos;
			}
			@Override
			public void abort() {
				filePart.delete();
			}
		};
	}

	protected static boolean equalsList(List<String> list, List<String> list2) {
		if (list == list2)
			return true;
		else if (list == null)
			return list2.isEmpty();
		else if (list2 == null)
			return list.isEmpty();
		return list.equals(list2);
	}

	public static List<String> readList(DataInputStream dis) throws IOException {
		final int count2 = dis.readInt();
		final ArrayList<String> list = new ArrayList<>(count2);
		if (count2 > 0) {
			for (int j = 0; j < count2; j++)
				list.add(dis.readUTF());
		}
		return list;
	}

	public static Map<String, List<String>> readListMap(DataInputStream dis) throws IOException {
		final int count = dis.readInt();
		final HashMap<String, List<String>> map = new HashMap<>(count);
		for (int i = 0; i < count; i++) {
			final String key = dis.readUTF();
			final List<String> list = readList(dis);
			map.put(key.length() > 0 ? key : null, list);
		}
		return map;
	}

	public static Map<String, List<String>> readResponseHeads(String url, File fileHead, List<String> varyFields) {
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileHead)));
			if (dis.readInt() != MAGIC)
				throw new RuntimeException("incorrect magic");
			if (!dis.readUTF().equals(url))
				throw new RuntimeException("incorrect uri");
			if (!equalsList(varyFields, readList(dis)))
				throw new RuntimeException("incorrect vary");
			return readListMap(dis);
		} catch (FileNotFoundException ignored) {
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			HttpClient.closeSilently(dis);
		}
	}

	public static void writeList(DataOutputStream dos, List<String> list) throws IOException {
		dos.writeInt(list.size());
		for (String value : list)
			dos.writeUTF(value != null ? value : "");
	}

	public static void writeListMap(DataOutputStream dos, Map<String, List<String>> map) throws IOException {
		int count = map != null ? map.size() : 0;
		dos.writeInt(count);
		if (count > 0) {
			final Set<Map.Entry<String, List<String>>> entries = map.entrySet();
			for (Map.Entry<String, List<String>> entry : entries) {
				final String key = entry.getKey();
				dos.writeUTF(key != null ? key : "");
				writeList(dos, entry.getValue());
			}
		}
	}

	public static void writeResponseHeads(String url, File fileHead, List<String> varyFields,
										   Map<String, List<String>> responseHeads) throws IOException {
		DataOutputStream dos = null;
		try {
			dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileHead)));
			dos.writeInt(MAGIC);
			dos.writeUTF(url);
			writeList(dos, varyFields != null ? varyFields : Collections.<String>emptyList());
			writeListMap(dos, responseHeads != null ? responseHeads : Collections.<String, List<String>>emptyMap());
		} finally {
			HttpClient.closeSilently(dos);
		}
	}

	public static final String CACHE_DIR = "http_cache";
	private static DiskCache mInstance;

	public static synchronized DiskCache getDiskCache(Context context) {
		if (mInstance == null) {
			final File dir = context.getExternalCacheDir();
			final File cacheDir = new File(dir != null ? dir : context.getCacheDir(), CACHE_DIR);
			long bytes = cacheDir.getFreeSpace();
			long maxSize = bytes > 4L * 1024 * 1024 * 1024 ? 1024 * 1024 * 1024 : 256L * 1024 * 1024;
			mInstance = new DiskCache(cacheDir, maxSize);
		}
		return mInstance;
	}

	public static synchronized void install(Context context) {
		ResponseCache current = ResponseCache.getDefault();
		if (!(current instanceof HttpCache)) {
			DiskCache cache = getDiskCache(context);
			ResponseCache.setDefault(new HttpCache(cache));
		}
	}
}
