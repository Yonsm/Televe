package internal.dialog;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import io.library.widget.PanelDialog;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;

import java.util.Locale;

import internal.playlist.Channel;
import io.library.exo.PlayerView;
import net.yonsm.televe.R;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class ChannelInfoDialog extends PanelDialog {
    private final PlayerView mPlayer;
    private final String mName;
    private final String mUrl;
    private final TextView mText;
    private final ScrollView mScroll;

    public ChannelInfoDialog(Context context, Channel channel, PlayerView player, int width) {
        super(context, new ScrollView(context), true, width);
        mPlayer = player;
        mName = channel.title;
        mUrl = channel.url;
        int padding = Math.round(getContext().getResources().getDisplayMetrics().density * 24);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        TextView title = new TextView(context);
        title.setText(Channel.isEmpty(mName) ? "" : mName);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(title);
        mText = new TextView(context);
        mText.setTextSize(14);
        content.addView(mText);
        mScroll = (ScrollView) mPanel;
        mScroll.setTranslationX(width);
        mScroll.setBackgroundColor(context.getColor(R.color.drawer_background));
        mScroll.setFocusableInTouchMode(true);
        mScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mScroll.addView(content);
        mScroll.setFillViewport(true);
        for (View view : new View[]{mScroll, content, title, mText}) {
            view.setOnClickListener(ignored -> dismiss());
            view.setOnLongClickListener(ignored -> {
                getContext().getSystemService(ClipboardManager.class).setPrimaryClip(
                        ClipData.newPlainText(mName, title.getText() + "\n" + mText.getText()));
                Toast.makeText(getContext(), R.string.channel_info_copied, Toast.LENGTH_SHORT).show();
                dismiss();
                return true;
            });
        }
        setOnShowListener(dialog -> {
            mScroll.animate().translationX(0).setDuration(70);
            mRefresh.run();
            mScroll.requestFocus();
        });
        setOnDismissListener(dialog -> mText.removeCallbacks(mRefresh));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        noteKeyEvent(event);
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) return true;
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_INFO) {
            if (event.getAction() == KeyEvent.ACTION_UP && isShortPress(event)) dismiss();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private final Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            if (!isShowing()) return;
            refreshInfo();
            mText.postDelayed(this, 1000);
        }
    };

    public boolean isForChannel(Channel channel) {
        return Channel.equals(mName, channel.title) && Channel.equals(mUrl, channel.url);
    }

    private void refreshInfo() {
        PlayerView.PlaybackInfo info = mPlayer.getPlaybackInfo();
        SpannableStringBuilder text = new SpannableStringBuilder();
        row(text, R.string.channel_url, mUrl);
        if (info.error != null) row(text, R.string.channel_error, info.error.getErrorCodeName());
        String buffer = info.bufferedDurationMs >= 0
                ? String.format(Locale.getDefault(), "%.1f s", info.bufferedDurationMs / 1000f) : null;
        SpannableStringBuilder videoText = new SpannableStringBuilder();
        Format video = info.video;
        VideoSize size = info.videoSize;
        int width = size != null && size.width > 0 ? size.width : video != null ? video.width : -1;
        int height = size != null && size.height > 0 ? size.height : video != null ? video.height : -1;
        boolean hasVideo = video != null || width > 0 && height > 0;
        row(videoText, R.string.channel_resolution, width > 0 && height > 0 ? width + " x " + height : null);
        row(videoText, R.string.channel_frame_rate, video != null && video.frameRate > 0
                ? String.format(Locale.getDefault(), "%.3f fps", video.frameRate) : null);
        if (video != null && video.colorInfo != null
                && video.colorInfo.isDataSpaceValid() && video.colorInfo.isBitdepthValid()) {
            String color = video.colorInfo.toString();
            if (color.startsWith("ColorInfo(") && color.endsWith(")"))
                color = color.substring("ColorInfo(".length(), color.length() - 1);
            row(videoText, R.string.channel_color, color);
        }
        format(videoText, video, info.videoDecoder);
        row(videoText, R.string.channel_decode_mode, info.videoHardwareAccelerated == null ? null
                : getContext().getString(info.videoHardwareAccelerated
                ? R.string.channel_decode_hardware : R.string.channel_decode_software));
        row(videoText, R.string.channel_decode_support, info.videoFormatSupported == null ? null
                : getContext().getString(info.videoFormatSupported
                ? R.string.channel_decode_supported : R.string.channel_decode_exceeded));
        if (info.videoDecoder != null) {
            row(videoText, R.string.channel_rendered_frames, info.renderedFrames);
            row(videoText, R.string.channel_dropped_frames, info.droppedFrames);
            row(videoText, R.string.channel_buffer, buffer);
        }
        section(text, R.string.channel_video, videoText);
        SpannableStringBuilder audioText = new SpannableStringBuilder();
        Format audio = info.audio;
        format(audioText, audio, info.audioDecoder);
        row(audioText, R.string.channel_sample_rate, audio != null && audio.sampleRate > 0 ? audio.sampleRate + " Hz" : null);
        row(audioText, R.string.channel_audio_channels, audio != null && audio.channelCount > 0 ? audio.channelCount : null);
        row(audioText, R.string.channel_language, audio != null && !"und".equals(audio.language) ? audio.language : null);
        if (!hasVideo && audioText.length() > 0) row(audioText, R.string.channel_buffer, buffer);
        section(text, R.string.channel_audio, audioText);
        if (!TextUtils.equals(text, mText.getText())) mText.setText(text);
    }

    private void section(SpannableStringBuilder text, int label, CharSequence details) {
        if (details.length() == 0) return;
        text.append('\n');
        int start = text.length();
        text.append(getContext().getString(label));
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append('\n').append(details);
    }

    private void format(SpannableStringBuilder text, Format format, String decoder) {
        row(text, R.string.channel_mime, format != null ? format.sampleMimeType : null);
        row(text, R.string.channel_codecs, format != null ? format.codecs : null);
        row(text, R.string.channel_bitrate, format != null ? bitrate(format.averageBitrate) : null);
        row(text, R.string.channel_peak_bitrate, format != null ? bitrate(format.peakBitrate) : null);
        row(text, R.string.channel_decoder, decoder);
    }

    private String bitrate(int bits) {
        return bits > 0 ? String.format(Locale.getDefault(), "%.1f kb/s", bits / 1000f) : null;
    }

    private void row(SpannableStringBuilder text, int label, Object value) {
        if (value == null || value.toString().isEmpty()) return;
        text.append(getContext().getString(label)).append(": ")
                .append(value.toString()).append('\n');
    }
}
