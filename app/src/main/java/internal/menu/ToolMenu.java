package internal.menu;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import net.yonsm.televe.R;

public final class ToolMenu {
    public static final int GROUP_PROVIDER = 1;

    private ToolMenu() {}

    public static RecyclerView create(Context context, Menu menu, MenuItem.OnMenuItemClickListener action) {
        ArrayList<MenuItem> items = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;
            items.add(item);
            if (item.getItemId() == R.id.source || item.getGroupId() == GROUP_PROVIDER && item.isChecked()) selected = items.size() - 1;
        }
        RecyclerView list = new RecyclerView(context);
        list.setTag(selected);
        list.setBackgroundColor(context.getColor(R.color.drawer_background));
        list.setLayoutManager(new GridLayoutManager(context, 1));
        list.setHasFixedSize(true);
        list.setItemAnimator(null);
        list.setVerticalScrollBarEnabled(true);
        list.setAdapter(new RecyclerView.Adapter<Holder>() {
            @Override public int getItemCount() { return items.size(); }
            @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
                Holder holder = new Holder(LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false));
                holder.itemView.setOnClickListener(view -> {
                    int position = holder.getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION || !items.get(position).isEnabled()) return;
                    MenuItem item = items.get(position);
                    if (item.getGroupId() == GROUP_PROVIDER && !item.isChecked()) {
                        int previous = (int) list.getTag();
                        list.setTag(position);
                        for (MenuItem source : items) if (source.getGroupId() == GROUP_PROVIDER)
                            source.setCheckable(true).setChecked(source == item);
                        notifyItemChanged(previous);
                        notifyItemChanged(position);
                    }
                    action.onMenuItemClick(item);
                });
                return holder;
            }
            @Override public void onBindViewHolder(Holder holder, int position) {
                MenuItem item = items.get(position);
                holder.itemView.setTag(item);
                holder.title.setText(item.getTitle());
                holder.icon.setImageResource(item.isChecked() ? R.drawable.ic_action_play : 0);
                holder.title.setEnabled(item.isEnabled());
                holder.itemView.setEnabled(item.isEnabled());
                holder.itemView.setFocusable(item.isEnabled());
            }
        });
        focus(list, selected);
        return list;
    }

    public static void focusCurrent(RecyclerView list) {
        focus(list, (int) list.getTag());
    }

    private static void focus(RecyclerView list, int initialFocus) {
        int itemHeight = list.getResources().getDimensionPixelSize(R.dimen.navigation_item_height);
        int height = list.getResources().getDisplayMetrics().heightPixels;
        ((GridLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(initialFocus,
                Math.min(initialFocus * itemHeight, Math.max(0, height - itemHeight)));
        list.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(initialFocus);
                if (list.isAttachedToWindow() && holder == null) {
                    return;
                }
                list.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (list.isAttachedToWindow() && holder != null) holder.itemView.requestFocusFromTouch();
            }
        });
        list.requestLayout();
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView icon;

        Holder(View view) {
            super(view);
            float density = view.getResources().getDisplayMetrics().density;
            view.setPaddingRelative((int) (16 * density), view.getPaddingTop(), (int) (2 * density), view.getPaddingBottom());
            icon = view.findViewById(R.id.icon);
            ((ViewGroup.MarginLayoutParams) icon.getLayoutParams()).setMarginEnd((int) (2 * density));
            view.findViewById(R.id.number).setVisibility(View.GONE);
            view.setOnLongClickListener(ignored -> true);
            title = view.findViewById(R.id.title);
            view.setOnFocusChangeListener((ignored, focused) -> title.setSelected(focused));
        }
    }

    public static boolean isMenuKey(int key) {
        return key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_F10 || key == KeyEvent.KEYCODE_TV_CONTENTS_MENU
                || key == KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU || key == KeyEvent.KEYCODE_MEDIA_TOP_MENU;
    }
}
