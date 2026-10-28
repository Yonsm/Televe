package internal.playlist;

import androidx.recyclerview.widget.SortedList;

public class ChannelList extends SortedList<Channel> {
    private int currentItem = INVALID_POSITION;

    public ChannelList(Callback<Channel> callback) {
        super(Channel.class, callback);
    }

    public int getCurrentItem() {
        return currentItem;
    }

    public void setCurrentItem(int item) {
        currentItem = item;
    }

    public int setCurrentItem(Channel channel, int suggestedIndex) {
        if (!(suggestedIndex >= 0 && suggestedIndex < size() && get(suggestedIndex).equals(channel))) {
            suggestedIndex = indexOf(channel);
        }
        setCurrentItem(suggestedIndex);
        return suggestedIndex;
    }
}
