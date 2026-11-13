package internal.playlist;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.library.net.HttpClient;
import internal.utils.PrefUtils;

/** Caches each list until it is explicitly refreshed. */
public class PlaylistRepository implements AutoCloseable {
    public static final String DEFAULT_URL = "https://yonsm.github.io/IPTV/lists.m3u8";
    private static final String LOCAL_URL = "http://192.168.1.1:84/lists.m3u";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final File directory;
    private final AtomicInteger requestVersion = new AtomicInteger();
    private volatile Channel root;
    private volatile boolean closed;
    private volatile boolean refreshChannels;
    public boolean fromNetwork;
    private volatile SharedPreferences initialPreferences;
    private final SharedPreferences preferences;

    public interface Listener {
        void onResult(List<Channel> channels);
        void onError(Throwable error);
    }

    public PlaylistRepository(Context context) {
        preferences = PrefUtils.getConfigPref(context);
        directory = new File(context.getFilesDir(), "playlists");
        if (!directory.isDirectory()) directory.mkdirs();
        setRootUrl(preferences.getString(PrefUtils.CONFIG_PLAYLIST_URL, DEFAULT_URL));
        if (!preferences.contains(PrefUtils.CONFIG_PLAYLIST_URL)) {
            initialPreferences = preferences;
        }
    }

    public Channel getRoot() {
        return root;
    }

    public boolean hasLoadedSource() {
        return preferences.getBoolean(PrefUtils.CONFIG_PLAYLIST_LOADED, false);
    }

    public synchronized void setRootUrl(String url) {
        initialPreferences = null;
        Channel channel = Channel.fromUrl(url);
        channel.playlist = true;
        root = channel;
    }

    public void cancelRefresh() {
        requestVersion.incrementAndGet();
    }

    public void request(Channel playlist, boolean providers, boolean refresh, boolean sourceRefresh, Listener listener) {
        if (closed) return;
        final Channel requestedRoot = root;
        int version = requestVersion.incrementAndGet();
        executor.execute(() -> {
            if (closed || root != requestedRoot || version != requestVersion.get()) return;
            try {
                List<Channel> channels = providers && playlist == requestedRoot && !sourceRefresh ? initializeSource(playlist, version) : null;
                fromNetwork = channels != null;
                if (closed || root != requestedRoot || version != requestVersion.get()) return;
                final String url = playlist.url;
                AtomicFile cache = cacheFile(url, playlist.baseUrl);
                if (channels == null && !refresh && (providers || !refreshChannels)) channels = readCache(cache, url, providers);
                if (channels == null || channels.isEmpty()) {
                    channels = HttpClient.get(url, new PlaylistParser(providers, playlist.baseUrl));
                    if (channels.isEmpty()) throw new IOException("Playlist is empty");
                    fromNetwork = true;
                    if (closed || root != requestedRoot || version != requestVersion.get()) return;
                    boolean sourceDirectory = sourceRefresh;
                    if (sourceDirectory) for (Channel channel : channels) sourceDirectory &= channel.playlistSource;
                    boolean clearChannels = !providers && refreshChannels;
                    if (!writeCache(cache, url, channels)) throw new IOException("Cannot cache playlist");
                    if (sourceRefresh && !sourceDirectory || clearChannels) {
                        File[] files = directory.listFiles();
                        File rootCache = cacheFile(requestedRoot.url, requestedRoot.baseUrl).getBaseFile();
                        if (files != null) for (File file : files)
                            if (!file.equals(cache.getBaseFile()) && (!clearChannels || !file.equals(rootCache)) && !file.delete())
                                Log.w("PlaylistRepository", "Cannot delete cache: " + file);
                    }
                    if (sourceRefresh || clearChannels) refreshChannels = sourceDirectory;
                }
                if (!closed && root == requestedRoot && version == requestVersion.get()) {
                    if (providers && playlist == requestedRoot) preferences.edit().putBoolean(PrefUtils.CONFIG_PLAYLIST_LOADED, true).apply();
                    rememberInitialSource(playlist);
                    listener.onResult(channels);
                }
            } catch (Exception error) {
                if (closed || root != requestedRoot || version != requestVersion.get()) return;
                List<Channel> cached = sourceRefresh || providers && playlist != requestedRoot ? null
                        : readCache(cacheFile(playlist.url, playlist.baseUrl), playlist.url, providers);
                if (cached != null && !cached.isEmpty()) {
                    if (providers && playlist == requestedRoot) preferences.edit().putBoolean(PrefUtils.CONFIG_PLAYLIST_LOADED, true).apply();
                    rememberInitialSource(playlist);
                    fromNetwork = false;
                    listener.onResult(cached);
                } else listener.onError(error);
            }
        });
    }

    private List<Channel> initializeSource(Channel playlist, int version) {
        SharedPreferences preferences = initialPreferences;
        if (preferences == null) return null;
        HttpURLConnection connection = null;
        try {
            connection = HttpClient.makeConnection(LOCAL_URL);
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            List<Channel> channels;
            try (InputStream stream = connection.getInputStream()) {
                channels = new PlaylistParser(true).onParse(LOCAL_URL, connection.getContentType(),
                        HttpClient.parseCharset(connection.getContentType()), stream, false);
            }
            if (channels.isEmpty()) return null;
            synchronized (this) {
                // A source entered manually while probing always takes precedence.
                if (closed || root != playlist || version != requestVersion.get()) return null;
                playlist.id = playlist.title = playlist.url = LOCAL_URL;
                rememberInitialSource(playlist);
                writeCache(cacheFile(LOCAL_URL, null), LOCAL_URL, channels);
                return channels;
            }
        } catch (Exception error) {
            Log.d("PlaylistRepository", "Local source unavailable; using default", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private synchronized void rememberInitialSource(Channel playlist) {
        if (initialPreferences == null || playlist != root) return;
        initialPreferences.edit().putString(PrefUtils.CONFIG_PLAYLIST_URL, playlist.url).apply();
        initialPreferences = null;
    }

    private AtomicFile cacheFile(String url, String baseUrl) {
        String key = Channel.isEmpty(baseUrl) ? url : url + "\n" + baseUrl;
        return new AtomicFile(new File(directory, UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString()));
    }

    private List<Channel> readCache(AtomicFile file, String url, boolean providers) {
        try (DataInputStream input = new DataInputStream(file.openRead())) {
            if (!url.equals(input.readUTF())) return null;
            int count = input.readInt();
            if (count < 0 || count > 100000) return null;
            List<Channel> channels = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Channel channel = new Channel(input);
                channel.playlist = providers;
                channels.add(channel);
            }
            if (input.available() > 0) {
                for (Channel channel : channels) channel.baseUrl = input.readUTF();
            }
            PlaylistParser.classifySources(url, channels, providers);
            return channels;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean writeCache(AtomicFile file, String url, List<Channel> channels) {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream output = new DataOutputStream(stream);
            output.writeUTF(url);
            output.writeInt(channels.size());
            for (Channel channel : channels) channel.writeTo(output);
            for (Channel channel : channels) output.writeUTF(channel.baseUrl == null ? "" : channel.baseUrl);
            output.flush();
            file.finishWrite(stream);
            return true;
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            Log.w("PlaylistRepository", "Cannot cache playlist", error);
            return false;
        }
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
