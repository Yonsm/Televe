package io.library.image;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Animatable;
import android.net.Uri;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import io.library.net.Downloader;
import io.library.net.HttpCache;
import io.library.utils.DiskCache;
import io.library.utils.DisplayUtils;
import io.library.utils.Size;
import io.library.utils.UriUtils;


public class ImageResolver {
	private static ImageResolver mInstance;

	public static synchronized ImageResolver getInstance(Context context) {
		if (mInstance == null)
			mInstance = new ImageResolver(context);
		return mInstance;
	}

	protected final DiskCache mDiskCache;
	protected final ImageDiskCache mImageCache;
	protected final ImageMemoryCache mMemoryCache;
	protected final int mMaxCacheSide;

	public ImageResolver(Context context) {
		mDiskCache = HttpCache.getDiskCache(context);
		mImageCache = ImageDiskCache.getInstance(context);
		mMemoryCache = ImageMemoryCache.getInstance(context);

		final Size size = DisplayUtils.getDisplaySize(context);
		mMaxCacheSide = size.max() >>> 1;
	}

	public Future<File> downloadUri(Uri uri, Map<String, String> heads, Downloader.Listener listener) {
		return onCreateDownloader(uri, heads != null ? heads : onGetHeads(), listener).submit();
	}

	public Uri getCacheUri(Uri uri) {
		if (UriUtils.isWebUri(uri))
			uri = Uri.fromFile(mDiskCache.getCacheFile(DiskCache.hash64(uri.toString())));
		return uri;
	}

	public ImageDrawable getFromMemoryCache(String cacheKey) {
		return mMemoryCache.get(cacheKey);
	}

	public void putToMemoryCache(String cacheKey, ImageDrawable image) {
		if (cacheKey != null && image != null)
			mMemoryCache.put(cacheKey, image);
	}

	public ImageDrawable loadLocalImage(Context context, Uri uri, int degree, Size maxSize) {
		if (context == null || uri == null)
			return null;

		final String key = getCacheKey(uri, maxSize);

		if (maxSize != null && maxSize.min() < mMaxCacheSide) {
			final ImageDrawable image = mImageCache.get(key);
			if (image != null) {
				if (degree != ImageDrawable.NO_DEGREE)
					image.mDegree = degree;
//				Log.i(TAG, "load from cache: " + key);
				return image;
			}
		}

		final BitmapFactory.Options opts = new BitmapFactory.Options();
		final ImageDrawable image = ImageDrawable.decodeImage(context, uri, degree, maxSize, opts);
		if (image != null && !(image instanceof Animatable) && opts.inSampleSize > 1
				&& Math.min(image.getBitmapWidth(), image.getBitmapHeight()) <= mMaxCacheSide) {
			mImageCache.put(key, image);
//			Log.i(TAG, "save cache: " + key);
		}
//		Log.i(TAG, "load from file: " + key);
		return image;
	}

	public Future<ImageDrawable> loadRemoteImage(Context context, final String url, Map<String, String> heads,
												 final int degree, final Size maxSize, Downloader.Listener listener) {
		if (context == null || url == null)
			return null;

		final WeakReference<Downloader.Listener> listenerRef = new WeakReference<>(listener);
		final WeakReference<Context> contextRef = new WeakReference<>(context);
		return Downloader.EXECUTOR.submit(new Callable<ImageDrawable>() {
			@Override
			public ImageDrawable call() {
				try {
					Downloader.Listener listener = listenerRef.get();
					File file = onCreateDownloader(Uri.parse(url), heads, listener).call();
					listener = listenerRef.get();
					if (listener != null && listener.isCancelled())
						return null;
					return loadLocalImage(contextRef.get(), Uri.fromFile(file), degree, maxSize);
				} catch (Exception e) {
					Downloader.Listener listener = listenerRef.get();
					if (listener != null)
						listener.onError(url, e);
				}
				return null;
			}
		});
	}

	public Downloader onCreateDownloader(Uri uri, Map<String, String> heads, Downloader.Listener listener) {
		return new Downloader(uri.toString(), true, heads, mDiskCache, listener);
	}

	public Map<String, String> onGetHeads() {
		return new HashMap<>();
	}

	public static String getCacheKey(String url, Size maxSize) {
		return url != null ? url + (maxSize != null ? "@" + maxSize.getKey() : "") : "";
	}

	public static String getCacheKey(Uri uri, Size maxSize) {
		return getCacheKey(uri != null ? uri.toString() : null, maxSize);
	}
}
