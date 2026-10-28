package internal.playlist;

import androidx.recyclerview.widget.SortedList;
import androidx.recyclerview.widget.RecyclerView;

public class ChannelListCallback extends SortedList.Callback<Channel> {
	private final RecyclerView.Adapter mAdapter;

	public ChannelListCallback(RecyclerView.Adapter adapter) {
		mAdapter = adapter;
	}

	@Override
	public int compare(Channel item1, Channel item2) {
		return new ChannelComparator(false).compare(item1, item2);
	}

	@Override
	public boolean areContentsTheSame(Channel oldItem, Channel newItem) {
		return oldItem.equalsContent(newItem);
	}

	@Override
	public boolean areItemsTheSame(Channel item1, Channel item2) {
		return item1.equals(item2);
	}

	@Override
	public void onChanged(int position, int count) {
		mAdapter.notifyItemRangeChanged(position, count);
	}

	@Override
	public void onInserted(int position, int count) {
		mAdapter.notifyItemRangeInserted(position, count);
	}

	@Override
	public void onRemoved(int position, int count) {
		mAdapter.notifyItemRangeRemoved(position, count);
	}

	@Override
	public void onMoved(int fromPosition, int toPosition) {
		mAdapter.notifyItemMoved(fromPosition, toPosition);
	}
}
