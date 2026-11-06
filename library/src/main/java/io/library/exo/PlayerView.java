package io.library.exo;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.media3.common.C;
import androidx.media3.common.FileTypes;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
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
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerView extends FrameLayout {
    private final SurfaceView mSurfaceView;
    private final TextureView mTextureView;

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
        default void onTracksChanged(Tracks tracks) {}
    }


    private boolean mPlayWhenReady = true;
    private int mVideoWidth;
    private int mVideoHeight;
    private float mAspectRatio = 0;
    private ExoPlayer mExoPlayer;
    private DefaultTrackSelector mTrackSelector;
    private MediaCodecInfo mVideoCodec;
    private String mVideoDecoder;
    private String mAudioDecoder;
    private Uri mCurrentUri;
    private MediaItem mCurrentMediaItem;
    private DataSource.Factory mCurrentDataSourceFactory;
    private boolean mRtspTsFallback;
    private PlaybackException mLastPlaybackError;
    private final CopyOnWriteArrayList<EventListener> mEventListeners = new CopyOnWriteArrayList<>();

    public PlayerView(Context context) {
        this(context, null);
    }

    public PlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // PixelCopy is unavailable on Android 6; retain texture snapshots there.
        mTextureView = Build.VERSION.SDK_INT < Build.VERSION_CODES.N ? new TextureView(context) : null;
        mSurfaceView = mTextureView == null ? new SurfaceView(context) : null;
        if (mSurfaceView != null) mSurfaceView.setZOrderMediaOverlay(true);
        addView(mSurfaceView != null ? mSurfaceView : mTextureView,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        createPlayer();
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

    public static class PlaybackInfo {
        public Format video;
        public Format audio;
        public String videoDecoder;
        public String audioDecoder;
        public Boolean videoHardwareAccelerated;
        public Boolean videoFormatSupported;
        public int renderedFrames;
        public int droppedFrames;
        public VideoSize videoSize;
        public long bufferedDurationMs;
        public boolean live;
        public PlaybackException error;
    }

    public PlaybackInfo getPlaybackInfo() {
        PlaybackInfo info = new PlaybackInfo();
        if (mExoPlayer != null) {
            info.video = mExoPlayer.getVideoFormat();
            info.audio = mExoPlayer.getAudioFormat();
            info.videoDecoder = info.video != null ? mVideoDecoder : null;
            info.audioDecoder = info.audio != null ? mAudioDecoder : null;
            if (info.videoDecoder != null && mVideoCodec == null && info.video.sampleMimeType != null) {
                try {
                    for (MediaCodecInfo codec : MediaCodecUtil.getDecoderInfos(info.video.sampleMimeType, false, false)) {
                        if (codec.name.equals(info.videoDecoder)) {
                            mVideoCodec = codec;
                            break;
                        }
                    }
                } catch (MediaCodecUtil.DecoderQueryException ignored) {
                    // Codec details remain unknown if the platform query fails.
                }
            }
            if (info.videoDecoder != null && mVideoCodec != null) {
                info.videoHardwareAccelerated = mVideoCodec.hardwareAccelerated;
                try {
                    info.videoFormatSupported = mVideoCodec.isFormatSupported(info.video);
                } catch (MediaCodecUtil.DecoderQueryException ignored) {
                    // Leave capability unknown when the platform query fails.
                }
            }
            DecoderCounters counters = mExoPlayer.getVideoDecoderCounters();
            if (counters != null) {
                counters.ensureUpdated();
                info.renderedFrames = counters.renderedOutputBufferCount;
                info.droppedFrames = counters.droppedBufferCount;
            }
            info.videoSize = mExoPlayer.getVideoSize();
            info.bufferedDurationMs = mExoPlayer.getTotalBufferedDuration();
            // Live sources can report an unbounded end timestamp rather than a real buffer.
            if (info.bufferedDurationMs < 0 || info.bufferedDurationMs >= Util.usToMs(Long.MAX_VALUE / 2))
                info.bufferedDurationMs = C.TIME_UNSET;
            info.live = mExoPlayer.isCurrentMediaItemLive();
            info.error = mExoPlayer.getPlayerError() != null ? mExoPlayer.getPlayerError() : mLastPlaybackError;
        }
        return info;
    }

    public void setAspectRatio(float aspectRatio) {
        if (mAspectRatio != aspectRatio) {
            mAspectRatio = aspectRatio;
            requestLayout();
        }
    }

    public void setUri(Context context, Uri uri, Map<String, String> headers, int type) {
        setUri(context, uri, headers, type, null, null);
    }

    public void setUri(Context context, Uri uri, Map<String, String> headers, int type, Uri subtitleUri, String subtitleType) {
        if (mExoPlayer != null) {
            if (!uri.equals(mCurrentUri)) {
                mLastPlaybackError = null;
                mRtspTsFallback = false;
            }
            mCurrentUri = uri;
            mVideoWidth = 0;
            mVideoHeight = 0;
            requestLayout();
            try {
                mCurrentMediaItem = buildMediaItem(uri, type, subtitleUri, subtitleType);
                mCurrentDataSourceFactory = buildSourceFactory(context, uri, headers, null);
                prepareMediaSource();
            } catch (RuntimeException error) {
                mExoPlayer.stop();
                mLastPlaybackError = new PlaybackException("Unsupported media source", error,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED);
                notifyError(mLastPlaybackError.errorCode, 0, mLastPlaybackError);
            }
        }
    }

    private void prepareMediaSource() {
        MediaSource source = "rtsp".equalsIgnoreCase(mCurrentUri.getScheme()) && !mRtspTsFallback
                ? new RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(mCurrentMediaItem)
                : new DefaultMediaSourceFactory(getContext()).setDataSourceFactory(mCurrentDataSourceFactory)
                .createMediaSource(mCurrentMediaItem);
        mExoPlayer.setMediaSource(source);
        mExoPlayer.prepare();
    }

    private boolean shouldTryRtspTs(PlaybackException error) {
        if (mCurrentUri == null || !"rtsp".equalsIgnoreCase(mCurrentUri.getScheme()) || mRtspTsFallback) return false;
        if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                || error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) return true;
        // Media3 filters unsupported SDP payloads such as IPTV's MPEG-TS-over-RTP.
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if ("No playable track.".equals(cause.getMessage())) return true;
        }
        return false;
    }

    public long getCurrentPosition() {
        if (mExoPlayer != null)
            return mExoPlayer.getCurrentPosition();
        return -1;
    }

    public long getDuration() {
        if (mExoPlayer != null)
            return mExoPlayer.getDuration();
        return -1;
    }

    public int getVideoWidth() {
        if (mVideoWidth > 0)
            return mVideoWidth;
        if (mExoPlayer != null) {
            VideoSize videoSize = mExoPlayer.getVideoSize();
            mVideoWidth = videoSize.width;
            mVideoHeight = videoSize.height;
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
        }
        return mVideoHeight;
    }

    public boolean isCurrentSeekable() {
        if (mExoPlayer != null)
            return mExoPlayer.isCurrentWindowSeekable() && !mExoPlayer.isCurrentWindowDynamic();
        return false;
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
        }
    }

    public void seekTo(long positionMs) {
        if (mExoPlayer != null)
            mExoPlayer.seekTo(positionMs);
    }

    public void setSpeed(float speed) {
        if (mExoPlayer != null)
            mExoPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1));
    }

    private void createPlayer() {
        mTrackSelector = new DefaultTrackSelector(getContext());
        mExoPlayer = createExoPlayer(getContext(), mTrackSelector);
        mExoPlayer.addListener(mExoEventListener);
        mExoPlayer.addListener(mExoVideoListener);
        mExoPlayer.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String name,
                                                   long initializedTimestampMs, long initializationDurationMs) {
                mVideoDecoder = name;
                mVideoCodec = null;
            }

            @Override
            public void onVideoDecoderReleased(EventTime eventTime, String name) {
                mVideoDecoder = null;
                mVideoCodec = null;
            }

            @Override
            public void onAudioDecoderInitialized(EventTime eventTime, String name,
                                                   long initializedTimestampMs, long initializationDurationMs) {
                mAudioDecoder = name;
            }

            @Override
            public void onAudioDecoderReleased(EventTime eventTime, String name) {
                mAudioDecoder = null;
            }
        });
        if (mSurfaceView != null) mExoPlayer.setVideoSurfaceView(mSurfaceView);
        else mExoPlayer.setVideoTextureView(mTextureView);
        mExoPlayer.setPlayWhenReady(mPlayWhenReady);
    }

    public void stop() {
        if (mExoPlayer != null)
            mExoPlayer.stop();
    }

    public void release() {
        if (mExoPlayer != null) {
            if (mSurfaceView != null) mExoPlayer.clearVideoSurfaceView(mSurfaceView);
            else mExoPlayer.clearVideoTextureView(mTextureView);
            mExoPlayer.removeListener(mExoEventListener);
            mExoPlayer.removeListener(mExoVideoListener);
            mExoPlayer.release();
            mExoPlayer = null;
            mTrackSelector = null;
        }
        mVideoDecoder = null;
        mVideoCodec = null;
        mAudioDecoder = null;
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
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public interface FrameCallback {
        void onFrame(Bitmap bitmap, Exception error);
    }

    public void captureFrame(FrameCallback callback) {
        Bitmap bitmap = null;
        try {
            if (mTextureView != null) {
                bitmap = mTextureView.getBitmap();
                callback.onFrame(bitmap, bitmap != null ? null : new IllegalStateException("No video frame"));
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                throw new IllegalStateException("Surface snapshots require Android 7.0");
            }
            bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Bitmap frame = bitmap;
            PixelCopy.request(mSurfaceView, frame, result -> {
                if (result == PixelCopy.SUCCESS) {
                    callback.onFrame(frame, null);
                } else {
                    frame.recycle();
                    callback.onFrame(null, new IllegalStateException("PixelCopy: " + result));
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (Exception error) {
            if (bitmap != null) bitmap.recycle();
            callback.onFrame(null, error);
        }
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
            if (mExoPlayer == null || mExoPlayer.getPlayerError() != error) return;
            if (shouldTryRtspTs(error)) {
                mRtspTsFallback = true;
                try {
                    prepareMediaSource();
                    return;
                } catch (RuntimeException fallbackError) {
                    error = new PlaybackException("Unsupported RTSP payload", fallbackError,
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED);
                }
            }
            mLastPlaybackError = error;
            notifyError(error.errorCode, 0, error);
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            for (EventListener listener : mEventListeners)
                listener.onTracksChanged(tracks);
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

    private static final int DEFAULT_MIN_BUFFER_MS = 10000;
    private static final int DEFAULT_MAX_BUFFER_MS = 20000;
    private static final int TIMEOUT_CONNECT = 15000;
    private static final int TIMEOUT_READ = 30000;

    public static ExoPlayer createExoPlayer(Context context, TrackSelector trackSelector) {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(DEFAULT_MIN_BUFFER_MS, DEFAULT_MAX_BUFFER_MS, 1000, 3000)
                .build();
        DefaultRenderersFactory rendererFactory = new DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
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
                                                        androidx.media3.datasource.TransferListener listener) {
        final String scheme = uri.getScheme();
        DataSource.Factory factory;
        if ("rtsp".equalsIgnoreCase(scheme) || "rtsps".equalsIgnoreCase(scheme)) {
            factory = new ClientDataSource.DefaultFactory(headers);
        } else if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            factory = new ClientDataSource.DefaultFactory(headers);
        } else {
            factory = buildDefaultFactory(context, headers, listener);
        }
        return factory;
    }

    public static MediaItem buildMediaItem(Uri uri, int type, Uri subtitleUri, String subtitleType) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);

        // 显式声明流类型：不以 .m3u8/.mpd 结尾的 HLS/DASH 地址，
        // DefaultMediaSourceFactory 按 URI 推断会误判为普通流导致起播失败
        if (type == C.CONTENT_TYPE_HLS) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (type == C.CONTENT_TYPE_DASH) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if ("rtsp".equalsIgnoreCase(uri.getScheme()) || "rtsps".equalsIgnoreCase(uri.getScheme())) {
            // The custom RTSP transport returns TS, which the progressive extractor consumes.
            builder.setMimeType(MimeTypes.VIDEO_MP2T);
        }

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

    public static int getFixedSourceType(Uri uri, boolean live) {
        int sourceType = Util.inferContentType(uri);
        if (sourceType == C.CONTENT_TYPE_OTHER && isWebUri(uri)
                && FileTypes.inferFileTypeFromUri(uri) == FileTypes.UNKNOWN) {
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
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
