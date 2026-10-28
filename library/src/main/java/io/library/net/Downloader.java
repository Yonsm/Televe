package io.library.net;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.library.utils.DiskCache;


public class Downloader implements Callable<File> {
	private static final ConcurrentHashMap<Long, Lock> mLoadings = new ConcurrentHashMap<>();

	public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

	public interface Listener extends HttpClient.Callback {
		void onResult(String url, File file, String mimeType, boolean fromCache);
		void onError(String url, Throwable err);
	}

	private static class Lock extends ReentrantLock {
		File file;
		long length;
	}

	protected String mUrl;
	protected boolean mSupportRange;
	protected Map<String, String> mHeads;
	protected DiskCache mDiskCache;
	protected Listener mListener;
	protected Lock mLock;

	public Downloader(String url, boolean supportRange, Map<String, String> heads, DiskCache cache,
					  Listener listener) {
		mUrl = url;
		mSupportRange = supportRange;
		mHeads = heads;
		mDiskCache = cache;
		mListener = listener;
	}

	public Future<File> submit() {
		return EXECUTOR.submit(this);
	}

	@Override
	public File call() {
		if (!mDiskCache.isOpened())
			mDiskCache.open();

		// lock downloading by the a uri related ReentrantLock to avoid conflict
		final long key = DiskCache.hash64(mUrl);
		mLock = lock(key);

		File fileCache = mDiskCache.getCacheFile(key);
		File fileHead = mDiskCache.getHeadFile(key);
		File filePart = null;
		Map<String, List<String>> responseHeads = null;
		Listener listener = mListener;
		try {
			responseHeads = HttpCache.readResponseHeads(mUrl, fileHead, null);
			if (responseHeads == null)
				mDiskCache.removeCache(key);

			boolean fromCache = fileCache.exists();
			if (fromCache) {
				mDiskCache.accessCache(key);
			} else {
				filePart = mLock.file = mDiskCache.getPartFile(key);
				if (!mSupportRange) {
					fileHead.delete();
					filePart.delete();
				}
				HttpClient.throwIfCancelled(listener);

				responseHeads = onDownloadFile(filePart);

				if (filePart.renameTo(fileCache))
					updateCache(key, fileHead, responseHeads);
			}
			mLoadings.remove(key);
			HttpClient.throwIfCancelled(listener);

			List<String> list = HttpClient.findField(responseHeads, "Content-Type");
			String mimeType = list != null && list.size() > 0 ? list.get(0) : "";
			if (listener != null)
				listener.onResult(mUrl, fileCache, mimeType, fromCache);
			return fileCache;
		} catch (Throwable e) {
			if (mSupportRange) {
				updateCache(key, fileHead, responseHeads);
			} else {
				if (fileHead != null)
					fileHead.delete();
				if (filePart != null)
					filePart.delete();
			}
			if (listener != null)
				listener.onError(mUrl, e);
		} finally {
			unlock();

			mListener = null;
			mDiskCache = null;
			mHeads = null;
			mUrl = null;
		}
		return null;
	}

	private Lock lock(long key) {
		final Lock newLock = new Lock();
		final Lock curLock = mLoadings.putIfAbsent(key, newLock);
		final Lock lock = curLock != null ? curLock : newLock;
		try {
			File file;
			Listener listener = mListener;
			while (!lock.tryLock(100, TimeUnit.MILLISECONDS)) {
				HttpClient.throwIfCancelled(listener);
				if (listener != null && lock.length > 0 && (file = lock.file) != null)
					listener.onProgress(file.length(), lock.length);
			}
		} catch (Exception ignored) {
		}
		return lock;
	}

	private void unlock() {
		final Lock lock = mLock;
		mLock = null;
		if (lock != null)
			lock.unlock();
	}

	private void updateCache(long key, File fileHead, Map<String, List<String>> responseHeads) {
		try {
			// Write the url for checking in onCheckCache()
			HttpCache.writeResponseHeads(mUrl, fileHead, null, responseHeads);
		} catch (Exception ignored) {
		}
		mDiskCache.updateCache(key);
	}

	protected Map<String, List<String>> onDownloadFile(File filePart) throws Exception {
		Map<String, String> heads = mHeads;
		if (mSupportRange) {
			if (mHeads != null)
				heads = new HashMap<>(mHeads);
			else
				heads = new HashMap<>(3);
			heads.put("Accept-Encoding", "identity");
			heads.put("Cache-Control", "no-cache");
			heads.put("Range", "bytes=" + filePart.length() + "-");
		}

		return HttpClient.downloadFile(filePart, mUrl, heads, new HttpClient.Callback() {
			@Override
			public boolean isCancelled() {
				Listener listener = mListener;
				return listener != null && listener.isCancelled();
			}

			@Override
			public void onProgress(long progress, long total) {
				Listener listener = mListener;
				if (listener != null)
					listener.onProgress(progress, total);
				mLock.length = total;
			}
		});
	}

	public static Map<String, List<String>> responseHeads(String mimeType, long lastModified) {
		final HashMap<String, List<String>> heads = new HashMap<>(2);
		heads.put("Content-Type", Collections.singletonList(mimeType));
		heads.put("Last-Modified", Collections.singletonList(Long.toString(lastModified)));
		return heads;
	}

	public static Future<File> download(String url, boolean supportRange, Map<String, String> heads,
								  DiskCache cache, Listener listener) {
		return new Downloader(url, supportRange, heads, cache, listener).submit();
	}
}
