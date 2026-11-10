package io.library.exo;

import android.content.Context;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.DecoderCounters;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.media3.common.C;
import androidx.media3.common.FileTypes;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
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
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = "PlayerView";

    public enum State {
        NONE, IDLE, BUFFERING, READY, ENDED
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
    private Surface mSurface;
    private DefaultTrackSelector mTrackSelector;
    private MediaCodecInfo mVideoCodec;
    private PlaybackException mLastPlaybackError;
    private String mVideoDecoder;
    private String mAudioDecoder;
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

    public void setUri(Context context, Uri uri, Map<String, String> headers, int type) {
        setUri(context, uri, headers, type, null, null);
    }

    public void setUri(Context context, Uri uri, Map<String, String> headers, int type, Uri subtitleUri, String subtitleType) {
        if (mExoPlayer != null) {
            mLastPlaybackError = null;
            mVideoWidth = 0;
            mVideoHeight = 0;
            requestLayout();
            MediaItem mediaItem = buildMediaItem(uri, type, subtitleUri, subtitleType);
            // 把按 scheme 选出的 DataSource 工厂(rtsp/http)注入 MediaSource。
            // 否则 ExoPlayer 退回默认 DataSource 去读 自定义 scheme，无法播放。
            DataSource.Factory dataSourceFactory = buildSourceFactory(context, uri, headers, null);
            MediaSource mediaSource = new DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
                    .createMediaSource(mediaItem);
            mExoPlayer.setMediaSource(mediaSource);
            mExoPlayer.prepare();
        }
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
        if (mSurface == null) {
            SurfaceTexture surface = getSurfaceTexture();
            mSurface = surface != null ? new Surface(surface) : null;
        }
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
            public void onAudioDecoderInitialized(EventTime eventTime, String name,
                                                   long initializedTimestampMs, long initializationDurationMs) {
                mAudioDecoder = name;
            }

            @Override
            public void onVideoDecoderReleased(EventTime eventTime, String name) {
                mVideoDecoder = null;
                mVideoCodec = null;
            }

            @Override
            public void onAudioDecoderReleased(EventTime eventTime, String name) {
                mAudioDecoder = null;
            }
        });
        mExoPlayer.setVideoSurface(mSurface);
        mExoPlayer.setPlayWhenReady(mPlayWhenReady);
    }

    public void stop() {
        if (mExoPlayer != null)
            mExoPlayer.stop();
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
        releaseSurface();
    }

    private void setSurface(SurfaceTexture surface) {
        releaseSurface();
        if (mExoPlayer != null)
            mExoPlayer.setVideoSurface(mSurface = new Surface(surface));
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
        if (mExoPlayer != null)
            mExoPlayer.clearVideoSurface();
        releaseSurface();
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
            mLastPlaybackError = error;
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
        if ("rtsp".equals(scheme) || "rtsps".equals(scheme)) {
            factory = new ClientDataSource.DefaultFactory(headers);
        } else if ("http".equals(scheme) || "https".equals(scheme)) {
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
        }
        // RTSP 不设置：由 ClientDataSource 把 RTP over TCP 还原成 TS 交给 Progressive 解析

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

    public static boolean isWebUri(Uri uri) {
        final String scheme = uri != null ? uri.getScheme() : "";
        return "http".equals(scheme) || "https".equals(scheme);
    }
}
