package internal.playlist;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

import android.os.Parcel;
import android.os.Parcelable;

/** A live channel or a playlist containing channels. */
public class Channel implements Parcelable {
    public static final String EXTRA_CHANNEL = "net.yonsm.televe.CHANNEL";
    public static final int NO_ORDER = Integer.MAX_VALUE;

    public String id = "";
    public String title = "";
    public String url = "";
    public String thumbnail = "";
    public String baseUrl = "";
    public int order = NO_ORDER;
    public long number = -1;
    public boolean playlist;
    public boolean playlistSource;

    public Channel() {}

    public Channel(DataInput in) throws IOException {
        id = in.readUTF();
        title = in.readUTF();
        url = in.readUTF();
        thumbnail = in.readUTF();
        order = in.readInt();
        number = in.readLong();
        playlist = in.readBoolean();
        playlistSource = in.readBoolean();
    }

    public static Channel fromUrl(String url) {
        Channel channel = new Channel();
        channel.id = channel.url = url;
        channel.title = url;
        return channel;
    }

    public void writeTo(DataOutput out) throws IOException {
        writeUTF(id, out);
        writeUTF(title, out);
        writeUTF(url, out);
        writeUTF(thumbnail, out);
        out.writeInt(order);
        out.writeLong(number);
        out.writeBoolean(playlist);
        out.writeBoolean(playlistSource);
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
                && Objects.equals(url, channel.url) && (!playlist || Objects.equals(baseUrl, channel.baseUrl));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, url, playlist, playlist ? baseUrl : null);
    }

    public boolean equalsContent(Channel other) {
        return equals(other) && order == other.order && number == other.number && Objects.equals(title, other.title)
                && playlistSource == other.playlistSource && Objects.equals(thumbnail, other.thumbnail)
                && Objects.equals(baseUrl, other.baseUrl);
    }

    private static void writeUTF(String value, DataOutput out) throws IOException {
        out.writeUTF(value != null ? value : "");
    }

    private Channel(Parcel in) {
        id = Objects.toString(in.readString(), "");
        title = Objects.toString(in.readString(), "");
        url = Objects.toString(in.readString(), "");
        thumbnail = Objects.toString(in.readString(), "");
        order = in.readInt();
        number = in.readLong();
        playlist = in.readByte() != 0;
        playlistSource = in.readByte() != 0;
        baseUrl = Objects.toString(in.readString(), "");
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(id);
        out.writeString(title);
        out.writeString(url);
        out.writeString(thumbnail);
        out.writeInt(order);
        out.writeLong(number);
        out.writeByte(playlist ? (byte) 1 : 0);
        out.writeByte(playlistSource ? (byte) 1 : 0);
        out.writeString(baseUrl);
    }

    public static final Creator<Channel> CREATOR = new Creator<Channel>() {
        @Override
        public Channel createFromParcel(Parcel in) { return new Channel(in); }
        @Override
        public Channel[] newArray(int size) { return new Channel[size]; }
    };
}
