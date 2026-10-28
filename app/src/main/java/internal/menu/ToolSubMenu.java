package internal.menu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

public class ToolSubMenu extends ToolMenu implements SubMenu {
	private final MenuItem mItem;

	public ToolSubMenu(Context context, int layoutResId, MenuItem item) {
		super(context, layoutResId);
		mItem = item;
	}

	public SubMenu setHeaderTitle(int titleRes) {
		return this;
	}

	public SubMenu setHeaderTitle(CharSequence title) {
		return this;
	}

	public SubMenu setHeaderIcon(int iconRes) {
		return this;
	}

	public SubMenu setHeaderIcon(Drawable icon) {
		return this;
	}

	public SubMenu setHeaderView(View view) {
		return this;
	}

	public SubMenu setIcon(int iconRes) {
		mItem.setIcon(iconRes);
		return this;
	}

	public SubMenu setIcon(Drawable icon) {
		mItem.setIcon(icon);
		return this;
	}

	public MenuItem getItem() {
		return mItem;
	}

	public void clearHeader() {
	}
}
