package io.library.image;

import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;

/**
 * native mediajni 不可用时，用 android-gif-drawable 播放动图 GIF 的 ImageDrawable 包装。
 * 委托 pl.droidsonroids.gif.GifDrawable 渲染/动画，将其 Callback 路由回本包装，
 * 以兼容现有 ImageView 的 invalidate/schedule 生命周期。
 */
public class GifCompatDrawable extends ImageDrawable implements android.graphics.drawable.Animatable {

	private final GifDrawable mDelegate;

	public GifCompatDrawable(AssetFileDescriptor afd) throws IOException {
		mDelegate = new GifDrawable(afd);
		mMimeType = "image/gif";
		mOriginalWidth = mWidth = mDelegate.getIntrinsicWidth();
		mOriginalHeight = mHeight = mDelegate.getIntrinsicHeight();
		mDelegate.setCallback(new DelegateCallback());
	}

	@Override
	public boolean isShareable() {
		return false; // 动图不参与跨视图共享缓存
	}

	@Override
	public boolean isValid() {
		return mDelegate != null;
	}

	@Override
	public int getIntrinsicWidth() {
		return mDelegate.getIntrinsicWidth();
	}

	@Override
	public int getIntrinsicHeight() {
		return mDelegate.getIntrinsicHeight();
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void draw(Canvas canvas) {
		mDelegate.setBounds(getBounds());
		mDelegate.draw(canvas);
	}

	@Override
	public void setAlpha(int alpha) {
		mDelegate.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		mDelegate.setColorFilter(cf);
	}

	@Override
	public void start() {
		mDelegate.start();
	}

	@Override
	public void stop() {
		mDelegate.stop();
	}

	@Override
	public boolean isRunning() {
		return mDelegate.isRunning();
	}

	@Override
	protected void recycle() {
		if (mDelegate != null) {
			mDelegate.stop();
			mDelegate.recycle();
		}
		super.recycle();
	}

	/** 把 delegate 的回调路由到本 drawable 的 callback（通常是 ImageView）。 */
	private class DelegateCallback implements Callback {
		@Override
		public void invalidateDrawable(Drawable d) {
			Callback cb = getCallback();
			if (cb != null)
				cb.invalidateDrawable(GifCompatDrawable.this);
		}

		@Override
		public void scheduleDrawable(Drawable d, Runnable what, long when) {
			Callback cb = getCallback();
			if (cb != null)
				cb.scheduleDrawable(GifCompatDrawable.this, what, when);
		}

		@Override
		public void unscheduleDrawable(Drawable d, Runnable what) {
			Callback cb = getCallback();
			if (cb != null)
				cb.unscheduleDrawable(GifCompatDrawable.this, what);
		}
	}
}
