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
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.library.net.Request;
import io.library.net.HttpClient;
import io.library.utils.DiskCache;
import internal.utils.PrefUtils;

/** Persists lists locally until an explicit refresh is requested. */
public class PlaylistRepository implements AutoCloseable {
    public static final String DEFAULT_URL = "https://yonsm.github.io/IPTV/lists.m3u8";
    private static final String LOCAL_URL = "http://192.168.1.1:84/lists.m3u8";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final File directory;
    private volatile Channel root;
    private volatile boolean closed;
    private SharedPreferences initialPreferences;

    public interface Listener {
        void onResult(List<Channel> channels);
        void onError(Throwable error);
    }

    public PlaylistRepository(Context context) {
        this(context, PrefUtils.getConfigPref(context).getString(
                PrefUtils.CONFIG_PLAYLIST_URL, DEFAULT_URL));
        SharedPreferences preferences = PrefUtils.getConfigPref(context);
        String[] cachedLists = directory.list();
        if (preferences.getAll().isEmpty()
                && PrefUtils.getStatePref(context).getAll().isEmpty()
                && cachedLists != null && cachedLists.length == 0) {
            initialPreferences = preferences;
        }
        // The selected source also records initialization, including an unsuccessful probe.
        if (!preferences.contains(PrefUtils.CONFIG_PLAYLIST_URL)) {
            preferences.edit().putString(PrefUtils.CONFIG_PLAYLIST_URL, root.url).apply();
        }
    }

    public PlaylistRepository(Context context, String rootUrl) {
        directory = new File(context.getFilesDir(), "playlists");
        // AtomicFile 不会创建父目录。
        if (!directory.isDirectory()) directory.mkdirs();
        setRootUrl(rootUrl);
    }

    public Channel getRoot() {
        return root;
    }

    public synchronized void setRootUrl(String url) {
        Channel channel = Channel.fromUrl(url);
        channel.playlist = true;
        root = channel;
    }

    public void request(Channel playlist, boolean refresh, Listener listener) {
        if (closed) return;
        final boolean rootPlaylist = playlist == root;
        executor.execute(() -> {
            try {
                List<Channel> channels = rootPlaylist ? initializeSource(playlist) : null;
                final String url = playlist.url;
                AtomicFile cache = new AtomicFile(new File(directory,
                        Long.toHexString(DiskCache.hash64(url))));
                if (channels == null && !refresh) channels = readCache(cache, url);
                if (channels == null) {
                    channels = new Request(url, "GET", false).execute(
                            new PlaylistParser(rootPlaylist), null, false, true);
                    writeCache(cache, url, channels);
                }
                if (!closed) listener.onResult(channels);
            } catch (Exception error) {
                if (!closed) listener.onError(error);
            }
        });
    }

    private List<Channel> initializeSource(Channel playlist) {
        SharedPreferences preferences = initialPreferences;
        if (preferences == null) return null;
        initialPreferences = null;
        HttpURLConnection connection = null;
        try {
            connection = HttpClient.makeConnection(LOCAL_URL, "GET", null);
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            List<Channel> channels;
            try (InputStream stream = connection.getInputStream()) {
                channels = new PlaylistParser(true).onParse(connection.getContentType(),
                        HttpClient.parseCharset(connection.getContentType()), stream, false);
            }
            if (channels.isEmpty()) return null;
            synchronized (this) {
                // A source entered manually while probing always takes precedence.
                if (closed || root != playlist) return null;
                playlist.id = playlist.title = playlist.url = LOCAL_URL;
                preferences.edit().putString(PrefUtils.CONFIG_PLAYLIST_URL, LOCAL_URL).apply();
                writeCache(new AtomicFile(new File(directory,
                        Long.toHexString(DiskCache.hash64(LOCAL_URL)))), LOCAL_URL, channels);
                return channels;
            }
        } catch (Exception error) {
            Log.d("PlaylistRepository", "Local source unavailable; using default", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private List<Channel> readCache(AtomicFile file, String url) {
        try (DataInputStream input = new DataInputStream(file.openRead())) {
            if (!url.equals(input.readUTF())) return null;
            int count = input.readInt();
            if (count < 0 || count > 100000) return null;
            List<Channel> channels = new ArrayList<>(count);
            for (int i = 0; i < count; i++) channels.add(new Channel(input));
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
