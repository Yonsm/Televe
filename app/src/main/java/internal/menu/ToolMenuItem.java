package internal.menu;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import android.view.ActionProvider;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

public class ToolMenuItem implements MenuItem {
	public static final int FLAG_CHECKABLE = 0x01;
	public static final int FLAG_CHECKED = 0x02;
	public static final int FLAG_DISABLED = 0x04;
	public static final int FLAG_INVISIBLE = 0x08;
	public static final int FLAG_RADIO = 0x10;
	public static final int FLAG_CRITICAL = 0x20;

	private final ToolMenu mParentMenu;
	private final Context mContext;
	private final int mGroupId;
	private final int mItemId;
	private int mFlags;
	private CharSequence mTitle;
	private Drawable mIcon;
	private int mIconRes;
	private SubMenu mSubMenu;

	public static ToolMenuItem createFromView(View view) {
		return new ToolMenuItem(null, view.getContext(), 0, view.getId(), null);
	}

	public ToolMenuItem(ToolMenu menu, Context context, int groupId, int itemId, CharSequence title) {
		mParentMenu = menu;
		mContext = context;
		mGroupId = groupId;
		mItemId = itemId;
		mTitle = title;
	}

	public int getGroupId() {
		return mGroupId;
	}

	public int getItemId() {
		return mItemId;
	}

	public MenuItem setTitle(CharSequence title) {
		mTitle = title;
		return this;
	}

	public MenuItem setTitle(int titleRes) {
		if (titleRes != 0)
			setTitle(mContext.getString(titleRes));
		return this;
	}

	public CharSequence getTitle() {
		return mTitle;
	}

	public MenuItem setTitleCondensed(CharSequence title) {
		return this;
	}

	public CharSequence getTitleCondensed() {
		return mTitle;
	}

	public MenuItem setIcon(int icon) {
		mIconRes = icon;
		return this;
	}

	public MenuItem setIcon(Drawable icon) {
		mIcon = icon;
		mIconRes = 0;
		return this;
	}

	public Drawable getIcon() {
		if (mIcon == null && mIconRes != 0)
			mIcon = getIcon(mContext, mIconRes);
		return mIcon;
	}

	public void recycleIcon() {
		if (mIconRes != 0)
			mIcon = null;
	}

	protected void setSubMenu(SubMenu subMenu) {
		mSubMenu = subMenu;
	}

	public boolean hasSubMenu() {
		return mSubMenu != null;
	}

	public SubMenu getSubMenu() {
		return mSubMenu;
	}

	public MenuItem setCheckable(boolean checkable) {
		if (checkable)
			mFlags |= FLAG_CHECKABLE;
		else
			mFlags &= ~FLAG_CHECKABLE;
		return this;
	}

	public boolean isCheckable() {
		return (mFlags & FLAG_CHECKABLE) == FLAG_CHECKABLE;
	}

	public MenuItem setChecked(boolean checked) {
		if (checked && mGroupId != 0 && (mFlags & FLAG_RADIO) != 0) {
			final int count = mParentMenu.size();
			for (int i = 0; i < count; i++) {
				final MenuItem item = mParentMenu.getItem(i);
				if (item.getGroupId() == mGroupId && item instanceof ToolMenuItem)
					((ToolMenuItem) item).mFlags &= ~FLAG_CHECKED;
			}
		}

		if (checked)
			mFlags |= FLAG_CHECKED;
		else
			mFlags &= ~FLAG_CHECKED;
		return this;
	}

	public boolean isChecked() {
		return (mFlags & FLAG_CHECKED) == FLAG_CHECKED;
	}

	public MenuItem setEnabled(boolean enabled) {
		if (!enabled)
			mFlags |= FLAG_DISABLED;
		else
			mFlags &= ~FLAG_DISABLED;
		return this;
	}

	public boolean isEnabled() {
		return (mFlags & FLAG_DISABLED) == 0;
	}

