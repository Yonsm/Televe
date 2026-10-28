package io.library.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import io.library.utils.DiskCache;
import io.library.utils.IoUtils;


public class ImageDiskCache {
	private static final String TAG = "ImageDiskCache";

	private static final int MAGIC = 0x63646D69; // 'imdc'

	private static final long CACHE_SIZE = 256 * 1024 * 1024;
	private static final String CACHE_DIR = "image_cache";
	private static ImageDiskCache mInstance;

	public static synchronized ImageDiskCache getInstance(Context context) {
		if (mInstance == null) {
			final File dir = context.getExternalCacheDir();
			final File cacheDir = new File(dir != null ? dir : context.getCacheDir(), CACHE_DIR);
			mInstance = new ImageDiskCache(cacheDir, CACHE_SIZE);
		}
		return mInstance;
	}

	private final DiskCache mCache;

	public ImageDiskCache(File dir, long maxSize) {
		mCache = new DiskCache(dir, maxSize);
	}

	public long size() {
		return mCache.size();
	}

	public void close() {
		mCache.close();
	}

	public void clear() {
		mCache.clear();
	}

	public ImageDrawable get(String skey) {
		if (!mCache.isOpened())
			mCache.open();

		long key = DiskCache.hash64(skey);
		File file = mCache.getCacheFile(key);
		if (file.length() == 0)
			return null;

		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
			if (dis.readInt() != MAGIC)
				return null;

			int degree = dis.readInt();
			int width = dis.readInt();
			int height = dis.readInt();
			String mimeType = dis.readUTF();
			Bitmap bitmap = BitmapFactory.decodeStream(dis);
			if (bitmap != null) {
				ImageDrawable image = new ImageDrawable(bitmap, degree, mimeType);
				image.mOriginalWidth = width;
				image.mOriginalHeight = height;
				return image;
			}
		} catch (Throwable e) {
			Log.w(TAG, "decode failed", e);
		} finally {
			IoUtils.closeSilently(dis);
			mCache.accessCache(key);
		}
		return null;
	}

	public void put(String skey, ImageDrawable image) {
		if (!mCache.isOpened())
			mCache.open();

		long key = DiskCache.hash64(skey);
		File file = mCache.getCacheFile(key);
		DataOutputStream dos = null;
		try {
			dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			dos.writeInt(MAGIC);
			dos.writeInt(image.mDegree);
			dos.writeInt(image.mOriginalWidth);
			dos.writeInt(image.mOriginalHeight);
			dos.writeUTF(image.mMimeType);
			image.compress("image/png".equalsIgnoreCase(image.mMimeType)
					? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 80, dos);
			mCache.updateCache(key);
		} catch (Throwable e) {
			Log.w(TAG, "compress failed", e);
		} finally {
			IoUtils.closeSilently(dos);
		}
	}

	public void remove(String skey) {
		if (!mCache.isOpened())
			mCache.open();

		long key = DiskCache.hash64(skey);
		mCache.removeCache(key);
	}
}
