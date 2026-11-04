package internal.playlist;

import java.util.Comparator;
import io.library.comparator.AlphanumComparator;

public class ChannelComparator implements Comparator<Channel> {
    protected static final AlphanumComparator ALPHANUM_COMPARATOR = new AlphanumComparator();
    private final boolean reversed;

    public ChannelComparator(boolean reversed) {
        this.reversed = reversed;
    }

    @Override
    public int compare(Channel first, Channel second) {
        long result = Long.compare(first.number >= 0 ? first.number : first.order,
                second.number >= 0 ? second.number : second.order);
        if (result == 0) result = onCompare(first, second);
        if (result == 0) result = first.id.compareTo(second.id);
        if (result == 0) result = first.url.compareTo(second.url);
        return (reversed ? -1 : 1) * Long.signum(result);
    }

    protected long onCompare(Channel first, Channel second) {
        return ALPHANUM_COMPARATOR.compare(first.title, second.title);
    }
}
