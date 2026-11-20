package internal.playlist;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.library.net.HttpClient;

/** Reads M3U channel lists and TXT source directories. */
public class PlaylistParser implements HttpClient.Parser<List<Channel>> {
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)=(?:\"([^\"]*)\"|([^\\s,]+))");
    private static final int MAX_LOAD_DEPTH = 2;
    private final boolean providers;
    private final String defaultBaseUrl;
    private final int depth;

    public PlaylistParser(boolean providers) {
        this(providers, null, 0);
    }

    public PlaylistParser(boolean providers, String baseUrl) {
        this(providers, baseUrl, 0);
    }

    private PlaylistParser(boolean providers, String baseUrl, int depth) {
        this.providers = providers;
        this.defaultBaseUrl = baseUrl;
        this.depth = depth;
    }

    public List<Channel> onParse(String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
        return parse(stream, charset);
    }

    private List<Channel> parse(InputStream stream, String charset) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                Channel.isEmpty(charset) ? "UTF-8" : charset));
        List<Channel> result = new ArrayList<>();
        String baseUrl = defaultBaseUrl;
        String genre = "";
        String line;
        boolean hasM3uHeader = false;
        Channel pending = null;
        while ((line = reader.readLine()) != null) {
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("<")) throw new IOException("Expected a playlist, received markup");
            if (line.startsWith("#EXTM3U")) {
                hasM3uHeader = true;
            } else if (line.startsWith("#BASE:")) {
                baseUrl = line.substring(6).trim();
            } else if (line.startsWith("#LOAD:")) {
                if (depth < MAX_LOAD_DEPTH) {
                    try {
                        List<Channel> included = HttpClient.get(line.substring(6).trim(),
                                new PlaylistParser(providers, null, depth + 1));
                        result.addAll(included);
                    } catch (Exception ignored) {
                    }
                }
            } else if (line.startsWith("#EXTINF:")) {
                pending = parseInfo(line.substring(8));
            } else if (!line.startsWith("#")) {
                int comma = line.indexOf(',');
                int scheme = line.indexOf("://");
                int query = line.indexOf('?');
                if (pending != null || comma < 0 || hasM3uHeader &&
                        ((scheme >= 0 && scheme < comma) || (query >= 0 && query < comma))) {
                    if (pending == null && !hasM3uHeader) continue;
                    Channel channel = pending != null ? pending : new Channel();
                    channel.url = line;
                    if (Channel.isEmpty(channel.title)) channel.title = line;
                    if (Channel.isEmpty(channel.id)) channel.id = channel.title;
                    channel.playlist = providers;
                    channel.baseUrl = Channel.isEmpty(baseUrl) ? "" : baseUrl;
                    channel.group = genre;
                    result.add(channel);
                    pending = null;
                } else {
                    if (comma < 0 || comma == line.length() - 1) continue;
                    Channel channel = new Channel();
                    channel.title = line.substring(0, comma).trim();
                    channel.url = line.substring(comma + 1).trim();
                    if ("#genre#".equals(channel.url)) {
                        genre = channel.title;
                        continue;
                    }
                    if (channel.title.isEmpty() || channel.url.isEmpty()) continue;
                    int dash = channel.title.indexOf('-');
                    if (dash > 0) {
                        int order = parseOrder(channel.title.substring(0, dash));
                        if (order != Channel.NO_ORDER) {
                            channel.order = order;
                            channel.title = channel.title.substring(dash + 1).trim();
                        }
                    }
                    channel.id = channel.title;
                    channel.playlist = providers;
                    channel.baseUrl = Channel.isEmpty(baseUrl) ? "" : baseUrl;
                    channel.group = genre;
                    result.add(channel);
                }
            }
        }
        long previousNumber = 0;
        for (Channel channel : result) {
            boolean explicitNumber = channel.order >= 0 && channel.order <= 999
                    && channel.order >= previousNumber;
            channel.number = explicitNumber ? channel.order : previousNumber + 1;
            previousNumber = channel.number;
        }
        return result;
    }

    @Override
    public List<Channel> onParse(String url, String mimeType, String charset, InputStream stream, boolean fromCache) throws Exception {
        List<Channel> channels = parse(stream, charset);
        for (int i = channels.size() - 1; i >= 0; i--) {
            try {
                Channel channel = channels.get(i);
                String baseUrl = channel.baseUrl;
                URI base = URI.create(url);
                String document = base.toString().split("[?#]", 2)[0];
                boolean template = !Channel.isEmpty(baseUrl) && baseUrl.contains("{}");
                if (!Channel.isEmpty(baseUrl)) {
                    String value = template ? baseUrl.replace("{}", "%7B%7D")
                            : baseUrl.endsWith("/") || baseUrl.startsWith("?") ? baseUrl : baseUrl + "/";
                    baseUrl = (value.startsWith("?") ? document + value : base.resolve(value).toString()).replace("%7B%7D", "{}");
                    if (providers) channel.baseUrl = baseUrl;
                    if (!template) {
                        base = URI.create(baseUrl);
                        document = base.toString().split("[?#]", 2)[0];
                    }
                }
                if (template && !channel.url.contains("://") && !channel.url.startsWith("//"))
                    channel.url = baseUrl.replace("{}", channel.url);
                channel.url = channel.url.startsWith("?") ? document + channel.url : base.resolve(channel.url).toString();
                if (!providers) channel.baseUrl = "";
            } catch (IllegalArgumentException invalid) {
                channels.remove(i);
            }
        }
        classifySources(url, channels, providers);
        return channels;
    }

    /** A .lst document, or a lists.txt named one, is a source directory. */
    static void classifySources(String url, List<Channel> channels, boolean providers) {
        if (!providers) return;
        String document = url.split("[?#]", 2)[0].toLowerCase(java.util.Locale.US);
        if (!document.endsWith(".lst") && !document.endsWith("/lists.txt")) return;
        for (Channel channel : channels) channel.playlistSource = true;
    }

    private Channel parseInfo(String info) {
        Channel channel = new Channel();
        Map<String, String> attributes = new HashMap<>();
        int comma = info.lastIndexOf(',');
        Matcher matcher = ATTRIBUTE.matcher(comma < 0 ? info : info.substring(0, comma));
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        }
        if (comma >= 0) channel.title = info.substring(comma + 1).trim();
        String first = info.trim().split("[\\s,]", 2)[0];
        channel.order = parseOrder(first);
        channel.id = attributes.get("tvg-name");
        if (Channel.isEmpty(channel.id)) channel.id = attributes.get("tvg-id");
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
