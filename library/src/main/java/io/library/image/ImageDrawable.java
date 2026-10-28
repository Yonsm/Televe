package io.library.image;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.media.ExifInterface;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import io.library.utils.IoUtils;
import io.library.utils.Size;
import io.library.utils.UriUtils;


public class ImageDrawable extends Drawable {
	protected static final String TAG = "ImageDrawable";

	public static final int NO_DEGREE = 360;

	// 视频缩略图并行硬解许可数：当前 TV 设备 CPU/解码器普遍够用，2 核老设备已淘汰，固定 4 路并发。
	private static Semaphore sDecoderSemaphore = new Semaphore(4);

	// 解码后的 bitmap 复用池，减少低端 TV 上频繁分配/回收造成的 GC 抖动
	private static final BitmapPool sBitmapPool = new BitmapPool(8);

	protected int mDegree;
	protected int mWidth;
	protected int mHeight;
	protected int mOriginalWidth;
	protected int mOriginalHeight;
	protected Bitmap mBitmap;
	protected Paint mPaint;
	protected Matrix mMatrix;
	protected String mMimeType;
	private final AtomicInteger mCounter = new AtomicInteger(1);

	public ImageDrawable() {
	}

	public ImageDrawable(Bitmap bitmap) {
		this(bitmap, 0, "image/bmp");
	}

	public ImageDrawable(Bitmap bitmap, int degree, String mimeType) {
		mDegree = (degree + 360) % 360;
		mOriginalWidth = mWidth = bitmap.getWidth();
		mOriginalHeight = mHeight = bitmap.getHeight();
		mBitmap = bitmap;
		mMimeType = mimeType;
	}

	public ImageDrawable addRef() {
		mCounter.incrementAndGet();
		return this;
	}

	public int release() {
		final int ref = mCounter.decrementAndGet();
		if (ref == 0)
			recycle();
		else if (ref < 0)
			Log.w(TAG, "destroyed: " + this + ", ref=" + ref);
		return ref;
	}

	public Bitmap detach() {
		final Bitmap bitmap = mBitmap;
		mBitmap = null;
		mPaint = null;
		mMatrix = null;
		mMimeType = null;
		return bitmap;
	}

	public boolean isShareable() {
		return true;
	}

	public boolean isValid() {
		return mBitmap != null;
	}

	public void setDegree(int degree) {
		if (mDegree != degree) {
			mDegree = degree;
			if (mMatrix != null)
				getBaseMatrix(mMatrix);
		}
	}

