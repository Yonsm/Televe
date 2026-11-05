package io.library.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import io.library.drawable.DrawerArrowDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class DrawerLayout extends FrameLayout {
	private static final String TAG = "DrawerLayout";

	private static final int DEFAULT_SCRIM_COLOR = 0x99000000;
	private static final int DEFAULT_SHADOW_COLOR = 0x40000000;
	private static final int DEFAULT_ELEVATION = 6; // DP
	private static final int EDGE_SIZE = 20; // dp

	private static final Interpolator sInterpolator = new Interpolator() {
		@Override
		public float getInterpolation(float t) {
			t -= 1.0f;
			return t * t * t * t * t + 1.0f;
		}
	};

	public interface Listener {
		default void onDrawerOpening(DrawerLayout layout) {}
		void onDrawerOpened(DrawerLayout layout);
		void onDrawerClosed(DrawerLayout layout);
	}

	private boolean mDrawerOpened;
	private boolean mIndicatorEnabled;
	private boolean mFirstLayout = true;
	private int mAnimationDuration;
	private int mDrawerSize;
	private int mToolbarHeight;
	private int mScrimColor = DEFAULT_SCRIM_COLOR;
	private int mStatusBarColor = Color.TRANSPARENT;
	private float mStatusBarPercent = 1;
	private float mEdgeSize;
	private float mShadowSize;
	private DrawerArrowDrawable mArrow;
	private DrawerDelegate mDelegate;
	private View mToolbar;
	private View mDrawerView;
	private Listener mListener;
	private final Paint mShadowPaint;
	private final Paint mScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Rect mEmptyRect = new Rect();
	private final Rect mWindowInsets = new Rect();

	public DrawerLayout(Context context) {
		this(context, null);
	}

	public DrawerLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public DrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

		final Resources res = context.getResources();
		final float density = res.getDisplayMetrics().density;
		mAnimationDuration = res.getInteger(android.R.integer.config_mediumAnimTime);
		mEdgeSize = density * EDGE_SIZE;
		mShadowSize = density * DEFAULT_ELEVATION;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			mShadowPaint.setShader(new LinearGradient(0, 0, mShadowSize, 0,
					DEFAULT_SHADOW_COLOR, Color.TRANSPARENT, Shader.TileMode.CLAMP));
		} else
			mShadowPaint = null;

		ViewConfiguration config = ViewConfiguration.get(context);
		mInterceptSlop = config.getScaledEdgeSlop();
	}

	public void init(View toolbar, DrawerDelegate delegate, Listener listener) {
		if (delegate == null) {
			Activity activity = (Activity) getContext();
			delegate = new ActionBarDelegate(activity);
		}

		mToolbar = toolbar;
		mArrow = new DrawerArrowDrawable(delegate.getThemedContext());
		mDelegate = delegate;
		mDelegate.setUpIndicator(mArrow);
		mListener = listener;

		if (delegate instanceof ActionBarDelegate) {
			Drawable.Callback callback = mArrow.getCallback();
			if (callback instanceof ImageView) {
				ImageView view = (ImageView) callback;
				view.getLayoutParams().width = (int) (getResources().getDisplayMetrics().density * 56);
				view.setScaleType(ImageView.ScaleType.CENTER);
			}
		}

		if (mToolbar != null && mToolbar.getParent() == this) {
			mToolbarHeight = mToolbar.getLayoutParams().height;
			if (mToolbarHeight <= 0) {
				final int[] attrs = new int[]{android.R.attr.actionBarSize};
				final TypedArray ta = getContext().getTheme().obtainStyledAttributes(attrs);
				mToolbarHeight = ta.getDimensionPixelSize(0, 0);
				ta.recycle();
			}
		} else {
			mToolbarHeight = 0;
		}

		requestFitSystemWindows();
	}

	public Rect getWindowInsets() {
		return mWindowInsets;
	}

	public void openDrawer(boolean animate) {
		moveDrawer(true, animate);
	}

	public void closeDrawer(boolean animate) {
		moveDrawer(false, animate);
	}

	public boolean isDrawerOpened() {
		return mDrawerOpened;
	}

	public void setEdgeSwipeEnabled(boolean enabled) {
		mEdgeSize = enabled ? getResources().getDisplayMetrics().density * EDGE_SIZE : 0;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		if (item != null && item.getItemId() == android.R.id.home && mIndicatorEnabled) {
			toggle();
			return true;
		}
		return false;
	}

	public void setIndicatorEnabled(boolean enabled) {
		setIndicatorEnabled(enabled, false);
	}

	public void setIndicatorEnabled(boolean enabled, boolean animate) {
		mIndicatorEnabled = enabled;

		float current = mArrow.getProgress();
		float target = enabled ? 0 : 1;
		if (current == target)
			return;

		if (animate) {
			ValueAnimator animator = ValueAnimator.ofFloat(current, target);
			animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					mArrow.setProgress((float) animation.getAnimatedValue());
				}
			});
			animator.start();
		} else {
			mArrow.setProgress(target);
		}
	}

	public void setScrimColor(int color) {
		mScrimColor = color;
		invalidate();
	}

	public void setStatusBarStyle(int color, float percent) {
		mStatusBarColor = color;
		mStatusBarPercent = percent;
		postInvalidateOnAnimation(0, 0, getWidth(), mWindowInsets.top
				- (mToolbar != null ? mToolbar.getHeight() : 0));
	}

	public void toggle() {
		if (isDrawerOpened())
			closeDrawer(true);
		else
			openDrawer(true);
	}

	@Override
	protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
		if (child == mToolbar && mStatusBarColor != Color.TRANSPARENT) {
			float bottom = (mWindowInsets.top - mToolbar.getHeight()) * mStatusBarPercent;
			mScrimPaint.setColor(mStatusBarColor);
			canvas.drawRect(0, 0, getWidth(), bottom, mScrimPaint);
		}

		boolean ret = super.drawChild(canvas, child, drawingTime);

		float left = mDrawerSize + mDrawerView.getTranslationX();
		float value = left / mDrawerSize;
		if (value > 0) {
			if (child == mDrawerView) {
				if (mShadowPaint != null) {
					mShadowPaint.setAlpha((int) (255 * value));
					canvas.translate(left, 0);
					canvas.drawRect(0, 0, mShadowSize, getHeight(), mShadowPaint);
					canvas.translate(-left, 0);
				}
			} else if (mScrimColor != Color.TRANSPARENT) {
				final int baseAlpha = (mScrimColor & 0xff000000) >>> 24;
				final int alpha = (int) (baseAlpha * value);
				final int color = alpha << 24 | (mScrimColor & 0xffffff);
				final int saveCount = canvas.save();
				for (int i = getChildCount() - 1; i >= 0; i--) {
					View view = getChildAt(i);
					if (view != child && view != mDrawerView && !isContentView(view))
						canvas.clipRect(view.getX(), view.getY(), view.getX() + view.getWidth(),
								view.getY() + view.getHeight(), Region.Op.DIFFERENCE);
				}
				mScrimPaint.setColor(color);
				canvas.drawRect(child.getX(), child == mToolbar ? 0 : child.getY(),
						child.getX() + child.getWidth(), child.getY() + child.getHeight(), mScrimPaint);
				canvas.restoreToCount(saveCount);
//				Log.d(TAG, "drawChild: " + child);
			}
		}
		return ret;
	}

	@Override
	protected boolean fitSystemWindows(Rect insets) {
		boolean myToolbar = mToolbar != null && mToolbar.getParent() == this;
		mDrawerView.setPadding(0, myToolbar ? 0 : insets.top, 0, insets.bottom);

		boolean changed = false;
		// The top tool bar is always within the content area.
		if (myToolbar)
			changed |= applyInsets(mToolbar, insets, true, true, true, false);

		// If the window has not requested system UI layout flags, we need to
		// make sure its contents are not being covered by system UI...  though it
		// will still be covered by the tool bar since they have requested it to
		// overlay.
		final int vis = getWindowSystemUiVisibility();
		final boolean stable = (vis & SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0;
		final Rect rect = (vis & SYSTEM_UI_LAYOUT_FLAGS) == 0 ? insets : mEmptyRect;
		final int count = getChildCount();
		for (int i = count - 1; i >= 0; i--) {
			View child = getChildAt(i);
			if (child != mToolbar && child != mDrawerView && isContentView(child))
				changed |= applyInsets(child, rect, true, true, true, true);
		}

		if (stable || (mToolbar != null && mToolbar.getVisibility() == VISIBLE)) {
			// The action bar creates additional insets for its content to use.
			insets.top += mToolbarHeight;
		}
		mWindowInsets.set(insets);

		if (changed)
			requestLayout();

		return super.fitSystemWindows(insets);
	}

	@Override
	public View focusSearch(View focused, int direction) {
		View view = super.focusSearch(focused, direction);
//		Log.d(TAG, "focusSearch: " + ", " + direction + ", " + focused + " -> " + view);
		if (isEnabled()) {
			if (view == null && direction == FOCUS_LEFT && !isDrawerOpened())
				openDrawer(true);
			else if ((direction == FOCUS_LEFT || direction == FOCUS_RIGHT)
					&& isDrawerOpened() && !isOwned(mDrawerView, view))
				closeDrawer(true);
		}
		return view;
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		mDrawerView = findDrawerView();
		mDrawerView.setFocusable(false);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		mDrawerSize = mDrawerView.getMeasuredWidth();

		if (mFirstLayout) {
			mFirstLayout = false;
			closeDrawer(false);
		}
	}

	private boolean mDragging;
	private float mDraggingLastX = -1;
	private float mDraggingOffset = 0;
	private float mInterceptSlop;
	private float mInterceptStartX;

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		if (!isEnabled())
			return false;

//		Log.d(TAG, "onInterceptTouchEvent: " + event);
		final float x = event.getX();
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (mDrawerOpened) {
					mInterceptStartX = x;
					final View child = findTopChildUnder(mDrawerView, mInterceptStartX, event.getY());
					if (child == null)
						return true;
				} else if (event.getX() < Math.max(mEdgeSize, mDrawerSize + mDrawerView.getTranslationX())) {
					return true;
				}
				break;

			case MotionEvent.ACTION_MOVE:
				if (mDrawerOpened && Math.abs(x - mInterceptStartX) >= mInterceptSlop) {
					mDragging = true;
					mDraggingLastX = x;
					return true;
				}
				break;
		}
		return super.onInterceptTouchEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
