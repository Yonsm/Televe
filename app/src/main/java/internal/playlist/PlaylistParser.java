package internal.playlist;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.library.net.Request;

/** Reads M3U playlists and comma-separated channel lists. */
public class PlaylistParser implements Request.Parser<List<Channel>> {
    private static final String LOGO_BASE = "https://yonsm.github.io/IPTV/LOGO/";
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)=(?:\"([^\"]*)\"|([^\\s,]+))");
    private final boolean providers;

    public PlaylistParser(boolean providers) {
        this.providers = providers;
    }

    @Override
    public List<Channel> onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                Channel.isEmpty(charset) ? "UTF-8" : charset));
        List<Channel> result = new ArrayList<>();
        String line;
        boolean m3u = false;
        Channel pending = null;
        while ((line = reader.readLine()) != null) {
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTM3U")) {
                m3u = true;
            } else if (line.startsWith("#EXTINF:")) {
                m3u = true;
                pending = parseInfo(line.substring(8));
            } else if (!line.startsWith("#")) {
                if (m3u) {
                    Channel channel = pending != null ? pending : new Channel();
                    channel.url = line;
                    if (Channel.isEmpty(channel.title)) channel.title = line;
                    if (Channel.isEmpty(channel.id)) channel.id = channel.title;
                    if (Channel.isEmpty(channel.thumbnail)) {
                        channel.thumbnail = LOGO_BASE + channel.id.replace("-", "").replace("HD", "").trim() + ".jpg";
                    }
                    channel.playlist = providers;
                    result.add(channel);
                    pending = null;
                } else {
                    int comma = line.indexOf(',');
                    if (comma <= 0 || comma == line.length() - 1) continue;
                    Channel channel = new Channel();
                    channel.title = line.substring(0, comma).trim();
                    channel.url = line.substring(comma + 1).trim();
                    if ("#genre#".equals(channel.url)) continue;
                    int dash = channel.title.indexOf('-');
                    if (dash > 0) {
                        int order = parseOrder(channel.title.substring(0, dash));
                        if (order != Channel.NO_ORDER) {
                            channel.order = order;
                            channel.title = channel.title.substring(dash + 1);
                        }
                    }
                    channel.id = channel.title;
                    channel.playlist = providers;
                    result.add(channel);
                }
            }
        }
        return result;
    }

    private Channel parseInfo(String info) {
        Channel channel = new Channel();
        Map<String, String> attributes = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(info);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        }
        // Legacy lists place attributes between two commas; standard M3U places them before the first.
        int comma = -1;
        boolean quoted = false;
        for (int i = 0; i < info.length(); i++) {
            char c = info.charAt(i);
            if (c == '"') quoted = !quoted;
            if (c == ',' && !quoted) { comma = i; break; }
        }
        if (comma >= 0) {
            String title = info.substring(comma + 1).trim();
            if (ATTRIBUTE.matcher(title).lookingAt()) {
                int last = title.lastIndexOf(',');
                if (last >= 0) title = title.substring(last + 1).trim();
            }
            channel.title = title;
        }
        String first = info.split("[\\s,]", 2)[0];
        channel.order = parseOrder(first);
        channel.id = attributes.get("tvg-name");
        if (Channel.isEmpty(channel.id)) channel.id = attributes.get("server-id");
        if (Channel.isEmpty(channel.id)) channel.id = attributes.get("tvg-id");
        channel.thumbnail = attributes.get("tvg-logo");
        return channel;
    }

    private static int parseOrder(String value) {
        try {
            int order = Integer.parseInt(value);
            return order >= 0 ? order : Channel.NO_ORDER;
        } catch (NumberFormatException ignored) {
            return Channel.NO_ORDER;
        }
    }
}
