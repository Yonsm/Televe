package io.library.image;

import android.content.Context;
import android.util.DisplayMetrics;

import io.library.utils.DisplayUtils;
import io.library.utils.LruHashMap;


public class ImageMemoryCache extends LruHashMap<String, ImageDrawable> {
	public static final int MAX_SCREEN_COUNT = 4;

	public ImageMemoryCache(int maxSize) {
		super(maxSize);
	}

	@Override
	public ImageDrawable get(String key) {
		final ImageDrawable img = super.get(key);
		return img != null ? img.addRef() : null;
	}

	@Override
	public void put(String key, ImageDrawable image) {
		if (image != null) {
			image.addRef();
			super.put(key, image);
		}
	}

	@Override
	protected void onRemoved(String key, ImageDrawable image, boolean replace) {
		image.release();
	}

	@Override
	protected long sizeOf(String key, ImageDrawable image) {
		return image.getByteCount();
	}

	private static ImageMemoryCache mInstance;

	public static synchronized ImageMemoryCache getInstance(Context context) {
		if (mInstance == null) {
			final DisplayMetrics metrics = DisplayUtils.getDisplayMetrics(context);
			int screenPixels = metrics.widthPixels * metrics.heightPixels;
			mInstance = new ImageMemoryCache(screenPixels * 4 * MAX_SCREEN_COUNT);
		}
		return mInstance;
	}
}
