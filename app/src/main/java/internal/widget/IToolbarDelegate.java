package internal.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import io.library.widget.DrawerDelegate;


public class IToolbarDelegate implements DrawerDelegate {

	private final IToolbar mToolbar;

	public IToolbarDelegate(IToolbar toolbar) {
		mToolbar = toolbar;
	}

	@Override
	public Context getThemedContext() {
		return mToolbar.getContext();
	}

	@Override
	public void setUpIndicator(Drawable drawable) {
		mToolbar.setNavigationIcon(drawable);
	}

	@Override
	public void setDescription(int descRes) {
		mToolbar.setTitle(descRes);
	}
}