	public MenuItem setVisible(boolean visible) {
		if (!visible)
			mFlags |= FLAG_INVISIBLE;
		else
			mFlags &= ~FLAG_INVISIBLE;
		return this;
	}

	public boolean isVisible() {
		return (mFlags & FLAG_INVISIBLE) == 0;
	}

	public int getFlags() {
		return mFlags;
	}

	public ToolMenuItem setFlags(int flags) {
		mFlags = flags;
		return this;
	}

	private int mShowAsAction = SHOW_AS_ACTION_NEVER;
	public int getShowAsAction() {
		return mShowAsAction;
	}

	public void setShowAsAction(int actionEnum) {
		mShowAsAction = actionEnum;
	}

	public MenuItem setShowAsActionFlags(int actionEnum) {
		mShowAsAction = actionEnum;
		return this;
	}

	private MenuItem.OnMenuItemClickListener mClickListener;
	public MenuItem.OnMenuItemClickListener getOnMenuItemClickListener() {
		return mClickListener;
	}

	public MenuItem setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener clickListener) {
		mClickListener = clickListener;
		return this;
	}

	public boolean performClick() {
		return mClickListener != null && mClickListener.onMenuItemClick(this);
	}

	public int getOrder() {
		return Menu.NONE;
	}

	private View mActionView;
	public View getActionView() {
		return mActionView;
	}

	public MenuItem setActionView(View view) {
		mActionView = view;
		return this;
	}

	public MenuItem setActionView(int resId) {
		mActionView = resId != 0 ? View.inflate(mContext, resId, null) : null;
		return this;
	}

	private ActionProvider mActionProvider;
	public MenuItem setActionProvider(ActionProvider actionProvider) {
		mActionProvider = actionProvider;
		return this;
	}

	public ActionProvider getActionProvider() {
		return mActionProvider;
	}

	public boolean expandActionView() {
		return false;
	}

	public boolean collapseActionView() {
		return false;
	}

	public boolean isActionViewExpanded() {
		return false;
	}

	public MenuItem setOnActionExpandListener(MenuItem.OnActionExpandListener onActionExpandListener) {
		return this;
	}

	private Intent mIntent;
	public MenuItem setIntent(Intent intent) {
		mIntent = intent;
		return this;
	}

	public Intent getIntent() {
		return mIntent;
	}

	private char mAlphabeticShortcut, mNumericShortcut;
	public MenuItem setShortcut(char numericChar, char alphaChar) {
		mAlphabeticShortcut = alphaChar;
		mNumericShortcut = numericChar;
		return this;
	}

	public MenuItem setAlphabeticShortcut(char alphaChar) {
		mAlphabeticShortcut = alphaChar;
		return this;
	}

	public MenuItem setNumericShortcut(char numericChar) {
		mNumericShortcut = numericChar;
		return this;
	}

	public char getNumericShortcut() {
		return mNumericShortcut;
	}

	public char getAlphabeticShortcut() {
		return mAlphabeticShortcut;
	}

	public ContextMenu.ContextMenuInfo getMenuInfo() {
		return null;
	}

	public static Drawable getIcon(Context context, int resId) {
		return getIcon(context, resId, Color.TRANSPARENT);
	}

	public static Drawable getIcon(Context context, int resId, int tintColor) {
		Resources res = context.getResources();
		Resources.Theme theme = context.getTheme();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			Drawable drawable = res.getDrawable(resId, theme);
			if (tintColor != Color.TRANSPARENT) {
				drawable = drawable.mutate();
				drawable.setTint(tintColor);
			}
			return drawable;
		} else {
			VectorDrawableCompat vector = VectorDrawableCompat.create(res, resId, theme);
			if (vector != null) {
				if (tintColor != Color.TRANSPARENT)
					vector.setTint(tintColor);
				return vector;
			}

			Drawable drawable = res.getDrawable(resId);
			if (tintColor != Color.TRANSPARENT) {
				drawable = drawable.mutate();
				drawable.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
			}
			return drawable;
		}
	}
}
