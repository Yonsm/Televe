package internal.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;

import java.util.Locale;

import internal.playlist.Channel;
import io.library.exo.PlayerView;
import net.yonsm.televe.R;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class ChannelInfoDialog extends AlertDialog {
    private final PlayerView mPlayer;
    private final String mName;
    private final String mUrl;
    private final TextView mText;
    private final ScrollView mScroll;

    public ChannelInfoDialog(Context context, Channel channel, PlayerView player) {
        super(context);
        mPlayer = player;
        mName = channel.title;
        mUrl = channel.url;
        setTitle(R.string.channel_info);
        int padding = Math.round(getContext().getResources().getDisplayMetrics().density * 24);
        mText = new TextView(getContext());
        mText.setPadding(padding, padding / 2, padding, padding / 2);
        mText.setTextSize(14);
        mText.setTextIsSelectable(true);
        mScroll = new ScrollView(getContext());
        mScroll.setFocusableInTouchMode(true);
        mScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mScroll.addView(mText);
        setView(mScroll);
        setCanceledOnTouchOutside(true);
        setOnShowListener(dialog -> {
            mRefresh.run();
            mScroll.requestFocus();
        });
        setOnDismissListener(dialog -> mText.removeCallbacks(mRefresh));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if ((key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)
                && !mText.hasSelection()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                mScroll.requestFocus();
                mScroll.arrowScroll(key == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
            mScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        return super.dispatchTouchEvent(event);
    }

    private final Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            if (!mText.hasSelection()) {
                String text = buildInfo();
                if (!text.contentEquals(mText.getText())) mText.setText(text);
            }
            mText.postDelayed(this, 5000);
        }
    };

    public boolean isForChannel(Channel channel) {
        return Channel.equals(mName, channel.title) && Channel.equals(mUrl, channel.url);
    }

    private String buildInfo() {
        PlayerView.PlaybackInfo info = mPlayer.getPlaybackInfo();
        StringBuilder text = new StringBuilder();
        row(text, R.string.channel_name, mName);
        row(text, R.string.channel_url, mUrl);
        row(text, R.string.channel_buffer, String.format(Locale.getDefault(), "%.1f s", info.bufferedDurationMs / 1000f));
        if (info.live) text.append(getContext().getString(R.string.channel_live)).append("\n\n");
        if (info.error != null) row(text, R.string.channel_error, info.error.getErrorCodeName());
        text.append('\n').append(getContext().getString(R.string.channel_video)).append('\n');
        Format video = info.video;
        VideoSize size = info.videoSize;
        int width = size != null && size.width > 0 ? size.width : video != null ? video.width : -1;
        int height = size != null && size.height > 0 ? size.height : video != null ? video.height : -1;
        row(text, R.string.channel_resolution, width > 0 && height > 0 ? width + " x " + height : null);
        row(text, R.string.channel_frame_rate, video != null && video.frameRate > 0
                ? String.format(Locale.getDefault(), "%.3f fps", video.frameRate) : null);
        format(text, video, info.videoDecoder);
        if (video != null && video.colorInfo != null) row(text, R.string.channel_color, video.colorInfo.toString());
        text.append('\n').append(getContext().getString(R.string.channel_audio)).append('\n');
        Format audio = info.audio;
        format(text, audio, info.audioDecoder);
        row(text, R.string.channel_sample_rate, audio != null && audio.sampleRate > 0 ? audio.sampleRate + " Hz" : null);
        row(text, R.string.channel_audio_channels, audio != null && audio.channelCount > 0 ? audio.channelCount : null);
        row(text, R.string.channel_language, audio != null ? audio.language : null);
        return text.toString();
    }

    private void format(StringBuilder text, Format format, String decoder) {
        row(text, R.string.channel_mime, format != null ? format.sampleMimeType : null);
        row(text, R.string.channel_codecs, format != null ? format.codecs : null);
        row(text, R.string.channel_bitrate, format != null ? bitrate(format.averageBitrate) : null);
        row(text, R.string.channel_peak_bitrate, format != null ? bitrate(format.peakBitrate) : null);
        row(text, R.string.channel_decoder, decoder);
    }

    private String bitrate(int bits) {
        return bits > 0 ? String.format(Locale.getDefault(), "%.1f kb/s", bits / 1000f) : null;
    }

    private void row(StringBuilder text, int label, Object value) {
        text.append(getContext().getString(label)).append(": ")
                .append(value == null || value.toString().isEmpty()
                        ? getContext().getString(R.string.channel_unknown) : value).append("\n\n");
    }
}
