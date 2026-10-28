package io.library.widget;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Method;


public class ActionBarDelegate implements DrawerDelegate {

	protected final ActionBar mActionBar;
	private Method mSetHomeAsUpIndicator;
	private Method mSetHomeActionContentDescription;
	private ImageView mUpIndicatorView;

	public ActionBarDelegate(Activity activity) {
		mActionBar = activity.getActionBar();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
			return;

		try {
			mSetHomeAsUpIndicator = ActionBar.class.getDeclaredMethod("setHomeAsUpIndicator", Drawable.class);
			mSetHomeActionContentDescription = ActionBar.class.getDeclaredMethod(
					"setHomeActionContentDescription", Integer.TYPE);
		} catch (NoSuchMethodException ignored) {
			mActionBar.setDisplayShowHomeEnabled(true);
			mUpIndicatorView = findUpIndicatorView(activity);
		}
	}

	@Override
	public Context getThemedContext() {
		return mActionBar.getThemedContext();
	}

	@Override
	public void setUpIndicator(Drawable drawable) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			mActionBar.setHomeAsUpIndicator(drawable);
			return;
		}

		if (mSetHomeAsUpIndicator != null) {
			try {
				mSetHomeAsUpIndicator.invoke(mActionBar, drawable);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (mUpIndicatorView != null) {
			mUpIndicatorView.setImageDrawable(drawable);
		}
	}

	@Override
	public void setDescription(int descRes) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			mActionBar.setHomeActionContentDescription(descRes);
			return;
		}

		if (mSetHomeActionContentDescription != null) {
			try {
				mSetHomeActionContentDescription.invoke(mActionBar, descRes);
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
					// For API 19 and earlier, we need to manually force the
					// action bar to generate a new content description.
					mActionBar.setSubtitle(mActionBar.getSubtitle());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			mActionBar.setTitle(descRes);
		}
	}

	public static ImageView findUpIndicatorView(Activity activity) {
		final View home = activity.findViewById(android.R.id.home);
		if (home == null) {
			// Action bar doesn't have a known configuration, an OEM messed with things.
			return null;
		} else {
			// Added by Nanling to hide the home
			final ViewGroup.LayoutParams params = home.getLayoutParams();
			params.width = 0;
			if (params instanceof ViewGroup.MarginLayoutParams) {
				final ViewGroup.MarginLayoutParams params1 = (ViewGroup.MarginLayoutParams) params;
				params1.leftMargin = params1.rightMargin = 0;
			}
			home.setLayoutParams(params);
			home.setVisibility(View.GONE);
		}

		final ViewGroup parent = (ViewGroup) home.getParent();
		final int childCount = parent.getChildCount();
		if (childCount != 2) {
			// No idea which one will be the right one, an OEM messed with things.
			return null;
		}

		final View first = parent.getChildAt(0);
		final View second = parent.getChildAt(1);
		View up = first.getId() == android.R.id.home ? second : first;

		ImageView upIndicatorView = null;
		if (up instanceof ImageView) {
			// Jackpot! (Probably...)
			upIndicatorView = (ImageView) up;
		}

		// Added by Nanling to find the up and fix it
		if (upIndicatorView == null) {
			final int id = activity.getResources().getIdentifier("up", "id", "android");
			up = id != 0 ? activity.findViewById(id) : null;
			upIndicatorView = (up instanceof ImageView) ? (ImageView) up : null;
		}
		if (upIndicatorView != null) {
			final ViewGroup.LayoutParams params = upIndicatorView.getLayoutParams();
			if (params instanceof ViewGroup.MarginLayoutParams) {
				final ViewGroup.MarginLayoutParams params1 = (ViewGroup.MarginLayoutParams) params;
				params1.leftMargin = params1.rightMargin = 0;
			}
			upIndicatorView.setLayoutParams(params);
			upIndicatorView.setPadding(0, 0, 0, 0);
			upIndicatorView.setScaleType(ImageView.ScaleType.CENTER);
		}
		return upIndicatorView;
	}
}
