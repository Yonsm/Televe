package internal.playlist;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.library.net.Request;
import io.library.utils.DiskCache;

/** Persists lists locally until an explicit refresh is requested. */
public class PlaylistRepository implements AutoCloseable {
    public static final String DEFAULT_URL = "https://yonsm.github.io/IPTV/lists.m3u8";
    private static final int CACHE_VERSION = 1;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final File directory;
    private final Channel root = Channel.fromUrl(DEFAULT_URL);
    private volatile boolean closed;

    public interface Listener {
        void onResult(List<Channel> channels);
        void onError(Throwable error);
    }

    public PlaylistRepository(Context context) {
        directory = new File(context.getFilesDir(), "playlists");
        root.playlist = true;
    }

    public Channel getRoot() {
        return root;
    }

    public void request(Channel playlist, boolean refresh, Listener listener) {
        if (closed) return;
        executor.execute(() -> {
            try {
                AtomicFile cache = new AtomicFile(new File(directory,
                        Long.toHexString(DiskCache.hash64(playlist.url))));
                List<Channel> channels = refresh ? null : readCache(cache, playlist.url);
                if (channels == null) {
                    channels = new Request(playlist.url, "GET", false)
                            .execute(new PlaylistParser(playlist == root));
                    writeCache(cache, playlist.url, channels);
                }
                if (!closed) listener.onResult(channels);
            } catch (Exception error) {
                if (!closed) listener.onError(error);
            }
        });
    }

    private List<Channel> readCache(AtomicFile file, String url) {
        try (DataInputStream input = new DataInputStream(file.openRead())) {
            if (input.readInt() != CACHE_VERSION || !url.equals(input.readUTF())) return null;
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
            output.writeInt(CACHE_VERSION);
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