	public boolean compress(Bitmap.CompressFormat format, int quality, OutputStream stream) {
		try {
			final Bitmap bitmap = mBitmap;
			if (bitmap != null) {
				bitmap.compress(format, quality, stream);
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	protected void recycle() {
		final Bitmap bitmap = detach();
		if (bitmap != null && !bitmap.isRecycled() && !sBitmapPool.release(bitmap))
			bitmap.recycle();
	}

//	Check if any object leaking in finalize()
	@Override
	protected void finalize() throws Throwable {
		final int ref = mCounter.get();
		if (ref > 0 && isValid())
			Log.w(TAG, "leak image: " + this + ", ref=" + ref);
		super.finalize();
	}

	@Override
	public void draw(Canvas canvas) {
		final Bitmap bitmap = mBitmap;
		if (bitmap != null) try {
			Matrix matrix = mMatrix;
			if (matrix == null && mDegree != 0)
				matrix = getMatrix();
			if (matrix != null) {
				canvas.drawBitmap(bitmap, matrix, getPaint());
			} else {
				canvas.drawBitmap(bitmap, null, getBounds(), getPaint());
			}
		} catch (Exception e) {
			Log.w(TAG, "draw failed: ",e);
		}
	}

	public int getByteCount() {
		final Bitmap bitmap = mBitmap;
		return bitmap != null ? bitmap.getByteCount() : 0;
	}

	public void getBaseMatrix(Matrix matrix) {
		getBaseMatrix(matrix, mWidth, mHeight, mDegree);
	}

	public void getCenterOrCropMatrix(Matrix matrix, int width, int height, boolean center) {
		int cxImage = getIntrinsicWidth();
		int cyImage = getIntrinsicHeight();
		getCenterOrCropMatrix(matrix, cxImage, cyImage, mDegree, width, height, center);
	}

	public int getDegree() {
		return mDegree;
	}

	public Matrix getMatrix() {
		if (mMatrix == null) {
			mMatrix = new Matrix();
			getBaseMatrix(mMatrix);
		}
		return mMatrix;
	}

	public String getMimeType() {
		return mMimeType;
	}

	public Paint getPaint() {
		if (mPaint == null)
			mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
		return mPaint;
	}

	public int getBitmapWidth() {
		return mWidth;
	}

	public int getBitmapHeight() {
		return mHeight;
	}

	public int getOriginalBitmapWidth() {
		return mOriginalWidth != 0 ? mOriginalWidth : mWidth;
	}

	public int getOriginalBitmapHeight() {
		return mOriginalHeight != 0 ? mOriginalHeight : mHeight;
	}

	@Override
	public int getIntrinsicWidth() {
		return mDegree % 180 == 0 ? mWidth : mHeight;
	}

	@Override
	public int getIntrinsicHeight() {
		return mDegree % 180 == 0 ? mHeight : mWidth;
	}

	@Override
	public int getOpacity() {
		final Bitmap bitmap = mBitmap;
		final Paint paint = mPaint;
		return bitmap == null || bitmap.hasAlpha() || (paint != null && paint.getAlpha() < 255)
				? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
	}

	@Override
	public void setAlpha(int alpha) {
		getPaint().setAlpha(alpha);
	}

	@Override
	public void setFilterBitmap(boolean filter) {
		getPaint().setFilterBitmap(filter);
	}

	@Override
	public void setColorFilter(ColorFilter filter) {
		getPaint().setColorFilter(filter);
	}

	public static Bitmap decodeBitmap(FileDescriptor fd, BitmapFactory.Options opts) {
		try {
			// avoid out of connection resource
			sDecoderSemaphore.acquire();
		} catch (Exception ignored) {
			return null;
		}

		if (opts == null)
			opts = new BitmapFactory.Options();

		final Bitmap.Config prefConfig = opts.inPreferredConfig;
		final int maxSampleSize = opts.inSampleSize * 4;
		try {
			while (opts.inSampleSize <= maxSampleSize) {
				// 尝试复用池中已释放的 bitmap 内存，减少 GC 抖动
				final Bitmap reused = sBitmapPool.acquire(opts);
				opts.inBitmap = reused;
				opts.inMutable = true;
				try {
					return BitmapFactory.decodeFileDescriptor(fd, null, opts);
				} catch (OutOfMemoryError e) {
					sBitmapPool.release(reused); // 复用未生效，归还池
					opts.inBitmap = null;
					if (opts.inPreferredConfig == Bitmap.Config.ARGB_8888) {
						opts.inPreferredConfig = Bitmap.Config.RGB_565;
					} else {
						opts.inSampleSize *= 2;
						opts.inPreferredConfig = prefConfig;
					}
				} catch (Throwable e) {
					sBitmapPool.release(reused);
					opts.inBitmap = null;
					Log.e(TAG, "decode failed: " + fd, e);
					break;
				}
			}
		} finally {
			sDecoderSemaphore.release();
		}
		return null;
	}

	public static Bitmap decodeVideoThumbnail(Object source, long timeUs) {
		return decodeVideoThumbnail(source, timeUs, null);
	}

	public static Bitmap decodeVideoThumbnail(Object source, long timeUs, BitmapFactory.Options opts) {
		try {
			// avoid out of connection resource
			sDecoderSemaphore.acquire();
		} catch (Exception ignored) {
			return null;
		}
		final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			String url = source.toString();
			if (source instanceof FileDescriptor)
				retriever.setDataSource((FileDescriptor) source);
			else if (source instanceof ParcelFileDescriptor)
				retriever.setDataSource(((ParcelFileDescriptor) source).getFileDescriptor());
			else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && source instanceof MediaDataSource)
				retriever.setDataSource((MediaDataSource) source);
			else if (UriUtils.isLocalUrl(url))
				retriever.setDataSource(url);
			else
				retriever.setDataSource(url, Collections.emptyMap());
			if (opts != null) try {
				opts.outMimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
				opts.outWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
				opts.outHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
			} catch (Exception e) {
				Log.w(TAG, "get metadata failed: " + source, e);
			}
			Bitmap bitmap = null;
			byte[] data = retriever.getEmbeddedPicture();
			if (data != null)
				bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
			if (bitmap == null)
				bitmap = retriever.getFrameAtTime(timeUs);
			return bitmap;
		} catch (Throwable e) {
			Log.e(TAG, "decode video failed: " + source, e);
		} finally {
			sDecoderSemaphore.release();
			try {
				retriever.release();
			} catch (IOException e) {
				Log.e(TAG, "release retriever failed: " + source, e);
			}
		}
		return null;
	}

	public static ImageDrawable decodeImage(Context context, Uri uri, int degree, Size maxSize,
											BitmapFactory.Options opts) {
		AssetFileDescriptor afd = null;
		try {
			afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");

			// 小尺寸缩略图优先用系统预生成，免解码全尺寸原图（API 29+）
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
					&& maxSize != null && maxSize.max() > 0 && maxSize.max() <= 200) {
				try {
					Bitmap thumb = context.getContentResolver().loadThumbnail(uri,
							new android.util.Size(maxSize.max(), maxSize.max()), null);
					if (thumb != null) {
						afd.close();
						// 系统预生成缩略图已按 EXIF 纠正方向，用 NO_DEGREE 避免与 bean.degree 二次旋转
						return new ImageDrawable(thumb, ImageDrawable.NO_DEGREE, "image/jpeg");
					}
				} catch (Exception ignored) {
				}
			}

			if (opts == null)
				opts = new BitmapFactory.Options();

            final FileDescriptor fd = afd.getFileDescriptor();
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeFileDescriptor(fd, null, opts);
			opts.inJustDecodeBounds = false;
			// 缩略图(目标尺寸 <=1024px)大多是不透明 JPEG/WEBP，用 RGB_565 内存减半且解码更快；
			// PNG/GIF 保留 ARGB_8888；全屏大图(LargeImageView 的 4096 fallback)也保留 8888 避免褪色丢 alpha
			if (opts.inPreferredConfig == null && maxSize != null && maxSize.max() <= 1024) {
				final String mime = opts.outMimeType;
				if (mime != null && !mime.equals("image/png") && !mime.equals("image/gif"))
					opts.inPreferredConfig = Bitmap.Config.RGB_565;
			}
			if (degree == NO_DEGREE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				boolean seekable = false;
				try {
					seekable = Os.lseek(fd, 0, OsConstants.SEEK_SET) == 0;
					ExifInterface ei = new ExifInterface(fd);
					switch (ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
						case ExifInterface.ORIENTATION_ROTATE_90:
							degree = 90; break;
						case ExifInterface.ORIENTATION_ROTATE_180:
							degree = 180; break;
						case ExifInterface.ORIENTATION_ROTATE_270:
							degree = 270; break;
					}
				} catch (Exception ignored) {
				} finally {
					if (seekable)
						Os.lseek(fd, 0, OsConstants.SEEK_SET);
				}
			}

			int width = opts.outWidth;
			int height = opts.outHeight;
			opts.inSampleSize = maxSize != null ? maxSize.computeSampleSize(width, height) : 1;

			if ("image/gif".equals(opts.outMimeType)) {
				// 动图 GIF：用 android-gif-drawable 播放（native mediajni 已移除）
				try {
					return new GifCompatDrawable(afd);
				} catch (IOException ignored) {
				}
			}

			Bitmap bitmap;
			if (opts.outMimeType != null || opts.outWidth > 0 || opts.outHeight > 0) {
				bitmap = decodeBitmap(fd, opts);
			} else {
				bitmap = decodeVideoThumbnail(fd, -1, opts);
				width = opts.outWidth;
				height = opts.outHeight;
				opts.inSampleSize = 2; // Force cache the video thumbnail
			}
			if (bitmap != null) {
				bitmap = shrinkIfNecessary(bitmap, maxSize);
				ImageDrawable image = new ImageDrawable(bitmap, degree, opts.outMimeType);
				image.mOriginalWidth = width;
				image.mOriginalHeight = height;
				return image;
			}
			throw new RuntimeException("unsupported");
		} catch (FileNotFoundException ignored) {
		} catch (Throwable e) {
			Log.e(TAG, "decode failed: " + uri, e);
		} finally {
			IoUtils.closeSilently(afd);
		}
		return null;
	}