//		Log.d(TAG, "onTouchEvent: " + event);
		final float x = event.getX();
		final float tx = mDrawerView.getTranslationX();
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (mDrawerOpened || x < Math.max(mEdgeSize, mDrawerSize + tx)) {
					if (tx <= -mDrawerSize) {
						float offset = -mDrawerSize + x;
						mDrawerView.setTranslationX(offset);
						setDrawerElevation((mDrawerSize + offset) / mDrawerSize);
					}
					mDragging = true;
					mDraggingLastX = x;
					return true;
				}
				break;

			case MotionEvent.ACTION_MOVE:
				if (mDragging && x < mDrawerSize) {
					mDraggingOffset = x - mDraggingLastX;
					mDraggingLastX = x;
					float offset = tx + mDraggingOffset;
					offset = Math.min(Math.max(offset, -mDrawerSize), 0);
					mDrawerView.setTranslationX(offset);
					setDrawerElevation((mDrawerSize + offset) / mDrawerSize);
					return true;
				}
				mDraggingLastX = x;
				break;

			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				if (mDrawerOpened && x >= mDrawerSize + tx) {
					closeDrawer(true);
				} else if (mDragging) {
					mDragging = false;
					moveDrawer(mDraggingOffset > 0, mAnimationDuration);
					return true;
				}
				break;
		}
		return super.onTouchEvent(event);
	}

	protected boolean applyInsets(View view, Rect insets, boolean left, boolean top, boolean right, boolean bottom) {
		boolean changed = false;
		LayoutParams lp = (LayoutParams) view.getLayoutParams();
		if (left && lp.leftMargin != insets.left) {
			changed = true;
			lp.leftMargin = insets.left;
		}
		if (top && lp.topMargin != insets.top) {
			changed = true;
			lp.topMargin = insets.top;
		}
		if (right && lp.rightMargin != insets.right) {
			changed = true;
			lp.rightMargin = insets.right;
		}
		if (bottom && lp.bottomMargin != insets.bottom) {
			changed = true;
			lp.bottomMargin = insets.bottom;
		}
		return changed;
	}

	protected View findDrawerView() {
		int count = getChildCount();
		for (int i = count - 1; i >= 0; i--) {
			View child = getChildAt(i);
			LayoutParams params = (LayoutParams) child.getLayoutParams();
			if (params.gravity == Gravity.LEFT)
				return child;
		}
		throw new RuntimeException("no gravity=left view!");
	}

	protected void moveDrawer(final boolean open, boolean animate) {
		moveDrawer(open, animate ? mAnimationDuration : 0);
	}

	protected void moveDrawer(final boolean open, int duration) {
		if (open && !mDrawerOpened && mListener != null)
			mListener.onDrawerOpening(this);
		mDrawerOpened = open;

		final int offset = mDrawerView.getFitsSystemWindows() ? 0 : mWindowInsets.left;
		final float target = open ? offset : - offset - mDrawerSize;
		final float current = mDrawerView.getTranslationX();
		if (current == target)
			return;

		if (duration > 0) {
			ObjectAnimator animator = ObjectAnimator.ofFloat(mDrawerView, "translationX", current, target);
			animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animator) {
					float value = (mDrawerSize + (float) animator.getAnimatedValue()) / mDrawerSize;
					setDrawerElevation(value);
					if (mArrow != null)
						mArrow.setProgress(value);
				}
			});
			animator.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animator) {
					onDrawerStateChanged(open);
				}
			});
			animator.setInterpolator(sInterpolator);
			animator.setDuration(duration);
			animator.start();
		} else {
			mDrawerView.setTranslationX(target - current);
			setDrawerElevation(open ? 1 : 0);
			if (mArrow != null)
				mArrow.setProgress(open ? 1 : 0);
			onDrawerStateChanged(open);
		}
	}

	protected void onDrawerStateChanged(boolean open) {
		mDrawerView.setFocusable(open);
		if (mDrawerView instanceof ViewGroup)
			((ViewGroup) mDrawerView).setDescendantFocusability(open ? ViewGroup.FOCUS_BEFORE_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
		if (mListener != null) {
			if (open)
				mListener.onDrawerOpened(this);
			else
				mListener.onDrawerClosed(this);
		}
	}

	protected void setDrawerElevation(float value) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			mDrawerView.setElevation(mShadowSize * value);
		postInvalidateOnAnimation();
	}

	public static View findTopChildUnder(View view, float x, float y) {
		if (view instanceof ViewGroup) {
			final ViewGroup parent = (ViewGroup) view;
			final int count = parent.getChildCount();
			for (int i = count - 1; i >= 0; i--) {
				final View child = parent.getChildAt(i);
				if (x >= child.getLeft() && x < child.getRight()
						&& y >= child.getTop() && y < child.getBottom()) {
					return child;
				}
			}
		}
		return null;
	}

	public static boolean isContentView(View view) {
		LayoutParams params = (LayoutParams) view.getLayoutParams();
		return params.width == LayoutParams.MATCH_PARENT && params.height == LayoutParams.MATCH_PARENT;
	}

	public static boolean isOwned(View view, View child) {
		if (child == null)
			return false;
		if (view == child)
			return true;
		for (ViewParent parent = child.getParent(); parent != null; parent = parent.getParent()) {
			if (parent == view)
				return true;
		}
		return false;
	}
}
