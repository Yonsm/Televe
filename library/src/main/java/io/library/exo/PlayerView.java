package io.library.exo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaDataSource;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = "PlayerView";

    public enum State {
        NONE, IDLE, BUFFERING, READY, ENDED
    }

    public static class Track {
        public enum Type {
            UNKNOWN, AUDIO, VIDEO, SUBTITLE, METADATA
        }

        public Type type;
        public String id;
        public String label;
        public String language;
        public String mimeType;
        public boolean selected;
        public TrackGroup group;
        private int index;
    }

    public interface EventListener {
        void onStateChanged(State state);
        void onFirstFrameRendered();
        void onError(int param1, int param2, Exception cause);

        default void onCues(List<Cue> cues) {}
    }


    private boolean mPlayWhenReady = true;
    private int mVideoWidth;
    private int mVideoHeight;
    private float mAspectRatio = 0;
    private ExoPlayer mExoPlayer;
    private MediaPlayer mMediaPlayer;
    private Surface mSurface;
    private DefaultTrackSelector mTrackSelector;
    private final CopyOnWriteArrayList<EventListener> mEventListeners = new CopyOnWriteArrayList<>();

    public PlayerView(Context context) {
        this(context, null);
    }

    public PlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSurfaceTextureListener(this);
        createPlayer(true);
    }

    public void addEventListener(EventListener listener) {
        mEventListeners.add(listener);
    }

    public void removeEventListener(EventListener listener) {
        mEventListeners.remove(listener);
    }

    public float getAspectRatio() {
        return mAspectRatio;
    }

    public void setAspectRatio(float aspectRatio) {
        if (mAspectRatio != aspectRatio) {
            mAspectRatio = aspectRatio;
            requestLayout();
        }
    }

    public void setExternalPlayer(boolean external) {
        release();
        createPlayer(external);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setDataSource(MediaDataSource dataSource) {
        if (mMediaPlayer != null) try {
            mMediaPlayer.setDataSource(dataSource);
            mMediaPlayer.prepareAsync();
            notifyEvent(State.BUFFERING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setUri(Context context, Uri uri, Map<String, String> headers, int type, boolean enableCache) {
        setUri(context, uri, headers, type, enableCache, null, null);
    }

    public void setUri(Context context, Uri uri, Map<String, String> headers, int type, boolean enableCache, Uri subtitleUri, String subtitleType) {
        if (mExoPlayer != null) {
            MediaItem mediaItem = buildMediaItem(context, uri, headers, type, enableCache, subtitleUri, subtitleType, null);
            // 把按 scheme 选出的 DataSource 工厂(rtsp/http/缓存)注入 MediaSource。
            // 否则 ExoPlayer 退回默认 DataSource 去读 自定义 scheme，无法播放。
            int actualType = type != -1 ? type : Util.inferContentType(uri);
            boolean useCache = enableCache && actualType == C.CONTENT_TYPE_OTHER;
            DataSource.Factory dataSourceFactory = buildSourceFactory(context, uri, headers, useCache, null);
            MediaSource mediaSource = new DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
                    .createMediaSource(mediaItem);
            mExoPlayer.setMediaSource(mediaSource);
            mExoPlayer.prepare();
        } else if (mMediaPlayer != null) try {
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(context, uri, headers);
            if (subtitleUri != null)
                mMediaPlayer.addTimedTextSource(context, subtitleUri, subtitleType);
            mMediaPlayer.prepareAsync();
            notifyEvent(State.BUFFERING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long getCurrentPosition() {
        if (mExoPlayer != null)
            return mExoPlayer.getCurrentPosition();
        else if (mMediaPlayer != null)
            return mMediaPlayer.getCurrentPosition();
        return -1;
    }

    public long getDuration() {
        if (mExoPlayer != null)
            return mExoPlayer.getDuration();
        else if (mMediaPlayer != null)
            return mMediaPlayer.getDuration();
        return -1;
    }

    public int getVideoWidth() {
        if (mVideoWidth > 0)
            return mVideoWidth;
        if (mExoPlayer != null) {
            VideoSize videoSize = mExoPlayer.getVideoSize();
            mVideoWidth = videoSize.width;
            mVideoHeight = videoSize.height;
        } else if (mMediaPlayer != null) {
            mVideoWidth = mMediaPlayer.getVideoWidth();
        }
        return mVideoWidth;
    }

    public int getVideoHeight() {
        if (mVideoHeight > 0)
            return mVideoHeight;
        if (mExoPlayer != null) {
            VideoSize videoSize = mExoPlayer.getVideoSize();
            mVideoWidth = videoSize.width;
            mVideoHeight = videoSize.height;
        } else if (mMediaPlayer != null) {
            mVideoHeight = mMediaPlayer.getVideoHeight();
        }
        return mVideoHeight;
    }

    public boolean isCurrentSeekable() {
        if (mExoPlayer != null)
            return mExoPlayer.isCurrentWindowSeekable() && !mExoPlayer.isCurrentWindowDynamic();
        else if (mMediaPlayer != null)
            return mMediaPlayer.getDuration() > 0;
        return false;
    }

    public boolean isExternalPlayer() {
        return mExoPlayer != null;
    }

    public boolean getPlayWhenReady() {
        if (mExoPlayer != null)
            mPlayWhenReady = mExoPlayer.getPlayWhenReady();
        return mPlayWhenReady;
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        mPlayWhenReady = playWhenReady;
        if (mExoPlayer != null)
            mExoPlayer.setPlayWhenReady(mPlayWhenReady);
        else if (mMediaPlayer != null) {
            if (mPlayWhenReady)
                mMediaPlayer.start();
            else
                mMediaPlayer.pause();
        }
    }

    public void getTracks(List<Track> tracks) {
        tracks.clear();
        if (mTrackSelector != null && mExoPlayer != null) {
            // Use Media3 Tracks API (1.4+)
            Tracks tracksObj = mExoPlayer.getCurrentTracks();
            if (tracksObj == null) return;
            for (Tracks.Group trackGroup : tracksObj.getGroups()) {
                for (int t = 0; t < trackGroup.length; t++) {
                    Format fmt = trackGroup.getTrackFormat(t);
                    Track track = new Track();
                    track.id = fmt.id;
                    track.language = fmt.language;
                    track.mimeType = fmt.sampleMimeType;
                    track.label = fmt.label;
                    track.index = t;
                    track.group = trackGroup.getMediaTrackGroup();
                    track.selected = trackGroup.isTrackSelected(t);
                    int trackType = trackGroup.getType();
                    switch (trackType) {
                        case C.TRACK_TYPE_AUDIO:
                            track.type = Track.Type.AUDIO;
                            break;
                        case C.TRACK_TYPE_VIDEO:
                            track.type = Track.Type.VIDEO;
                            break;
                        case C.TRACK_TYPE_TEXT:
                            track.type = Track.Type.SUBTITLE;
                            break;
                        case C.TRACK_TYPE_METADATA:
                            track.type = Track.Type.METADATA;
                            break;
                        default:
                            track.type = Track.Type.UNKNOWN;
                            break;
                    }
                    tracks.add(track);
                }
            }
        } else if (mMediaPlayer != null) {
            MediaPlayer.TrackInfo[] list = mMediaPlayer.getTrackInfo();
            if (list == null) return;
            int index = 0;
            for (MediaPlayer.TrackInfo info : list) {
                MediaFormat fmt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? info.getFormat() : null;
                Track track = new Track();
                track.language = info.getLanguage();
                track.mimeType = fmt != null ? fmt.getString(MediaFormat.KEY_LANGUAGE) : null;
                track.index = index++;
                int trackType = info.getTrackType();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    track.selected = track.index == mMediaPlayer.getSelectedTrack(trackType);
                switch (info.getTrackType()) {
                    case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO:
                        track.type = Track.Type.AUDIO;
                        break;
                    case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO:
                        track.type = Track.Type.VIDEO;
                        break;
                    case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE:
                    case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT:
                        track.type = Track.Type.SUBTITLE;
                        break;
                    case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_METADATA:
                        track.type = Track.Type.METADATA;
                        break;
                    case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_UNKNOWN:
                    default:
                        track.type = Track.Type.UNKNOWN;
                        break;
                }
                tracks.add(track);
            }
        }
    }

    public void selectTrack(Track track) {
        if (mExoPlayer != null && track.group != null) {
            // Media3 1.4+: 用 TrackSelectionOverride 强制选中指定轨道
            TrackSelectionOverride override = new TrackSelectionOverride(track.group, track.index);
            TrackSelectionParameters params = mExoPlayer.getTrackSelectionParameters()
                    .buildUpon()
                    .setTrackTypeDisabled(override.getType(), false)
                    .setOverrideForType(override)
                    .build();
            mExoPlayer.setTrackSelectionParameters(params);
        } else if (mMediaPlayer != null) {
            mMediaPlayer.selectTrack(track.index);
        }
    }

    public void seekTo(long positionMs) {
        if (mExoPlayer != null)
            mExoPlayer.seekTo(positionMs);
        else if (mMediaPlayer != null)
            mMediaPlayer.seekTo((int) positionMs);
    }

    public void setSpeed(float speed) {
        if (mExoPlayer != null)
            mExoPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1));
        else if (mMediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(speed));
    }

    private void createPlayer(boolean external) {
        if (mSurface == null) {
            SurfaceTexture surface = getSurfaceTexture();
            mSurface = surface != null ? new Surface(surface) : null;
        }
        if (external) {
            mTrackSelector = new DefaultTrackSelector(getContext());
            mExoPlayer = createExoPlayer(getContext(), mTrackSelector);
            mExoPlayer.addListener(mExoEventListener);
            mExoPlayer.addListener(mExoVideoListener);
            mExoPlayer.setVideoSurface(mSurface);
            mExoPlayer.setPlayWhenReady(mPlayWhenReady);
        } else {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setSurface(mSurface);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mVideoSizeChangedListener);
        }
    }

    public void stop() {
        if (mExoPlayer != null)
            mExoPlayer.stop();
        else if (mMediaPlayer != null)
            mMediaPlayer.stop();
    }

    public void release() {
        if (mExoPlayer != null) {
            mExoPlayer.clearVideoSurface();
            mExoPlayer.removeListener(mExoEventListener);
            mExoPlayer.removeListener(mExoVideoListener);
            mExoPlayer.release();
            mExoPlayer = null;
            mTrackSelector = null;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.setOnPreparedListener(null);
            mMediaPlayer.setOnVideoSizeChangedListener(null);
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        releaseSurface();
    }

    private void setSurface(SurfaceTexture surface) {
        releaseSurface();
        if (mExoPlayer != null)
            mExoPlayer.setVideoSurface(mSurface = new Surface(surface));
        else if (mMediaPlayer != null)
            mMediaPlayer.setSurface(mSurface = new Surface(surface));
    }

    private void releaseSurface() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int videoWidth = getVideoWidth();
        int videoHeight = getVideoHeight();
        if (mAspectRatio != 0)
            videoWidth = Math.round(videoHeight * mAspectRatio);

        int width = getDefaultSize(videoWidth, widthMeasureSpec);
        int height = getDefaultSize(videoHeight, heightMeasureSpec);

        if (videoWidth > 0 && videoHeight > 0) {
            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize;
                height = heightSpecSize;
                if (videoWidth * height  < width * videoHeight) {
                    width = height * videoWidth / videoHeight;
                } else if (videoWidth * height  > width * videoHeight) {
                    height = width * videoHeight / videoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize;
                height = width * videoHeight / videoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                height = heightSpecSize;
                width = height * videoWidth / videoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    width = widthSpecSize;
                }
            } else {
                width = videoWidth;
                height = videoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    height = heightSpecSize;
                    width = height * videoWidth / videoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    width = widthSpecSize;
                    height = width * videoHeight / videoWidth;
                }
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        setSurface(surface);
        Log.d(TAG, "onSurfaceTextureAvailable " + surface + ": " + width + "x" + height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged " + surface + ": " + width + "x" + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        release();
        Log.d(TAG, "onSurfaceTextureDestroyed " + surface);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void notifyEvent(State state) {
        for (EventListener listener : mEventListeners)
            listener.onStateChanged(state);
    }

    private void notifyFirstFrameRendered() {
        for (EventListener listener : mEventListeners)
            listener.onFirstFrameRendered();
    }

    private void notifyError(int param1, int param2, Exception cause) {
        for (EventListener listener : mEventListeners)
            listener.onError(param1, param2, cause);
    }

    private void notifyCues(List<Cue> cues) {
        for (EventListener listener : mEventListeners)
            listener.onCues(cues);
    }

    private final Player.Listener mExoEventListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            State state = State.IDLE;
            switch (playbackState) {
                case Player.STATE_IDLE: state = State.IDLE; break;
                case Player.STATE_BUFFERING: state = State.BUFFERING; break;
                case Player.STATE_READY: state = State.READY; break;
                case Player.STATE_ENDED: state = State.ENDED; break;
            }
            notifyEvent(state);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            notifyError(error.errorCode, 0, error);
        }

        @Override
        public void onCues(CueGroup cueGroup) {
            notifyCues(cueGroup.cues);
        }
    };

    private final Player.Listener mExoVideoListener = new Player.Listener() {
        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            int width = videoSize.width;
            int height = videoSize.height;
            float pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio;
            if (pixelWidthHeightRatio > 0)
                width = Math.round(width * pixelWidthHeightRatio);
            if (mVideoWidth != width || mVideoHeight != height) {
                mVideoWidth = width;
                mVideoHeight = height;
                requestLayout();
            }
        }

        @Override
        public void onRenderedFirstFrame() {
            requestLayout();
            notifyFirstFrameRendered();
        }
    };

    private final MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer player) {
            if (player == mMediaPlayer)
                notifyEvent(State.ENDED);
        }
    };

    public static final int MEDIA_ERROR_SYSTEM = Integer.MIN_VALUE;
    private final MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer player, int what, int extra) {
            if (player == mMediaPlayer && what > MediaPlayer.MEDIA_ERROR_UNKNOWN && extra != MEDIA_ERROR_SYSTEM)
                notifyError(what, extra, null);
            return true;
        }
    };

    private final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer player) {
            if (player == mMediaPlayer) {
                if (mPlayWhenReady)
                    player.start();
                notifyEvent(State.READY);
            }
        }
    };

    private final MediaPlayer.OnVideoSizeChangedListener mVideoSizeChangedListener = new MediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(MediaPlayer player, int width, int height) {
            if (player == mMediaPlayer)
                mExoVideoListener.onVideoSizeChanged(new VideoSize(width, height));
        }
    };

    private static final int DEFAULT_MIN_BUFFER_MS = 2000;
    private static final int DEFAULT_MAX_BUFFER_MS = 5000;
    private static final int TIMEOUT_CONNECT = 15000;
    private static final int TIMEOUT_READ = 30000;

    public static ExoPlayer createExoPlayer(Context context, TrackSelector trackSelector) {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(DEFAULT_MIN_BUFFER_MS, DEFAULT_MAX_BUFFER_MS, DEFAULT_MIN_BUFFER_MS / 2, DEFAULT_MIN_BUFFER_MS)
                .build();
        DefaultRenderersFactory rendererFactory = new DefaultRenderersFactory(context);
        return new ExoPlayer.Builder(context)
                .setRenderersFactory(rendererFactory)
                .setTrackSelector((DefaultTrackSelector) trackSelector)
                .setLoadControl(loadControl)
                .build();
    }

    public static DataSource.Factory buildDefaultFactory(Context context, Map<String, String> headers, androidx.media3.datasource.TransferListener listener) {
        String agent = Util.getUserAgent(context, context.getPackageName());
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(agent)
                .setConnectTimeoutMs(TIMEOUT_CONNECT)
                .setReadTimeoutMs(TIMEOUT_READ)
                .setAllowCrossProtocolRedirects(true);
        if (headers != null && !headers.isEmpty())
            httpFactory.setDefaultRequestProperties(headers);
        return new DefaultDataSource.Factory(context, httpFactory);
    }

    public static DataSource.Factory buildSourceFactory(Context context, Uri uri, Map<String, String> headers,
                                                        boolean enableCache, androidx.media3.datasource.TransferListener listener) {
        final String scheme = uri.getScheme();
        DataSource.Factory factory;
        if ("rtsp".equals(scheme) || "rtsps".equals(scheme)) {
            factory = new ClientDataSource.DefaultFactory(headers);
            enableCache = false;
        } else if ("http".equals(scheme) || "https".equals(scheme)) {
            factory = new ClientDataSource.DefaultFactory(headers);
        } else {
            factory = buildDefaultFactory(context, headers, listener);
            if (!isWebUri(uri))
                enableCache = false;
        }
        if (enableCache) {
            CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory();
            cacheFactory.setCache(getDefaultCache(context));
            cacheFactory.setUpstreamDataSourceFactory(factory);
            factory = cacheFactory;
        }
        return factory;
    }

    public static MediaItem buildMediaItem(Context context, Uri uri, Map<String, String> headers, int type,
                                           boolean enableCache, Uri subtitleUri, String subtitleType, androidx.media3.datasource.TransferListener listener) {
        if (type == -1)
            type = Util.inferContentType(uri);
        if (type != C.CONTENT_TYPE_OTHER)
            enableCache = false;

        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);

        if (subtitleUri != null) {
            String mime = subtitleType != null ? subtitleType : "text/x-srt";
            MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(mime)
                    .build();
            List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
            subs.add(subtitle);
            builder.setSubtitleConfigurations(subs);
        }

        return builder.build();
    }

    private static final String CACHE_DIR = "media_cache";
    private static final long CACHE_SIZE = 256 * 1024 * 1024;
    private static SimpleCache sVideoCache;

    public static File getCacheDir(Context context) {
        final File dir = context.getExternalCacheDir();
        return new File(dir != null ? dir : context.getCacheDir(), CACHE_DIR);
    }

    public static long getCacheSize() {
        SimpleCache cache = sVideoCache;
        return cache != null ? cache.getCacheSpace() : 0;
    }

    public static synchronized SimpleCache getDefaultCache(Context context) {
        if (sVideoCache == null)
            sVideoCache = new SimpleCache(getCacheDir(context), new LeastRecentlyUsedCacheEvictor(CACHE_SIZE));
        return sVideoCache;
    }

    public static int getFixedSourceType(Uri uri, boolean live) {
        int sourceType = Util.inferContentType(uri);
        if (sourceType == C.CONTENT_TYPE_OTHER && isWebUri(uri)) {
            final String path = uri.getPath();
            if (path != null && path.endsWith("m3u8"))
                sourceType = C.CONTENT_TYPE_HLS;
        }
        return sourceType;
    }

    public static String getSubtitleMimeType(String filename) {
        if (filename == null)
            return null;
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        if (ext.equalsIgnoreCase("vtt"))
            return "text/vtt";
        else if (ext.equalsIgnoreCase("ass") || ext.equalsIgnoreCase("ssa"))
            return "text/x-ssa";
        return "application/x-subrip";
    }

    public static boolean isWebUri(Uri uri) {
        final String scheme = uri != null ? uri.getScheme() : "";
        return "http".equals(scheme) || "https".equals(scheme);
    }
}
