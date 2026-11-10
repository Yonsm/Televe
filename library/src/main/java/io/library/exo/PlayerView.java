package io.library.exo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.media3.common.C;
import androidx.media3.common.FileTypes;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
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

import java.util.concurrent.CopyOnWriteArrayList;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerView extends FrameLayout {
    private final SurfaceView mSurfaceView;
    private final View mShutterView;

    public enum State {
        NONE, IDLE, BUFFERING, READY, ENDED
    }

    public interface EventListener {
        void onStateChanged(State state);
        void onFirstFrameRendered();
        void onError(int param1, int param2, Exception cause);

        default void onTracksChanged(Tracks tracks) {}
    }


    private int mVideoWidth;
    private int mVideoHeight;
    private float mAspectRatio = 0;
    private ExoPlayer mExoPlayer;
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
        mSurfaceView = new SurfaceView(context);
        mSurfaceView.setZOrderMediaOverlay(true);
        addView(mSurfaceView,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mShutterView = new View(context);
        mShutterView.setBackgroundColor(Color.BLACK);
        addView(mShutterView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        createPlayer();
    }

    public void addEventListener(EventListener listener) {
        mEventListeners.add(listener);
    }

    public void removeEventListener(EventListener listener) {
        mEventListeners.remove(listener);
    }

    public static boolean exceedsDecoderCapabilities(Exception error) {
        return error instanceof androidx.media3.exoplayer.ExoPlaybackException
                && ((androidx.media3.exoplayer.ExoPlaybackException) error).type
                == androidx.media3.exoplayer.ExoPlaybackException.TYPE_RENDERER
                && ((androidx.media3.exoplayer.ExoPlaybackException) error).rendererFormatSupport
                == C.FORMAT_EXCEEDS_CAPABILITIES;
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

    public void setUri(Uri uri, int type) {
        mShutterView.setVisibility(VISIBLE);
        if (mExoPlayer != null) {
            if (!uri.equals(mCurrentUri)) {
                mLastPlaybackError = null;
                mRtspTsFallback = false;
            }
            mCurrentUri = uri;
            try {
                mCurrentMediaItem = buildMediaItem(uri, type);
                mCurrentDataSourceFactory = buildSourceFactory(uri);
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
            if ("No playable track.".equals(cause.getMessage())
                    || "Unsupported static paylod type 33".equals(cause.getMessage())) return true;
        }
        return false;
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

    private void createPlayer() {
        mExoPlayer = createExoPlayer(getContext());
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
        mExoPlayer.setVideoSurfaceView(mSurfaceView);
        mExoPlayer.setPlayWhenReady(true);
    }

    public void stop() {
        mShutterView.setVisibility(VISIBLE);
        if (mExoPlayer != null)
            mExoPlayer.stop();
    }

    public void release() {
        if (mExoPlayer != null) {
            mExoPlayer.clearVideoSurfaceView(mSurfaceView);
            mExoPlayer.removeListener(mExoEventListener);
            mExoPlayer.removeListener(mExoVideoListener);
            mExoPlayer.release();
            mExoPlayer = null;
        }
        mVideoDecoder = null;
        mVideoCodec = null;
        mAudioDecoder = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int videoWidth = getVideoWidth();
        int videoHeight = getVideoHeight();
        if (mAspectRatio != 0) {
            // Exact reference dimensions keep both preset ratios free of rounding borders.
            videoHeight = 1080;
            videoWidth = Math.round(videoHeight * mAspectRatio);
        }

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

    };

    private final Player.Listener mExoVideoListener = new Player.Listener() {
        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            int width = videoSize.width;
            int height = videoSize.height;
            // Keep the current frame geometry until the next stream has a valid size.
            if (width <= 0 || height <= 0) return;
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
            mShutterView.setVisibility(GONE);
            requestLayout();
            notifyFirstFrameRendered();
        }
    };

    private static final int DEFAULT_MIN_BUFFER_MS = 10000;
    private static final int DEFAULT_MAX_BUFFER_MS = 20000;
    private static final int TIMEOUT_CONNECT = 15000;
    private static final int TIMEOUT_READ = 30000;

    private static ExoPlayer createExoPlayer(Context context) {
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(DEFAULT_MIN_BUFFER_MS, DEFAULT_MAX_BUFFER_MS, 1000, 3000)
                .build();
        DefaultRenderersFactory rendererFactory = new DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
        return new ExoPlayer.Builder(context)
                .setRenderersFactory(rendererFactory)
                .setTrackSelector(new DefaultTrackSelector(context))
                .setLoadControl(loadControl)
                .build();
    }

    private DataSource.Factory buildSourceFactory(Uri uri) {
        String scheme = uri.getScheme();
        if ("rtsp".equalsIgnoreCase(scheme) || "rtsps".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
            return new ClientDataSource.DefaultFactory(null);
        return new DefaultDataSource.Factory(getContext(), new DefaultHttpDataSource.Factory()
                .setUserAgent(Util.getUserAgent(getContext(), getContext().getPackageName()))
                .setConnectTimeoutMs(TIMEOUT_CONNECT)
                .setReadTimeoutMs(TIMEOUT_READ)
                .setAllowCrossProtocolRedirects(true));
    }

    private static MediaItem buildMediaItem(Uri uri, int type) {
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

        return builder.build();
    }

    public static int getFixedSourceType(Uri uri) {
        int sourceType = Util.inferContentType(uri);
        if (sourceType == C.CONTENT_TYPE_OTHER && isWebUri(uri)
                && FileTypes.inferFileTypeFromUri(uri) == FileTypes.UNKNOWN) {
            sourceType = C.CONTENT_TYPE_HLS;
        }
        return sourceType;
    }

    public static boolean isWebUri(Uri uri) {
        final String scheme = uri != null ? uri.getScheme() : "";
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
