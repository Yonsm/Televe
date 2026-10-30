package internal.menu;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class MenuAdapter extends BaseAdapter {
	public static final int CRITICAL_COLOR = 0xFFF1522C;

	protected final int mLayoutResId;
	protected final int mCriticalColor;
	protected final boolean mLayoutRTL;
	protected final Context mContext;
	protected boolean mShowIcons;
	protected ColorStateList mDefaultTextColors;
	protected final ArrayList<ToolMenuItem> mItems = new ArrayList<>(8);

	public MenuAdapter(Context context, int layoutResId) {
		final Resources res = context.getResources();
		mContext = context;
		mLayoutResId = layoutResId;
		mCriticalColor = CRITICAL_COLOR;
		mLayoutRTL = (res.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_LAYOUTDIR_MASK) == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL;
	}

	public void setDefaultTextColors(ColorStateList colors) {
		mDefaultTextColors = colors;
	}

	public void update(ToolMenu menu) {
		final int count = menu.size();
		mItems.clear();
		mItems.ensureCapacity(count);
		for (int i = 0; i < count; i++) {
			final ToolMenuItem item = (ToolMenuItem) menu.getItem(i);
			if (item.isVisible())
				mItems.add(item);
		}
		mShowIcons = menu.isShowIcons();
	}

	public int[] measureSize() {
		final Resources res = mContext.getResources();
//		final int minWidth = res.getDimensionPixelSize(R.dimen.menu_min_width);
//		final int maxWidth = res.getDimensionPixelSize(R.dimen.menu_max_width);
		final int size[] = measureChildren(this);
//		size[0] = Math.min(Math.max(size[0], minWidth), maxWidth);
		return size;
	}

	public int getCount() {
		return mItems.size();
	}

	public Object getItem(int position) {
		return mItems.get(position);
	}

	public long getItemId(int position) {
		return mItems.get(position).getItemId();
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		final LinearLayout view;
		final ViewHolder holder;
		if (convertView == null) {
			view = (LinearLayout) View.inflate(mContext, mLayoutResId, null);
			holder = new ViewHolder(view);
			view.setTag(holder);
			if (mLayoutRTL) {
				final int count = view.getChildCount();
				for (int i = 0; i < count / 2; i++) {
					view.bringChildToFront(view.getChildAt(i));
				}
				holder.titleView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
			}
			if (mDefaultTextColors == null)
				mDefaultTextColors = holder.titleView.getTextColors();
		} else {
			view = (LinearLayout) convertView;
			holder = (ViewHolder) view.getTag();
		}

		final ToolMenuItem item = mItems.get(position);
		final boolean enabled = item.isEnabled();

		holder.iconView.setVisibility(mShowIcons ? View.VISIBLE : View.GONE);
		if (mShowIcons)
			holder.iconView.setImageDrawable(item.getIcon());
		item.recycleIcon();

		final int flags = item.getFlags();
		if ((flags & ToolMenuItem.FLAG_CRITICAL) != 0)
			holder.titleView.setTextColor(mCriticalColor);
		else
			holder.titleView.setTextColor(mDefaultTextColors);
		holder.titleView.setText(item.getTitle());
		holder.titleView.setEnabled(enabled);

		if (item.isCheckable()) {
			final boolean checked = item.isChecked();
			final boolean radio = (flags & ToolMenuItem.FLAG_RADIO) != 0;
			if (radio && holder.radioView != null) {
				holder.radioView.setChecked(checked);
				holder.radioView.setVisibility(View.VISIBLE);
			} else if (!radio && holder.checkView != null) {
				holder.checkView.setChecked(checked);
				holder.checkView.setVisibility(View.VISIBLE);
			}
		} else {
			if (holder.checkView != null)
				holder.checkView.setVisibility(View.GONE);
			if (holder.radioView != null)
				holder.radioView.setVisibility(View.GONE);
		}
		return view;
	}

	private static class ViewHolder {
		ImageView iconView;
		TextView titleView;
		CompoundButton checkView;
		CompoundButton radioView;

		ViewHolder(View itemView) {
			iconView = (ImageView) itemView.findViewById(android.R.id.icon);
			titleView = (TextView) itemView.findViewById(android.R.id.title);
			checkView = (CompoundButton) itemView.findViewById(android.R.id.checkbox);
			radioView = (CompoundButton) itemView.findViewById(android.R.id.selectedIcon);
		}
	}

	public static int[] measureChildren(ListAdapter adapter) {
		final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		final int count = adapter.getCount();
		int width = 0;
		int height = 0;
		View view = null;
		for (int i = 0; i < count; i++) {
			view = adapter.getView(i, view, null);
			view.measure(widthMeasureSpec, heightMeasureSpec);
			width = Math.max(view.getMeasuredWidth(), width);
			height += view.getMeasuredHeight();
		}
		return new int[] {width, height};
	}
}
