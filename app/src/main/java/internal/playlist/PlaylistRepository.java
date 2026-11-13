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

import io.library.net.HttpClient;
import internal.utils.PrefUtils;

/** Caches each list until it is explicitly refreshed. */
public class PlaylistRepository implements AutoCloseable {
    public static final String DEFAULT_URL = "https://yonsm.github.io/IPTV/lists.m3u8";
    private static final String LOCAL_URL = "http://192.168.1.1:84/lists.m3u8";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final File directory;
    private volatile Channel root;
    private volatile boolean closed;
    public boolean fromNetwork;
    private volatile SharedPreferences initialPreferences;

    public interface Listener {
        void onResult(List<Channel> channels);
        void onError(Throwable error);
    }

    public PlaylistRepository(Context context) {
        SharedPreferences preferences = PrefUtils.getConfigPref(context);
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

    public boolean isInitializing() {
        return initialPreferences != null;
    }

    public synchronized void setRootUrl(String url) {
        initialPreferences = null;
        Channel channel = Channel.fromUrl(url);
        channel.playlist = true;
        root = channel;
    }

    public void request(Channel playlist, boolean providers, boolean refresh, Listener listener) {
        if (closed) return;
        final Channel requestedRoot = root;
        executor.execute(() -> {
            if (closed || root != requestedRoot) return;
            try {
                List<Channel> channels = providers && playlist == requestedRoot ? initializeSource(playlist) : null;
                fromNetwork = channels != null;
                if (closed || root != requestedRoot) return;
                final String url = playlist.url;
                AtomicFile cache = cacheFile(url, playlist.baseUrl);
                if (channels == null && !refresh) channels = readCache(cache, url, providers);
                if (channels == null || channels.isEmpty()) {
                    channels = HttpClient.get(url, new PlaylistParser(providers, playlist.baseUrl));
                    if (channels.isEmpty()) throw new IOException("Playlist is empty");
                    fromNetwork = true;
                    if (closed || root != requestedRoot) return;
                    writeCache(cache, url, channels);
                }
                if (!closed && root == requestedRoot) {
                    rememberInitialSource(playlist);
                    listener.onResult(channels);
                }
            } catch (Exception error) {
                if (closed || root != requestedRoot) return;
                List<Channel> cached = readCache(cacheFile(playlist.url, playlist.baseUrl), playlist.url, providers);
                if (cached != null && !cached.isEmpty()) {
                    rememberInitialSource(playlist);
                    fromNetwork = false;
                    listener.onResult(cached);
                } else listener.onError(error);
            }
        });
    }

    private List<Channel> initializeSource(Channel playlist) {
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
                if (closed || root != playlist) return null;
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

    private void writeCache(AtomicFile file, String url, List<Channel> channels) {
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
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            Log.w("PlaylistRepository", "Cannot cache playlist", error);
        }
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
