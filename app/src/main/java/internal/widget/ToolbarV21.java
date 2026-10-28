package internal.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;

import net.yonsm.televe.R;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ToolbarV21 extends Toolbar implements IToolbar {
	private final ColorDrawable mBackground;

	public ToolbarV21(Context context) {
		super(context);
		mBackground = getBackgroundColorDrawable();
	}

	public ToolbarV21(Context context, AttributeSet attrs) {
		super(context, attrs);
		mBackground = getBackgroundColorDrawable();
	}

	public ToolbarV21(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		mBackground = getBackgroundColorDrawable();
	}

	private ColorDrawable getBackgroundColorDrawable() {
		Drawable drawable = getBackground();
		if (drawable instanceof ColorDrawable)
			return (ColorDrawable) drawable;
		else
			return new ColorDrawable(getResources().getColor(R.color.colorPrimary));
	}

	@Override
	public int getBackgroundColor() {
		return mBackground.getColor();
	}

	@Override
	public View getView() {
		return this;
	}

	@Override
	public void setOnMenuItemClickListener(final MenuItem.OnMenuItemClickListener listener) {
		setOnMenuItemClickListener(listener != null? new Toolbar.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				return listener.onMenuItemClick(item);
			}
		} : null);
	}
}
