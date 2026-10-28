package internal.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;


public interface IToolbar {
	Context getContext();
	Menu getMenu();
	View getView();

	int getBackgroundColor();
	void setBackgroundColor(int color);

	CharSequence getTitle();
	CharSequence getSubtitle();

	void setTitle(int resId);
	void setTitle(CharSequence title);
	void setSubtitle(int resId);
	void setSubtitle(CharSequence title);

	void setNavigationIcon(Drawable drawable);
	void setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener listener);
}
