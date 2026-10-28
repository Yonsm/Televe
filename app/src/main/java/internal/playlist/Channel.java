package internal.playlist;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

import io.library.utils.ParcelableObject;

/** A live channel or a playlist containing channels. */
public class Channel extends ParcelableObject<Channel> {
    public static final String EXTRA_CHANNEL = "net.yonsm.televe.CHANNEL";
    public static final int NO_ORDER = Integer.MAX_VALUE;

    public String id = "";
    public String title = "";
    public String url = "";
    public String thumbnail = "";
    public int order = NO_ORDER;
    public boolean playlist;

    public Channel() {}

    public Channel(DataInput in) throws IOException {
        id = in.readUTF();
        title = in.readUTF();
        url = in.readUTF();
        thumbnail = in.readUTF();
        order = in.readInt();
        playlist = in.readBoolean();
    }

    public static Channel fromUrl(String url) {
        Channel channel = new Channel();
        channel.id = channel.url = url;
        channel.title = url;
        return channel;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        writeUTF(id, out);
        writeUTF(title, out);
        writeUTF(url, out);
        writeUTF(thumbnail, out);
        out.writeInt(order);
        out.writeBoolean(playlist);
    }

    public static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static boolean equals(Object left, Object right) {
        return Objects.equals(left, right);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Channel)) return false;
        Channel channel = (Channel) other;
        return playlist == channel.playlist && Objects.equals(id, channel.id)
                && Objects.equals(url, channel.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, url, playlist);
    }

    public boolean equalsContent(Channel other) {
        return equals(other) && order == other.order && Objects.equals(title, other.title)
                && Objects.equals(thumbnail, other.thumbnail);
    }

    public static final Creator<Channel> CREATOR = new ParcelableCreator<>(Channel.class);
}
