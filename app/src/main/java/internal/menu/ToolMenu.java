package internal.menu;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListPopupWindow;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import net.yonsm.televe.R;

import java.util.ArrayList;

public class ToolMenu extends ArrayList<ToolMenuItem> implements Menu {
	private boolean mShowIcons = true;
	private MenuItem.OnMenuItemClickListener mItemClickListener;
	protected final int mLayoutResId;
	protected final Context mContext;

	public ToolMenu(Context context) {
		this(context, R.layout.item_menu);
	}

	public ToolMenu(Context context, int layoutResId) {
		mContext = context;
		mLayoutResId = layoutResId;
	}

	public boolean isShowIcons() {
		return mShowIcons;
	}

	public void setShowIcons(boolean showIcons) {
		mShowIcons = showIcons;
	}

	public MenuItem add(CharSequence title) {
		return addItem(Menu.NONE, Menu.NONE, Menu.NONE, title);
	}

	public MenuItem add(int titleRes) {
		return addItem(Menu.NONE, Menu.NONE, Menu.NONE, mContext.getString(titleRes));
	}

	public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
		return addItem(groupId, itemId, order, title);
	}

	public MenuItem add(int groupId, int itemId, int order, int titleRes) {
		return addItem(groupId, itemId, order, mContext.getString(titleRes));
	}

	public ToolMenuItem addItem(int groupId, int itemId, int order, CharSequence title) {
		//	Log.d(TAG, "add item: " + "groupId=" + groupId + ", itemId=" + itemId + ", order=" + order + ", title=" + title);
		final ToolMenuItem item = new ToolMenuItem(this, mContext, groupId, itemId, title);
		super.add(item);
		return item;
	}

	public void copyTo(Menu menu) {
		for (ToolMenuItem item : this) {
			final MenuItem newItem;
			if (item.hasSubMenu()) {
				SubMenu subMenu = menu.addSubMenu(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
				((ToolSubMenu) item.getSubMenu()).copyTo(subMenu);
				newItem = subMenu.getItem();
			} else {
				newItem = menu.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
			}
			newItem.setCheckable(item.isCheckable())
					.setActionProvider(item.getActionProvider())
					.setActionView(item.getActionView())
					.setChecked(item.isChecked())
					.setIcon(item.getIcon())
					.setIntent(item.getIntent())
					.setOnMenuItemClickListener(item.getOnMenuItemClickListener())
					.setShortcut(item.getNumericShortcut(), item.getAlphabeticShortcut())
					.setVisible(item.isVisible())
					.setShowAsAction(item.getShowAsAction());
		}
	}

	public int findItemIndex(int id) {
		final int size = super.size();
		for (int i = 0; i < size; i++) {
			final ToolMenuItem item = get(i);
			if (item.getItemId() == id)
				return i;
		}
		return -1;
	}

	public void removeItem(int itemId) {
		removeItemAtIndex(findItemIndex(itemId));
	}

	public void removeItemAtIndex(int index) {
		if (index >= 0 && index < size())
			remove(index);
	}

	public MenuItem findItem(int id) {
		for (final ToolMenuItem item : this) {
			if (item.getItemId() == id) {
				return item;
			} else if (item.hasSubMenu()) {
				final MenuItem item2 = item.getSubMenu().findItem(id);
				if (item2 != null)
					return item2;
			}
		}
		return null;
	}

	public MenuItem getItem(int index) {
		return get(index);
	}

	public boolean hasVisibleItems() {
		for (final ToolMenuItem item : this) {
			if (item.isVisible())
				return true;
		}
		return false;
	}

	public SubMenu addSubMenu(CharSequence title) {
		return addSubMenuItem(Menu.NONE, Menu.NONE, Menu.NONE, title);
	}

	public SubMenu addSubMenu(int titleRes) {
		return addSubMenuItem(Menu.NONE, Menu.NONE, Menu.NONE, mContext.getText(titleRes));
	}

	public SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
		return addSubMenuItem(groupId, itemId, order, title);
	}

	public SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
		return addSubMenuItem(groupId, itemId, order, mContext.getText(titleRes));
	}

	private SubMenu addSubMenuItem(int groupId, int itemId, int order, CharSequence title) {
		//	Log.d(TAG, "add sub menu: " + "groupId=" + groupId + ", itemId=" + itemId + ", order=" + order + ", title=" + title);
		final ToolMenuItem item = addItem(groupId, itemId, order, title);
		final ToolSubMenu subMenu = new ToolSubMenu(mContext, mLayoutResId, item);
		item.setSubMenu(subMenu);
		subMenu.setShowIcons(mShowIcons);
		return subMenu;
	}

	public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
		final int mask = ToolMenuItem.FLAG_CHECKABLE | (exclusive ? ToolMenuItem.FLAG_RADIO : 0);
		for (ToolMenuItem item : this) {
			if (item.getGroupId() == group) {
				int flags = item.getFlags();
				if (checkable)
					flags |= mask;
				else
					flags &= ~mask;
				item.setFlags(flags);
			}
		}
		//	Log.d(TAG, "set group checkable: " + "groupId=" + group + ", checkable=" + checkable + ", exclusive=" + exclusive);
	}

	public void setGroupEnabled(int group, boolean enable) {
		for (ToolMenuItem item : this) {
			if (item.getGroupId() == group)
				item.setEnabled(enable);
		}
		//	Log.d(TAG, "set group enabled: " + "enable=" + enable);
	}

	public void setGroupVisible(int group, boolean visible) {
		for (ToolMenuItem item : this) {
			if (item.getGroupId() == group)
				item.setVisible(visible);
		}
		//	Log.d(TAG, "set group visible: " + "visible=" + visible);
	}

	public void removeGroup(int group) {
		for (int i = size() - 1; i >= 0; i--) {
			ToolMenuItem item = get(i);
			if (item.getGroupId() == group)
				removeItemAtIndex(i);
		}
		//	Log.d(TAG, "remove group: " + group);
	}

	public int addIntentOptions(int groupId, int itemId, int order, ComponentName componentName,
								Intent[] intents, Intent intent, int flags, MenuItem[] menuItems) {
		return 0;
	}

	//	Unsupported operations.
	public boolean isShortcutKey(int keyCode, KeyEvent keyEvent) {
		return false;
	}

	public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
		return false;
	}

	public boolean performIdentifierAction(int id, int flags) {
		return false;
	}

	public void setQwertyMode(boolean qwerty) {
	}

	public void close() {
	}

	//	Helper functions

	public MenuAdapter createAdapter() {
		final MenuAdapter adapter = new MenuAdapter(mContext, mLayoutResId);
		adapter.update(this);
		return adapter;
	}

	public void setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener listener) {
		mItemClickListener = listener;
	}

	public void show(View anchor) {
		show(anchor, null);
	}

	public void show(String titleOfDialog) {
		show(null, titleOfDialog);
	}

	public void show(final View anchor, final String titleOfDialog) {
		final PopupMenu.OnMenuItemClickListener listener = new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				final SubMenu subMenu;
				if (item.hasSubMenu() && (subMenu = item.getSubMenu()) instanceof ToolSubMenu) {
					((ToolSubMenu)subMenu).setOnMenuItemClickListener(mItemClickListener);
					((ToolSubMenu)subMenu).show(anchor);
					return true;
				} else if (item instanceof ToolMenuItem && ((ToolMenuItem) item).performClick()) {
					return true;
				} else if (mItemClickListener != null) {
					return mItemClickListener.onMenuItemClick(item);
				} else if (mContext instanceof Window.Callback) {
					return ((Window.Callback) mContext).onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
				}
				return false;
			}
		};

		if (anchor != null && anchor.isShown()) {
//			final PopupMenu popup = new PopupMenu(mContext, anchor);
//			copyTo(popup.getMenu());
//			popup.setOnMenuItemClickListener(listener);
//			showPopupMenu(popup);
			showPopupMenu(mContext, createAdapter(), anchor, listener);
		} else {
			showDialogMenu(mContext, createAdapter(), titleOfDialog, listener);
		}
	}

	//	Static functions

	public static void copyMenu(Menu src, Menu dst) {
		final int count = src.size();
		for (int i = 0; i < count; i++) {
			final MenuItem item = src.getItem(i);
			if (item.hasSubMenu()) {
				SubMenu subMenu = dst.addSubMenu(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle()).setIcon(item.getIcon());
				copyMenu(item.getSubMenu(), subMenu);
			} else {
				dst.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle()).setIcon(item.getIcon());//.setShowAsAction(item.mShowAsAction);
			}
		}
	}

	public static void inflateMenu(Context context, int resId, Menu menu, MenuInflater inflater) {
		if (inflater == null)
			inflater = new MenuInflater(context);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || menu instanceof ToolMenu) {
			inflater.inflate(resId, menu);
		} else {
			ToolMenu nm = new ToolMenu(context);
			inflater.inflate(resId, nm);
			nm.copyTo(menu);
		}
	}

	private static void showDialogMenu(final Context context, final MenuAdapter adapter, CharSequence title,
									   final PopupMenu.OnMenuItemClickListener clickListener) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setIcon(0);
		if (title != null)
			builder.setTitle(title);
		builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dlg, int position) {
				final ToolMenuItem item = (ToolMenuItem) adapter.getItem(position);
				if (item.isEnabled())
					clickListener.onMenuItemClick(item);
			}
		});
		final AlertDialog dialog = builder.show();

		// 菜单项文字直接沿用框架对话框标题的文字颜色:两者同处一个窗口、同一渲染环境,
		// 对比度永远一致。部分 ROM(如斐讯 N1)用 RRO overlay 把框架的列表项颜色改成与
		// 应用主题不符的白色(白底弹窗内不可见),且 app 主题内显式覆盖也会被绕过,故不依赖任何主题属性。
		TextView titleView = (TextView) dialog.findViewById(android.R.id.title);
		if (titleView == null) {
			final int id = context.getResources().getIdentifier("alertTitle", "id", "android");
			if (id != 0)
				titleView = (TextView) dialog.findViewById(id);
		}
		if (titleView != null) {
			adapter.setDefaultTextColors(titleView.getTextColors());
			adapter.notifyDataSetChanged();
		}
	}

	private static void showPopupMenu(final Context context, final MenuAdapter adapter,
									  final View anchor, final PopupMenu.OnMenuItemClickListener clickListener) {
		final Rect rect = new Rect();
		anchor.getWindowVisibleDisplayFrame(rect);
		int actionBarHeight = 0;
		if (context instanceof Activity) {
			final ActionBar actionBar = ((Activity) context).getActionBar();
			if (actionBar != null)
				actionBarHeight = actionBar.getHeight();
		}
		final int windowHeight = rect.height();
		final int maxHeight = windowHeight > rect.width() ? windowHeight - actionBarHeight * 3 / 2 : windowHeight;

		final int[] size = adapter.measureSize();
		final int style = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? android.R.attr.actionOverflowMenuStyle : android.R.attr.popupMenuStyle;
		final ListPopupWindow popup = new ListPopupWindow(context, null, style);
		popup.setAdapter(adapter);
		popup.setAnchorView(anchor);
		popup.setContentWidth(size[0]);
		if (size[1] > maxHeight) {
			size[1] = maxHeight;
			popup.setHeight(maxHeight);
		}
		popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
		popup.setModal(true);
		popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final ToolMenuItem item = (ToolMenuItem) adapter.getItem(position);
				if (item.isEnabled()) {
					if (popup.isShowing())
						popup.dismiss();
					clickListener.onMenuItemClick(item);
				}
			}
		});

		final int cxAnchor = anchor.getWidth();
		final int cyAnchor = anchor.getHeight();
		if (cxAnchor > size[0])
			popup.setHorizontalOffset((cxAnchor - size[0]) / 2);
		if (cyAnchor > size[1])
			popup.setVerticalOffset(cyAnchor - size[1]);
		popup.show();
	}

}
