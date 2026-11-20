package internal.menu;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import internal.playlist.Channel;
import net.yonsm.televe.R;

/** The source column: menu actions plus an auto-grouped, collapsible source list. */
public final class ToolMenu {
    private ToolMenu() {}

    public interface ProviderListener {
        void onProviderSelected(Channel provider);
    }

    public static RecyclerView create(Context context, Menu menu, List<Channel> providers, Channel selected,
                                      MenuItem.OnMenuItemClickListener action, ProviderListener providerListener) {
        RecyclerView list = new RecyclerView(context);
        list.setBackgroundColor(context.getColor(R.color.drawer_background));
        list.setLayoutManager(new GridLayoutManager(context, 1));
        list.setHasFixedSize(true);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setAddDuration(0);
        animator.setRemoveDuration(0);
        animator.setMoveDuration(100);
        animator.setSupportsChangeAnimations(false);
        list.setItemAnimator(animator);
        MenuAdapter adapter = new MenuAdapter(context, menu, providers, selected, action, providerListener);
        list.setAdapter(adapter);
        adapter.attach(list);
        return list;
    }

    public static void focusCurrent(RecyclerView list) {
        MenuAdapter adapter = adapter(list);
        if (adapter != null) adapter.focusSelected();
    }

    /** Expands a group or selects a source; false for menu actions. */
    public static boolean activate(RecyclerView list, View itemView) {
        MenuAdapter adapter = adapter(list);
        Object tag = itemView == null ? null : itemView.getTag();
        if (adapter == null || !(tag instanceof Row)) return false;
        Row row = (Row) tag;
        if (row.group != null) adapter.expand(row.group);
        else if (row.provider != null) itemView.performClick();
        else return false;
        return true;
    }

    /** Collapses the focused group or source group; false when there is nothing to collapse. */
    public static boolean collapse(RecyclerView list) {
        MenuAdapter adapter = adapter(list);
        return adapter != null && adapter.collapseFocused();
    }

    private static MenuAdapter adapter(RecyclerView list) {
        return list != null && list.getAdapter() instanceof MenuAdapter ? (MenuAdapter) list.getAdapter() : null;
    }

    private static final class Row {
        final MenuItem item;
        final String group;
        final Channel provider;

        Row(MenuItem item, String group, Channel provider) {
            this.item = item;
            this.group = group;
            this.provider = provider;
        }
    }

    private static final class MenuAdapter extends RecyclerView.Adapter<Holder> {
        private final Context context;
        private final Menu menu;
        private final List<Channel> providers;
        private final MenuItem.OnMenuItemClickListener action;
        private final ProviderListener providerListener;
        private final List<Row> rows = new ArrayList<>();
        private final HashSet<String> expandedGroups = new HashSet<>();
        private Channel selected;
        private RecyclerView list;

        MenuAdapter(Context context, Menu menu, List<Channel> providers, Channel selected,
                    MenuItem.OnMenuItemClickListener action, ProviderListener providerListener) {
            this.context = context;
            this.menu = menu;
            this.providers = new ArrayList<>(providers);
            this.selected = selected;
            this.action = action;
            this.providerListener = providerListener;
            if (selected != null) {
                String group = groupOf(selected);
                if (!group.isEmpty()) expandedGroups.add(group);
            }
            rebuild();
        }

        void attach(RecyclerView list) {
            this.list = list;
            int position = selectedPosition();
            if (position < 0) return;
            ((GridLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
            focus(position);
        }

        @Override public int getItemCount() { return rows.size(); }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            Holder holder = new Holder(LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false));
            holder.itemView.setOnClickListener(view -> {
                if (view.getTag() instanceof Row) click((Row) view.getTag());
            });
            return holder;
        }

        private void click(Row row) {
            if (row.item != null) {
                if (row.item.isEnabled()) action.onMenuItemClick(row.item);
            } else if (row.group != null) {
                toggle(row.group);
            } else {
                selected = row.provider;
                notifyDataSetChanged();
                providerListener.onProviderSelected(row.provider);
            }
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            Row row = rows.get(position);
            float density = context.getResources().getDisplayMetrics().density;
            int start = (int) (16 * density);
            int end = (int) (2 * density);
            holder.itemView.setTag(row);
            holder.itemView.setEnabled(true);
            holder.itemView.setFocusable(true);
            holder.title.setEnabled(true);
            holder.icon.setRotation(0);
            int indent = start;
            if (row.item != null) {
                holder.title.setText(row.item.getTitle());
                holder.icon.setImageResource(row.item.isChecked() ? R.drawable.ic_action_play : 0);
                holder.title.setEnabled(row.item.isEnabled());
                holder.itemView.setEnabled(row.item.isEnabled());
                holder.itemView.setFocusable(row.item.isEnabled());
            } else if (row.group != null) {
                holder.title.setText(row.group);
                holder.icon.setImageResource(R.drawable.ic_panel_arrow);
                holder.icon.setRotation(expandedGroups.contains(row.group) ? 90f : 0f);
            } else {
                String group = groupOf(row.provider);
                holder.title.setText(Channel.trimPrefix(row.provider.title, group));
                holder.icon.setImageResource(row.provider.equals(selected) ? R.drawable.ic_action_play : 0);
                if (!group.isEmpty()) indent += (int) holder.title.getTextSize();
            }
            holder.itemView.setPaddingRelative(indent, holder.itemView.getPaddingTop(), end,
                    holder.itemView.getPaddingBottom());
        }