	public static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
		if (bitmap == null || bitmap.getConfig() != null)
			return bitmap;
		try {
			final Bitmap newBmp = bitmap.copy(Bitmap.Config.ARGB_8888, false);
			bitmap.recycle();
			bitmap = newBmp;
		} catch (Throwable e) {
			Log.e(TAG, "create GLCompatible failed: ", e);
		}
		return bitmap;
	}

	public static void getBaseMatrix(Matrix matrix, int cxImage, int cyImage, int degree) {
		matrix.reset();
		if (degree != 0) {
			final float cxHalf = cxImage * 0.5f;
			final float cyHalf = cyImage * 0.5f;
			matrix.preTranslate(-cxHalf, -cyHalf);
			matrix.postRotate(degree);
			if (degree % 180 == 0)
				matrix.postTranslate(cxHalf, cyHalf);
			else
				matrix.postTranslate(cyHalf, cxHalf);
		}
	}

	public static void getCenterOrCropMatrix(Matrix matrix, int cxImage, int cyImage, int degree, int width, int height, boolean center) {
		float scaleX = width / (float) cxImage;
		float scaleY = height / (float) cyImage;
		if (center)
			scaleX = scaleY = Math.min(scaleX, scaleY);
		else
			scaleX = scaleY = Math.max(scaleX, scaleY);
		matrix.postScale(scaleX, scaleY);
		matrix.postTranslate((width - cxImage * scaleX) * 0.5f, (height - cyImage * scaleY) * 0.5f);
	}

	public static float getMatrixScale(float[] values) {
		final float scaleX = values[Matrix.MSCALE_X];
		final float skewY = values[Matrix.MSKEW_Y];
		return (float) Math.hypot(scaleX, skewY);
	}

	public static float getMatrixRotation(float[] values) {
		final double ang = Math.atan2(values[Matrix.MSKEW_Y], values[Matrix.MSCALE_Y]);
		return (float) Math.toDegrees(ang);
	}

	public static Paint getPaint(Drawable drawable) {
		if (drawable instanceof ImageDrawable)
			return ((ImageDrawable) drawable).getPaint();
		else if (drawable instanceof BitmapDrawable)
			return ((BitmapDrawable) drawable).getPaint();
		else if (drawable instanceof ShapeDrawable)
			return ((ShapeDrawable) drawable).getPaint();
		else
			return null;
	}

	public static final int MAX_BITMAP_SIDE = 4096;
	public static final int MAX_MULTIPLE = 4;
	public static Bitmap shrinkIfNecessary(Bitmap bitmap, Size maxSize) {
		if (bitmap == null || maxSize == null)
			return bitmap;

		final int width = bitmap.getWidth();
		final int height = bitmap.getHeight();
		final int side = Math.max(width, height);
		final int maxSide = Math.min(maxSize.max() * MAX_MULTIPLE, MAX_BITMAP_SIDE);
		float scale = maxSize.computeCropScale(width, height);
		if (scale < 1 || side > maxSide) {
			//	check not big than 4096, can not draw with hardware canvas
			int outWidth = Math.round(width * scale);
			int outHeight = Math.round(height * scale);
			float scaleX = outWidth / (float) maxSide;
			float scaleY = outHeight / (float) maxSide;
			if (scaleX > 1f || scaleY > 1f) {
				scale /= Math.max(scaleX, scaleY);
				outWidth = Math.round(width * scale);
				outHeight = Math.round(height * scale);
			}
			try {
				final Bitmap bmpNew = Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true);
				if (bmpNew != null) {
					bitmap.recycle();
					bitmap = bmpNew;
				}
			} catch (Throwable e) {
				Log.e(TAG, "create scaled failed: " + maxSize, e);
			}
		}
		return bitmap;
	}

	/**
	 * 简易 bitmap 复用池：缓存已 release 的 mutable bitmap，供 {@link #decodeBitmap} 通过
	 * inBitmap 复用内存，减少低端 TV 上频繁分配/回收造成的 GC 抖动。线程安全。
	 */
	private static final class BitmapPool {
		private final ArrayList<Bitmap> mPool;
		private final int mMaxSize;
		private final Object mLock = new Object();

		BitmapPool(int maxSize) {
			mMaxSize = maxSize;
			mPool = new ArrayList<>(maxSize);
		}

		/** 按 inSampleSize 计算的目标尺寸与 config 查找可复用的 bitmap；找不到返回 null。 */
		Bitmap acquire(BitmapFactory.Options opts) {
			if (opts == null || opts.inSampleSize < 1 || opts.outWidth <= 0 || opts.outHeight <= 0)
				return null;
			final int targetW = (opts.outWidth + opts.inSampleSize - 1) / opts.inSampleSize;
			final int targetH = (opts.outHeight + opts.inSampleSize - 1) / opts.inSampleSize;
			final Bitmap.Config want = opts.inPreferredConfig;
			synchronized (mLock) {
				for (int i = mPool.size() - 1; i >= 0; i--) {
					final Bitmap b = mPool.get(i);
					if (b == null || b.isRecycled()) {
						mPool.remove(i);
						continue;
					}
					if (b.getWidth() == targetW && b.getHeight() == targetH
							&& (want == null || b.getConfig() == want)) {
						mPool.remove(i);
						return b;
					}
				}
			}
			return null;
		}

		/** 归还 bitmap 到池；返回 false 表示池满或不适合复用（调用方应自行 recycle）。 */
		boolean release(Bitmap bitmap) {
			if (bitmap == null || bitmap.isRecycled() || !bitmap.isMutable())
				return false;
			synchronized (mLock) {
				if (mPool.size() < mMaxSize) {
					mPool.add(bitmap);
					return true;
				}
			}
			return false;
		}
	}
}