        private void rebuild() {
            rows.clear();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.isVisible()) rows.add(new Row(item, null, null));
            }
            HashSet<String> groups = new HashSet<>();
            for (Channel provider : providers) {
                String group = groupOf(provider);
                if (group.isEmpty()) {
                    rows.add(new Row(null, null, provider));
                    continue;
                }
                if (groups.add(group)) rows.add(new Row(null, group, null));
                if (expandedGroups.contains(group)) rows.add(new Row(null, null, provider));
            }
            notifyDataSetChanged();
        }

        private void toggle(String group) {
            if (expandedGroups.remove(group)) {
                setExpandedRows(group, false);
            } else {
                expandedGroups.add(group);
                setExpandedRows(group, true);
            }
            list.postDelayed(() -> revealGroup(group), 100);
        }

        private void expand(String group) {
            if (expandedGroups.add(group)) setExpandedRows(group, true);
            list.postDelayed(() -> {
                revealGroup(group);
                focus(indexOf(group, true));
            }, 100);
        }

        private void setExpandedRows(String group, boolean expanded) {
            int start = indexOf(group, false);
            if (start < 0) return;
            if (expanded) {
                int end = start + 1;
                for (Channel provider : providers) {
                    if (group.equals(groupOf(provider))) rows.add(end++, new Row(null, null, provider));
                }
                notifyItemRangeInserted(start + 1, end - start - 1);
            } else {
                int end = start + 1;
                while (end < rows.size() && rows.get(end).provider != null
                        && group.equals(groupOf(rows.get(end).provider))) end++;
                rows.subList(start + 1, end).clear();
                notifyItemRangeRemoved(start + 1, end - start - 1);
            }
            notifyItemChanged(start);
        }

        private void revealGroup(String group) {
            int position = indexOf(group, true);
            if (position < 0) return;
            View child = list.getLayoutManager().findViewByPosition(position);
            if (child != null && child.getTop() < list.getHeight() && child.getBottom() > 0) return;
            list.scrollToPosition(position);
        }

        void focusSelected() {
            if (selected != null) {
                String group = groupOf(selected);
                if (!group.isEmpty() && expandedGroups.add(group)) rebuild();
            }
            focus(selectedPosition());
        }

        boolean collapseFocused() {
            View focused = list == null ? null : list.findFocus();
            Object tag = focused == null ? null : focused.getTag();
            if (!(tag instanceof Row)) return false;
            Row row = (Row) tag;
            String group = row.group != null ? row.group
                    : row.provider == null ? "" : groupOf(row.provider);
            if (group.isEmpty() || !expandedGroups.contains(group)) return false;
            int start = indexOf(group, false);
            if (start < 0) return false;
            int end = start + 1;
            while (end < rows.size() && rows.get(end).provider != null
                    && group.equals(groupOf(rows.get(end).provider))) end++;
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(start);
            if (holder != null) holder.itemView.requestFocus();
            expandedGroups.remove(group);
            rows.subList(start + 1, end).clear();
            notifyItemRangeRemoved(start + 1, end - start - 1);
            notifyItemChanged(start);
            if (holder == null) focus(start);
            return true;
        }

        private String groupOf(Channel provider) {
            return Channel.isEmpty(provider.group) ? "" : provider.group;
        }

        private int selectedPosition() {
            int fallback = rows.isEmpty() ? -1 : 0;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.item != null && row.item.getItemId() == R.id.source) fallback = i;
                if (row.provider != null && selected != null && selected.equals(row.provider)) return i;
            }
            return fallback;
        }

        private int indexOf(String group, boolean provider) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (provider ? row.provider != null && group.equals(groupOf(row.provider))
                        : row.group != null && row.group.equals(group)) return i;
            }
            return -1;
        }

        private void focus(int position) {
            if (list == null || position < 0 || position >= rows.size()) return;
            list.post(() -> {
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    holder.itemView.requestFocus();
                    return;
                }
                list.scrollToPosition(position);
                list.post(() -> {
                    RecyclerView.ViewHolder target = list.findViewHolderForAdapterPosition(position);
                    if (target != null) target.itemView.requestFocus();
                });
            });
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView icon;

        Holder(View view) {
            super(view);
            view.findViewById(R.id.number).setVisibility(View.GONE);
            icon = view.findViewById(R.id.icon);
            ((ViewGroup.MarginLayoutParams) icon.getLayoutParams()).setMarginEnd(
                    (int) (2 * view.getResources().getDisplayMetrics().density));
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
