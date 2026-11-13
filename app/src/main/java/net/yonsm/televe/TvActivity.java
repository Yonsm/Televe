package net.yonsm.televe;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.media3.common.C;
import androidx.media3.common.Tracks;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import io.library.widget.PanelDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import internal.playlist.Channel;
import internal.playlist.ChannelComparator;
import internal.playlist.ChannelList;
import internal.playlist.ChannelListCallback;
import internal.playlist.PlaylistRepository;
import internal.dialog.AboutDialog;
import internal.dialog.ChannelInfoDialog;
import internal.menu.ToolMenu;
import internal.utils.PrefUtils;
import internal.utils.SnapshotStore;
import android.Manifest;
import android.content.pm.PackageManager;
import io.library.exo.PlayerView;

public class TvActivity extends ComponentActivity {
    private static final String TAG = "TvActivity";

    private static final int REPLAY_DELAY = 1000;
    private static final int PLAYBACK_FAILURE_THRESHOLD = 3;
    private static final int STABLE_PLAYBACK_MS = 30000;

    private float mDensity;
    private float mAspectRatio = 16 / 9f;
    private boolean mLoadingPlaylist;
    private Channel mChannel;
    private String mRestoredChannelUrl;
    private String mPlaybackUrl;
    private boolean mOpenedExternally;
    private Drawable mPlayIcon;
    private RecyclerView mChannelMenu;
    private FrameLayout mRootView;
    private PanelDialog mChannelPanel;
    private RecyclerView mSourceMenu;
    private MenuItem mSnapshotItem;
    private int mPanelMode = 2; // 0: menu, 1: menu and channels, 2: channels.
    private LinearLayout mChannelContent;
    private int mPanelWidth;
    private int mInfoPanelWidth;
    private FrameLayout mPanelContainer;
    private ImageView mPanelHint;
    private ImageView mPinIcon;
    private View mLoadingView;
    private View mBannerContainer;
    private boolean mPanelPinned, mPanelClosing;
    private PlayerView mPlayerView;
    private TextView mChannelBanner;
    private CharSequence mPendingChannelBanner;
    private View mRadioIcon;
    private boolean mAudioOnly;
    private int mProviderRequest;
    private int mPendingChannelFocus = -1;
    private boolean mRevealCurrentChannel;
    private int mPlaybackFailures;
    private ChannelInfoDialog mChannelInfoDialog;
    private AlertDialog mSourceEditor;
    private String mChannelNumber = "";
    private int mNumberConfirmKey = KeyEvent.KEYCODE_UNKNOWN;
    private LayoutInflater mLayoutInflater;
    private PlaylistRepository mIptvSource;
    private final ArrayList<Channel> mProviderList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_tv);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });

        Resources res = getResources();
        mDensity = res.getDisplayMetrics().density;
        mLayoutInflater = getLayoutInflater();

        mPlayIcon = getDrawable(R.drawable.ic_action_play);

        mRootView = findViewById(R.id.player_container);
        PanelDialog.immersive(getWindow());
        mRootView.setOnClickListener(mClickListener);
        mRootView.setOnTouchListener(mTouchListener);

        mChannelMenu = mRootView.findViewById(R.id.channel_menu);
        mChannelMenu.setAdapter(mChannelAdapter);
        mChannelMenu.setHasFixedSize(true);

        mChannelMenu.setFocusableInTouchMode(true);
        mChannelMenu.setItemAnimator(null);
        mChannelMenu.setLayoutManager(new GridLayoutManager(this, 2));
        mChannelMenu.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) cancelChannelFocus();
            return false;
        });
        mRootView.removeView(mChannelMenu);
        mChannelMenu.setBackgroundColor(getColor(R.color.drawer_background));
        mChannelContent = new LinearLayout(this);
        mChannelContent.addView(mChannelMenu);
        mPanelContainer = new FrameLayout(this);
        mPanelContainer.addView(mChannelContent);
        mPanelHint = new ImageView(this);
        mPanelHint.setImageResource(R.drawable.ic_panel_arrow);
        mPanelHint.setFocusable(false);
        mPanelHint.setScaleType(ImageView.ScaleType.CENTER);
        mPanelHint.setOnClickListener(view -> showPanel(1));
        FrameLayout.LayoutParams hint = new FrameLayout.LayoutParams((int) (16 * mDensity),
                getResources().getDimensionPixelSize(R.dimen.navigation_item_height), Gravity.START | Gravity.TOP);
        mPanelContainer.addView(mPanelHint, hint);
        mPinIcon = new ImageView(this);
        mPinIcon.setImageResource(R.drawable.ic_panel_menu);
        mPinIcon.setFocusable(false);
        mPinIcon.setScaleType(ImageView.ScaleType.CENTER);
        mPinIcon.setOnClickListener(view -> togglePanelPin());
        FrameLayout.LayoutParams pin = new FrameLayout.LayoutParams((int) (16 * mDensity),
                getResources().getDimensionPixelSize(R.dimen.navigation_item_height), Gravity.TOP | Gravity.END);
        pin.setMarginEnd((int) (3 * mDensity));
        mPanelContainer.addView(mPinIcon, pin);
        mChannelPanel = new PanelDialog(this, mPanelContainer, false, 0) {
            @Override public void dismiss() { if (!isShowing() || mPanelClosing) return;
                mPanelClosing = true;
                mPanelContainer.animate().translationX(-mPanelContainer.getWidth()).setDuration(110).withEndAction(() -> { mPanelClosing = false; super.dismiss(); });
            }
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                PanelDialog.noteKeyEvent(event);
                return handleMenuKey(event) || ((mSourceMenu == null || !mSourceMenu.hasFocus()) && handleChannelNumberKey(event)) || handleDrawerNavigation(event)
                        || super.dispatchKeyEvent(event);
            }
        };
        mChannelPanel.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        mChannelPanel.setCanceledOnTouchOutside(false);
        mChannelPanel.setOnOppositeSwipe(() -> {
            if (mSourceMenu == null || mPanelMode == 0) showProviderMenu();
        });
        mChannelPanel.setOnShowListener(dialog -> {
            mLastBackPressedTime = 0;
            cancelChannelNumber();
            mPendingChannelBanner = null;
            hideChannelBanner();
            mChannelMenu.post(() -> {
                if (mSourceMenu == null) focusVisibleChannel();
                else positionCurrentChannel(fixCurrentIndexIfNecessary());
            });
        });
        mChannelPanel.setOnDismissListener(dialog -> {
            mPanelPinned = false;
            mPinIcon.setImageResource(R.drawable.ic_panel_menu);
            mRevealCurrentChannel = false;
            cancelChannelFocus();
            mChannelMenu.stopScroll();
            mPlayerView.requestFocus();
            CharSequence pending = mPendingChannelBanner;
            mPendingChannelBanner = null;
            if (mResumed && pending != null) showChannelBanner(pending);
        });
        mIptvSource = new PlaylistRepository(this);

        mBannerContainer = mRootView.findViewById(R.id.banner_container);
        mLoadingView = mRootView.findViewById(R.id.loading);
        mChannelBanner = mRootView.findViewById(R.id.channel_banner);
        mRadioIcon = mRootView.findViewById(R.id.radio_icon);

        mPlayerView = mRootView.findViewById(R.id.player);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPlayerView.setDefaultFocusHighlightEnabled(false);
            mRootView.setDefaultFocusHighlightEnabled(false);
        }
        mPlayerView.addEventListener(mEventListener);
        mPlayerView.setKeepScreenOn(true);
        mPlayerView.setOnClickListener(mClickListener);
        View.OnLongClickListener showInfo = v -> {
            mLongPressed = true;
            showChannelInfo();
            return true;
        };
        mPlayerView.setOnLongClickListener(showInfo);
        mRootView.setOnLongClickListener(showInfo);
        mPlayerView.setOnTouchListener(mTouchListener);
        mPlayerView.requestFocus();

        if (mPlayerView.getAspectRatio() != mAspectRatio) {
            mPlayerView.setAspectRatio(mAspectRatio);
        }

        updatePanelWidth(res.getDisplayMetrics().widthPixels);
        mRootView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) updatePanelWidth(right - left);
        });

        Intent intent = getIntent();
        Channel channel = intent.getParcelableExtra(Channel.EXTRA_CHANNEL);
        if (channel == null) {
            Uri uri = intent.getData();
            if (uri != null) {
                channel = Channel.fromUrl(uri.toString());
                String title = intent.getStringExtra(Intent.EXTRA_TITLE);
                if (!Channel.isEmpty(title)) channel.title = title;
            }
        }
        mOpenedExternally = channel != null;
        if (channel == null) mRestoredChannelUrl = loadStatusString(PrefUtils.STATE_CHANNEL_URL, null);
        mChannel = channel;
        setupProviderMenu(false);
    }

    private void updatePanelWidth(int availableWidth) {
        mPanelWidth = Math.min(availableWidth, getResources().getDimensionPixelSize(R.dimen.panel_width));
        mInfoPanelWidth = availableWidth < 600 * mDensity ? availableWidth : (int) (378 * mDensity);
        layoutChannelPanel(availableWidth);
        if (mChannelInfoDialog != null) mChannelInfoDialog.setPanelWidth(mInfoPanelWidth);
    }

    @Override
    protected void onDestroy() {
        cancelChannelFocus();
        if (mPlayerView != null) {
            mPlayerView.removeCallbacks(mResetPlaybackFailuresAction);
            mPlayerView.setKeepScreenOn(false);
            mPlayerView.removeCallbacks(mReplayAction);
            mPlayerView.removeEventListener(mEventListener);
            mPlayerView.release();
        }
        if (mIptvSource != null) mIptvSource.close();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mResumed = false;
        updateLoading();
        mChannelPanel.dismiss();
        cancelChannelFocus();
        mChannelMenu.stopScroll();
        cancelChannelNumber();
        mNumberConfirmKey = KeyEvent.KEYCODE_UNKNOWN;
        if (mChannelInfoDialog != null) {
            mChannelInfoDialog.dismiss();
            mChannelInfoDialog = null;
        }
        hideChannelBanner();
        mPlayerView.removeCallbacks(mReplayAction);
        mPlayerView.removeCallbacks(mResetPlaybackFailuresAction);
        mPlayerView.stop();
        mPlaybackUrl = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        mResumed = true;
        updateLoading();
        resetPlaybackFailures();

        if (mChannel != null && (mOpenedExternally
                || (mCurrentFolder != null && fixCurrentIndexIfNecessary() >= 0)))
            playChannel(mChannel);
    }

    private long mLastBackPressedTime;
    private void handleBackPressed() {
        if (!mChannelNumber.isEmpty()) {
            cancelChannelNumber();
            return;
        }
        long now = System.currentTimeMillis();

        if (mChannelPanel.isShowing()) {
            mChannelPanel.dismiss();
            mLastBackPressedTime = 0;
            return;
        }

        if (now > mLastBackPressedTime + 3000) {
            mLastBackPressedTime = now;
            Toast.makeText(this, R.string.press_again_exit, Toast.LENGTH_SHORT).show();
            return;
        }

        exitApp();
    }

    private void exitApp() {
        finishAndRemoveTask();
    }

    private boolean mResumed;

    private boolean handleChannelNumberKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int digit = -1;
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
            digit = keyCode - KeyEvent.KEYCODE_0;
        else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
            digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        if (mResumed && digit >= 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                appendChannelNumber(digit);
            return true;
        }
        boolean confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_SPACE;
        if (confirm && (!mChannelNumber.isEmpty() || mNumberConfirmKey == keyCode)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mNumberConfirmKey = keyCode;
                mChannelBanner.removeCallbacks(mCommitChannelNumberAction);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                mNumberConfirmKey = KeyEvent.KEYCODE_UNKNOWN;
                if (event.isCanceled()) cancelChannelNumber();
                else commitChannelNumber();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && !mChannelNumber.isEmpty()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mChannelNumber = mChannelNumber.substring(0, mChannelNumber.length() - 1);
                if (mChannelNumber.isEmpty()) {
                    cancelChannelNumber();
                    hideChannelBanner();
                } else previewChannelNumber();
            }
            return true;
        }
        return false;
    }

    private void appendChannelNumber(int digit) {
        if (mLoadingPlaylist && mProviderChanged) return;
        if (mChannelList.size() == 0) {
            showChannelBanner(getString(R.string.channel_list_empty));
            return;
        }
        if (mChannelPanel.isShowing()) mChannelPanel.dismiss();
        if (mChannelNumber.equals("0")) mChannelNumber = "";
        // Keep one excess digit so an invalid number cannot select a valid prefix.
        if (mChannelNumber.length() <= 3) mChannelNumber += digit;
        mLastBackPressedTime = 0;
        previewChannelNumber();
    }

    private int pendingChannelIndex() {
        if (mChannelNumber.isEmpty() || mChannelNumber.length() > 3) return -1;
        int number = Integer.parseInt(mChannelNumber);
        for (int i = 0; i < mChannelList.size(); i++) {
            if (mChannelList.get(i).number == number) return i;
        }
        return -1;
    }

    private void previewChannelNumber() {
        mChannelBanner.removeCallbacks(mCommitChannelNumberAction);
        int index = pendingChannelIndex();
        showChannelBanner(index >= 0
                ? channelLabel(mChannelNumber, mChannelList.get(index).title)
                : channelLabel(mChannelNumber, null));
        mChannelBanner.postDelayed(mCommitChannelNumberAction, 1500);
    }

    private void cancelChannelNumber() {
        boolean pending = !mChannelNumber.isEmpty();
        mChannelNumber = "";
        mChannelBanner.removeCallbacks(mCommitChannelNumberAction);
        if (pending) hideChannelBanner();
    }

    private void commitChannelNumber() {
        if (mChannelNumber.isEmpty()) return;
        String number = mChannelNumber;
        int index = pendingChannelIndex();
        cancelChannelNumber();
        if (index >= 0) playChannel(index);
        else showChannelBanner(getString(R.string.channel_number_invalid, number));
    }

    private final Runnable mCommitChannelNumberAction = this::commitChannelNumber;

    @Override
    // Framework callback: handle MENU before the window's built-in menu dispatch.
    @SuppressLint("RestrictedApi")
    public boolean dispatchKeyEvent(KeyEvent event) {
        PanelDialog.noteKeyEvent(event);
        if (handleEscapeKey(event) || handleChannelNumberKey(event) || handleMenuKey(event)
                || handlePlaybackNavigation(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean handlePlaybackNavigation(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                    showProviderMenu();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) saveSnapshot();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getAction() == KeyEvent.ACTION_UP && PanelDialog.isShortPress(event))
                    playNextItem(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN);
                return true;
            default:
                return false;
        }
    }

    private boolean handleMenuKey(KeyEvent event) {
        if (!ToolMenu.isMenuKey(event.getKeyCode())) return false;
        PanelDialog.noteKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (mChannelPanel.isShowing() && mChannelMenu.getVisibility() == View.VISIBLE) {
                togglePanelPin();
            } else if (mChannelPanel.isShowing()) mChannelPanel.dismiss();
            else showPanel(0);
        }
        return true;
    }

    private void togglePanelPin() {
        mPanelPinned = !mPanelPinned;
        mPinIcon.setImageResource(mPanelPinned ? R.drawable.ic_panel_pin : R.drawable.ic_panel_menu);
    }

    private boolean handleEscapeKey(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_ESCAPE) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
            getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_INFO) {
            if (PanelDialog.isShortPress(event)) showChannelInfo();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private int fixCurrentIndexIfNecessary() {
        int index = mChannelList.getCurrentItem();
        Channel bean = (index >= 0 && index < mChannelList.size()) ? mChannelList.get(index) : null;
        if (mChannel != null && !Channel.equals(mChannel, bean)) {
            index = mChannelList.setCurrentItem(mChannel, index);
            if (index == -1) {
                for (int i = mChannelList.size() - 1; i >= 0; i--) {
                    if (Channel.equals(mChannelList.get(i).url, mChannel.url)) {
                        index = i;
                        mChannelList.setCurrentItem(index);
                        break;
                    }
                }
            }
            if (index != -1)
                saveStatusString(PrefUtils.STATE_CHANNEL_URL, mChannel.url);
        }
        if (index >= 0 && index < mChannelList.size())
            mChannel = mChannelList.get(index);
        return index;
    }

    private void focusCurrentChannel() {
        cancelChannelFocus();
        mRevealCurrentChannel = true;
        focusVisibleChannel();
    }

    private void focusVisibleChannel() {
        if (mPanelMode == 0) return;
        if (mChannelList.size() == 0) {
            mChannelMenu.requestFocusFromTouch();
            return;
        }
        if (mPendingChannelFocus >= 0 && mPendingChannelFocus < mChannelList.size()) return;
        cancelChannelFocus();
        GridLayoutManager layout = (GridLayoutManager) mChannelMenu.getLayoutManager();
        int current = mRevealCurrentChannel ? fixCurrentIndexIfNecessary() : mChannelList.getCurrentItem();
        if (mRevealCurrentChannel && current >= 0 && current < mChannelList.size()) {
            mRevealCurrentChannel = false;
            positionCurrentChannel(current);
            RecyclerView.ViewHolder holder = mChannelMenu.findViewHolderForAdapterPosition(current);
            if (holder != null) holder.itemView.requestFocusFromTouch();
            else focusChannel(current);
            return;
        }
        int first = layout.findFirstCompletelyVisibleItemPosition();
        int last = layout.findLastCompletelyVisibleItemPosition();
        View focused = mChannelMenu.findFocus();
        RecyclerView.ViewHolder previous = focused == null ? null : mChannelMenu.findContainingViewHolder(focused);
        int index = previous == null ? mChannelList.getCurrentItem() : previous.getBindingAdapterPosition();
        if (index < first || index > last) index = first;
        RecyclerView.ViewHolder holder = index < 0 ? null : mChannelMenu.findViewHolderForAdapterPosition(index);
        if (holder == null || !holder.itemView.requestFocus()) mChannelMenu.requestFocus();
    }

    private void focusChannel(int index) {
        cancelChannelFocus();
        mPendingChannelFocus = index;
        mChannelMenu.stopScroll();
        RecyclerView.ViewHolder holder = mChannelMenu.findViewHolderForAdapterPosition(index);
        if (holder != null && holder.itemView.requestFocus()) {
            mPendingChannelFocus = -1;
            return;
        }
        mChannelMenu.getViewTreeObserver().addOnGlobalLayoutListener(mFocusChannelAfterLayout);
        ((GridLayoutManager) mChannelMenu.getLayoutManager()).scrollToPositionWithOffset(index, 0);
    }

    private void positionCurrentChannel(int current) {
        if (current < 0) return;
        GridLayoutManager layout = (GridLayoutManager) mChannelMenu.getLayoutManager();
        View view = layout.findViewByPosition(current);
        if (!mChannelMenu.hasPendingAdapterUpdates() && view != null && layout.getDecoratedTop(view) >= mChannelMenu.getPaddingTop()
                && layout.getDecoratedBottom(view) <= mChannelMenu.getHeight() - mChannelMenu.getPaddingBottom()) return;
        int offset = Math.max(0, (mChannelMenu.getHeight() - mChannelMenu.getPaddingTop() - mChannelMenu.getPaddingBottom()
                - getResources().getDimensionPixelSize(R.dimen.navigation_item_height)) / 2);
        layout.scrollToPositionWithOffset(current - (layout.getSpanCount() == 1 ? 0 : current % 2), offset);
    }

    private void cancelChannelFocus() {
        mPendingChannelFocus = -1;
        if (mChannelMenu != null && mChannelMenu.getViewTreeObserver().isAlive())
            mChannelMenu.getViewTreeObserver().removeOnGlobalLayoutListener(mFocusChannelAfterLayout);
    }

    private final ViewTreeObserver.OnGlobalLayoutListener mFocusChannelAfterLayout = this::focusChannelAfterLayout;

    private void focusChannelAfterLayout() {
        if (!mResumed || !mChannelPanel.isShowing() || mPendingChannelFocus < 0
                || mPendingChannelFocus >= mChannelList.size()) {
            cancelChannelFocus();
            return;
        }
        RecyclerView.ViewHolder holder = mChannelMenu.findViewHolderForAdapterPosition(mPendingChannelFocus);
        if (holder != null && holder.itemView.requestFocusFromTouch()) cancelChannelFocus();
    }

    private boolean handleDrawerNavigation(KeyEvent event) {
        int key = event.getKeyCode();
        if ((key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) && mChannelMenu.hasFocus()) {
            View focused = mChannelMenu.findFocus();
            RecyclerView.ViewHolder holder = focused == null ? null : mChannelMenu.findContainingViewHolder(focused);
            if (holder != null) {
                int position = holder.getBindingAdapterPosition();
                int columns = ((GridLayoutManager) mChannelMenu.getLayoutManager()).getSpanCount();
                if (position != RecyclerView.NO_POSITION && (key == KeyEvent.KEYCODE_DPAD_UP
                        ? position < columns : position + columns >= mChannelList.size())) return true;
            }
            return false;
        }
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) mRevealCurrentChannel = false;
        if (mSourceMenu != null && mSourceMenu.hasFocus()) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (event.getRepeatCount() == 0) mChannelPanel.dismiss();
                return true;
            }
            if (event.getRepeatCount() > 0) return true;
            if (mPanelMode == 0) {
                showPanel(1);
                return true;
            }
            View source = mSourceMenu.getFocusedChild();
            if (source != null && source.getTag() instanceof MenuItem
                    && ((MenuItem) source.getTag()).getGroupId() == ToolMenu.GROUP_PROVIDER) source.performClick();
            else focusCurrentChannel();
            return true;
        }
        if (mChannelList.size() == 0 && key == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (mSourceMenu == null) showProviderMenu();
            else ToolMenu.focusCurrent(mSourceMenu);
            return true;
        }
        View focused = mChannelMenu.findFocus();
        RecyclerView.ViewHolder holder = focused == null ? null : mChannelMenu.findContainingViewHolder(focused);
        if (holder == null && mPendingChannelFocus < 0) {
            focusVisibleChannel();
            return true;
        }
        int position = mPendingChannelFocus >= 0 ? mPendingChannelFocus : holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return true;
        int columns = ((GridLayoutManager) mChannelMenu.getLayoutManager()).getSpanCount();
        if (key == KeyEvent.KEYCODE_DPAD_LEFT && position % columns == 0) {
            if (mSourceMenu == null) showProviderMenu();
            else ToolMenu.focusCurrent(mSourceMenu);
        } else focusChannel(key == KeyEvent.KEYCODE_DPAD_RIGHT ? (position + 1) % mChannelList.size() : position - 1);
        return true;
    }

    private void playChannel(Channel channel) {
        if (mChannelInfoDialog != null && !mChannelInfoDialog.isForChannel(channel))
            mChannelInfoDialog.dismiss();
        SharedPreferences pref = PrefUtils.getConfigPref(this);
        mAspectRatio = pref.getFloat(PrefUtils.TV_ASPECT_RATIO + ":" + channel.url, 16 / 9f);
        mPlayerView.setAspectRatio(mAspectRatio);
        if (!mResumed) return;
        Uri uri = Uri.parse(channel.url);
        int sourceType = PlayerView.getFixedSourceType(uri);
        mPlayerView.removeCallbacks(mReplayAction);
        if (!Channel.equals(mPlaybackUrl, channel.url)) {
            mAudioOnly = false;
            updatePlaybackPresentation();
        }
        mPlaybackUrl = channel.url;
        mPlayerView.setUri(uri, sourceType);
    }

    private void updatePlaybackPresentation() {
        updateLoading();
        if (mSourceMenu != null && mSnapshotItem != null && mSnapshotItem.isEnabled() != canSnapshot()) {
            mSnapshotItem.setEnabled(canSnapshot());
            mSourceMenu.getAdapter().notifyItemRangeChanged(0, mSourceMenu.getAdapter().getItemCount());
        }
        mRadioIcon.setVisibility(mAudioOnly ? View.VISIBLE : View.GONE);
    }

    private void playChannel(int index) {
        if (index < 0 || index >= mChannelList.size())
            return;

        Channel channel = mChannelList.get(index);
        if (channel == null)
            return;
        if (Channel.equals(channel, mChannel) && Channel.equals(mPlaybackUrl, channel.url)) return;

        resetPlaybackFailures();
        mOpenedExternally = false;
        mChannel = channel;
        mChannelList.setCurrentItem(index);
        playChannel(mChannel);
        showChannelBanner(channelLabel(String.valueOf(channel.number), channel.title));

        SharedPreferences.Editor state = PrefUtils.getStatePref(this).edit()
                .putString(PrefUtils.STATE_CHANNEL_URL, mChannel.url);
        if (mCurrentFolder != null) state.putString(PrefUtils.STATE_CHANNEL_SERVER, mCurrentFolder.id)
                .putString(PrefUtils.STATE_CHANNEL_URL + ":" + mCurrentFolder.url, mChannel.url);
        state.apply();
    }

    private boolean playNextItem(boolean forward) {
        if (mLoadingPlaylist && mProviderChanged) return false;
        cancelChannelNumber();
        int count = mChannelList.size();
        if (count == 0) return false;
        int index = fixCurrentIndexIfNecessary();
        int next;
        if (forward)
            next = index < count - 1 ? index + 1 : 0;
        else
            next = index > 0 ? index - 1 : count - 1;
        if (next == index)
            return false;

        if (next >= 0 && next < count)
            playChannel(next);
        return true;
    }

    private void showChannelBanner(CharSequence text) {
        if (mChannelPanel.isShowing()) {
            mPendingChannelBanner = text;
            return;
        }
        mChannelBanner.removeCallbacks(mHideChannelBannerAction);
        mChannelBanner.setText(text);
        mBannerContainer.setVisibility(View.VISIBLE);
        mChannelBanner.postDelayed(mHideChannelBannerAction, 3000);
    }

    private CharSequence channelLabel(String number, String name) {
        SpannableString label = new SpannableString(name == null ? number
                : getString(R.string.channel_number_name, number, name));
        label.setSpan(new ForegroundColorSpan(getColor(R.color.channel_number)),
                0, number.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return label;
    }

    private void hideChannelBanner() {
        mChannelBanner.removeCallbacks(mHideChannelBannerAction);
        mBannerContainer.setVisibility(View.GONE);
    }

    private final Runnable mHideChannelBannerAction = () -> {
        if (mLoadingView.getVisibility() != View.VISIBLE) mBannerContainer.setVisibility(View.GONE);
    };

    private String loadStatusString(String key, String defValue) {
        return PrefUtils.getStatePref(this).getString(key, defValue);
    }

    private void saveStatusString(String key, String value) {
        PrefUtils.getStatePref(this).edit().putString(key, value).apply();
    }

    private static final int REQUEST_SNAPSHOT_PERMISSION = 1;

    private boolean canSnapshot() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mResumed && mChannel != null
                && !mAudioOnly && mLastState == PlayerView.State.READY;
    }

    private void saveSnapshot() {
        if (!canSnapshot()) return;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_SNAPSHOT_PERMISSION);
            return;
        }
        mPlayerView.captureFrame((snapshot, error) -> {
            if (error != null) {
                showToast(getString(R.string.snapshot_failed, error.getMessage()));
                return;
            }
            saveSnapshot(snapshot);
        });
    }

    private void saveSnapshot(Bitmap snapshot) {
        final String name = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", java.util.Locale.US).format(new Date()) + ".jpg";
        AsyncTask.execute(() -> {
            try {
                SnapshotStore.save(getApplicationContext(), snapshot, name);
                showToast(getString(R.string.snapshot_saved, name));
            } catch (Exception error) {
                showToast(getString(R.string.snapshot_failed, error.getMessage()));
            } finally {
                snapshot.recycle();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_SNAPSHOT_PERMISSION && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) saveSnapshot();
    }

    private Channel mCurrentFolder = null;
    private int mChannelRequest;
    private boolean mProviderChanged;

    private void setChannelProvider(int index, boolean refresh) {
        if (index < 0 || index >= mProviderList.size())
            return;

        cancelChannelNumber();
        cancelChannelFocus();

        final Channel previousFolder = mCurrentFolder;
        final Channel folder = mCurrentFolder = mProviderList.get(index);
        boolean changed = !Channel.equals(previousFolder, folder);
        mProviderChanged |= previousFolder != null && changed;
        mRestoredChannelUrl = loadStatusString(PrefUtils.STATE_CHANNEL_URL + ":" + folder.url,
                previousFolder == null ? mRestoredChannelUrl : null);
        setPlaylistLoading(true);
        saveStatusString(PrefUtils.STATE_CHANNEL_SERVER, folder.id);

        requestPlaylist(folder, false, refresh, list -> {
            mChannelList.replaceAll(list);
            setPlaylistLoading(false);
            if (mProviderChanged && !list.isEmpty()) {
                mChannel = null;
                mProviderChanged = false;
            }
            if (!list.isEmpty()) {
                if (mChannel == null || !mOpenedExternally && fixCurrentIndexIfNecessary() < 0) {
                    int restore = 0;
                    for (int i = 0; i < mChannelList.size(); i++) {
                        if (Channel.equals(mChannelList.get(i).url, mRestoredChannelUrl)) {
                            restore = i;
                            break;
                        }
                    }
                    playChannel(restore);
                }
                mRestoredChannelUrl = null;
                if (mResumed && mChannel != null && !Channel.equals(mPlaybackUrl, mChannel.url))
                    playChannel(mChannel);
            }
            mChannelMenu.post(() -> {
                if (!mChannelPanel.isShowing() || mPanelMode == 0) return;
                positionCurrentChannel(fixCurrentIndexIfNecessary());
                if (mRevealCurrentChannel || mSourceMenu == null || !mSourceMenu.hasFocus()) focusVisibleChannel();
            });
        });
    }

    private void setupProviderMenu(boolean refreshChannels) {
        final Channel root = mIptvSource.getRoot();
        ++mChannelRequest;
        setPlaylistLoading(true);
        requestPlaylist(root, true, refreshChannels, list -> {
            String id = loadStatusString(PrefUtils.STATE_CHANNEL_SERVER, null);
            int index = 0;
            for (int i = 0; i < list.size(); i++) {
                if (Channel.equals(list.get(i).id, id)) {
                    index = i;
                    break;
                }
            }
            mProviderList.clear();
            if (isPlaylistList(list)) {
                mProviderList.addAll(list);
                setChannelProvider(index, refreshChannels);
            } else {
                mProviderList.add(root);
                setChannelProvider(0, false);
            }
        });
    }

    private static boolean isPlaylistList(List<Channel> channels) {
        for (Channel channel : channels) {
            if (!channel.playlistSource) return false;
        }
        return !channels.isEmpty();
    }

    private void showSourceEditor() {
        if (mSourceEditor != null && mSourceEditor.isShowing()) return;
        mChannelPanel.dismiss();
        boolean cancelable = !mProviderList.isEmpty();
        final AlertDialog.Builder builder = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1
                ? new AlertDialog.Builder(this, R.style.Dialog_Light) : new AlertDialog.Builder(this);
        final EditText input = new EditText(builder.getContext());
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            input.setTextColor(Color.DKGRAY);
            input.setHintTextColor(Color.GRAY);
        }
        input.setId(View.generateViewId());
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint(PlaylistRepository.DEFAULT_URL);
        String currentUrl = mIptvSource.getRoot().url;
        if (!PlaylistRepository.DEFAULT_URL.equals(currentUrl)) input.setText(currentUrl);

        int padding = (int) (mDensity * 24);
        FrameLayout content = new FrameLayout(builder.getContext());
        content.setPadding(padding, 0, padding, 0);
        content.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = mSourceEditor = builder
                .setTitle(R.string.source)
                .setView(content)
                .setNegativeButton(cancelable ? getString(android.R.string.cancel) : null, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            long openedAt = SystemClock.uptimeMillis();
            View confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            View cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            input.setNextFocusDownId(confirm.getId());
            input.setNextFocusForwardId(confirm.getId());
            confirm.setNextFocusForwardId(cancelable ? cancel.getId() : input.getId());
            if (cancelable) cancel.setNextFocusForwardId(input.getId());
            confirm.setNextFocusUpId(input.getId());
            if (cancelable) cancel.setNextFocusUpId(input.getId());
            // Leave touch mode explicitly when TV remotes navigate out of the editor.
            input.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) confirm.requestFocusFromTouch();
                return true;
            });
            confirm.setOnClickListener(view -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) url = PlaylistRepository.DEFAULT_URL;
                if (!isSourceUrl(url)) {
                    input.requestFocus();
                    input.setError(getString(R.string.source_url_invalid));
                    return;
                }
                updateSource(url);
                if (cancelable) dialog.dismiss();
            });
            input.setOnEditorActionListener((view, actionId, event) -> {
                if (event != null && event.getDownTime() < openedAt) return true;
                if (event != null && event.getKeyCode() != KeyEvent.KEYCODE_DPAD_CENTER
                        && event.getKeyCode() != KeyEvent.KEYCODE_ENTER
                        && event.getKeyCode() != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
                if (event == null && actionId != EditorInfo.IME_ACTION_DONE) return false;
                if (event == null || event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                    confirm.performClick();
                return true;
            });
            input.requestFocus();
        });
        dialog.setCancelable(cancelable);
        if (cancelable) dialog.setOnKeyListener(PanelDialog::closeOnKey);
        dialog.show();
    }

    private void showChannelInfo() {
        cancelChannelNumber();
        mLastBackPressedTime = 0;
        if (mChannelInfoDialog != null && mChannelInfoDialog.isShowing()) {
            mChannelInfoDialog.dismiss();
            return;
        }
        if (mChannel != null && (mChannelInfoDialog == null || !mChannelInfoDialog.isShowing())) {
            if (mChannelPanel.isShowing()) mChannelPanel.dismiss();
            mPendingChannelBanner = null;
            hideChannelBanner();
            mChannelInfoDialog = new ChannelInfoDialog(this, mChannel, mPlayerView, mInfoPanelWidth) {
                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    return handleMenuKey(event) || super.dispatchKeyEvent(event);
                }
            };
            mChannelInfoDialog.show();
        }
    }

    private static boolean isSourceUrl(String value) {
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    private void updateSource(String url) {
        boolean changed = !Channel.equals(url, mIptvSource.getRoot().url);
        if (!changed && mLoadingPlaylist) return;
        if (!changed && mIptvSource.isInitializing()) {
            setupProviderMenu(true);
            return;
        }
        mOpenedExternally = false;
        mRestoredChannelUrl = null;
        if (changed) {
            mCurrentFolder = null;
            mChannelList.clear();
            resetPlaybackFailures();
            mAudioOnly = false;
            mRadioIcon.setVisibility(View.GONE);
            mChannel = null;
            mPlayerView.removeCallbacks(mReplayAction);
            mPlayerView.stop();
            PrefUtils.getStatePref(this).edit()
                    .remove(PrefUtils.STATE_CHANNEL_SERVER)
                    .remove(PrefUtils.STATE_CHANNEL_URL)
                    .apply();
        }
        mProviderChanged = false;
        if (changed) mProviderList.clear();
        mIptvSource.setRootUrl(url);
        PrefUtils.getConfigPref(this).edit().putString(PrefUtils.CONFIG_PLAYLIST_URL, url).apply();
        setupProviderMenu(true);
    }

    private void showProviderMenu() {
        showPanel(1);
    }

    private void showPanel(int mode) {
        if (mChannelPanel.isShowing() && mPanelMode == 2 && mode == 1 && mRootView.getWidth() < 458 * mDensity)
            TransitionManager.beginDelayedTransition(mChannelContent, new ChangeBounds().addTarget(mChannelMenu).setDuration(100)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()));
        boolean expandingMenu = mChannelPanel.isShowing() && mPanelMode == 0 && mode == 1;
        boolean hadSourceMenu = mSourceMenu != null;
        int previousWidth = mChannelPanel.isShowing() ? mChannelContent.getWidth() : 0;
        mPanelMode = mode;
        mRevealCurrentChannel = mode == 2;
        mPanelContainer.animate().cancel(); mPanelClosing = false;
        mPanelContainer.setTranslationX(0);
        cancelChannelNumber();
        if (!expandingMenu) clearSourceMenu();
        if (mChannelInfoDialog != null) mChannelInfoDialog.dismiss();
        mLastBackPressedTime = 0;
        if (mode != 2 && mSourceMenu == null) {
            Menu menu = new PopupMenu(this, mRootView).getMenu();
            getMenuInflater().inflate(R.menu.tv_actions, menu);
            menu.findItem(R.id.channel_info).setEnabled(mChannel != null);
            menu.findItem(R.id.aspect_ratio).setEnabled(mChannel != null);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) menu.removeItem(R.id.snapshot);
            else mSnapshotItem = menu.findItem(R.id.snapshot).setEnabled(canSnapshot());
            int id = 0;
            for (Channel bean : mProviderList) {
                boolean checked = bean.equals(mCurrentFolder);
                menu.add(ToolMenu.GROUP_PROVIDER, id++, Menu.NONE, bean.title).setCheckable(checked).setChecked(checked);
            }
            mSourceMenu = ToolMenu.create(this, menu, item -> {
                if (item.getGroupId() == ToolMenu.GROUP_PROVIDER) {
                    selectProvider(item.getItemId());
                } else if (item.getItemId() == R.id.aspect_ratio) {
                    mChannelPanel.dismiss();
                    showAspectRatio();
                } else if (item.getItemId() == R.id.source) {
                    showSourceEditor();
                } else if (item.getItemId() == R.id.about) {
                    mChannelPanel.dismiss();
                    new AboutDialog(this).show();
                } else if (item.getItemId() == R.id.snapshot) {
                    if (!mPanelPinned) mChannelPanel.dismiss();
                    saveSnapshot();
                } else if (item.getItemId() == R.id.channel_info) {
                    showChannelInfo();
                }
                return true;
            });
            mChannelContent.addView(mSourceMenu, 0);
        }
        layoutChannelPanel(mRootView.getWidth());
        if (hadSourceMenu != (mSourceMenu != null)) mChannelAdapter.notifyDataSetChanged();
        mChannelPanel.show();
        if (mode == 2 || expandingMenu && !mProviderChanged) mChannelMenu.post(this::focusCurrentChannel);
        int width = mChannelPanel.getWindow().getAttributes().width;
        if (width > previousWidth) {
            mPanelContainer.setTranslationX(previousWidth - width);
            mPanelContainer.animate().translationX(0).setDuration(70);
        }
    }

    private void selectProvider(int index) {
        boolean current = Channel.equals(mProviderList.get(index), mCurrentFolder);
        if (!current || !mLoadingPlaylist && mChannelList.size() == 0) {
            mOpenedExternally = false;
            mRestoredChannelUrl = null;
            setChannelProvider(index, current);
        }
        if (mPanelMode == 0) showPanel(1);
        if (mProviderChanged && mLoadingPlaylist) mRevealCurrentChannel = true;
        else focusCurrentChannel();
    }

    private void setPlaylistLoading(boolean loading) {
        mLoadingPlaylist = loading;
        mChannelMenu.setAlpha(loading && mProviderChanged ? 0.5f : 1f);
        if (mChannelPanel.isShowing()) layoutChannelPanel(mRootView.getWidth());
        updateLoading();
    }

    private void updateLoading() {
        boolean loading = mResumed && (mLoadingPlaylist || mLastState == PlayerView.State.BUFFERING);
        mLoadingView.removeCallbacks(mShowLoading);
        if (loading) mLoadingView.postDelayed(mShowLoading, 300);
        else {
            mLoadingView.setVisibility(View.GONE);
            if (mBannerContainer.getVisibility() == View.VISIBLE) {
                mChannelBanner.removeCallbacks(mHideChannelBannerAction);
                mChannelBanner.postDelayed(mHideChannelBannerAction, 3000);
            }
        }
    }

    private final Runnable mShowLoading = () -> {
        mLoadingView.setVisibility(View.VISIBLE);
        if (!mChannelPanel.isShowing() && mChannel != null && mBannerContainer.getVisibility() != View.VISIBLE)
            showChannelBanner(channelLabel(String.valueOf(mChannel.number), mChannel.title));
    };

    private void clearSourceMenu() {
        if (mSourceMenu != null) mChannelContent.removeView(mSourceMenu);
        mSourceMenu = null;
        mSnapshotItem = null;
    }

    private void layoutChannelPanel(int availableWidth) {
        int menuWidth = (int) (132 * mDensity);
        int columns = mSourceMenu == null || availableWidth >= 458 * mDensity ? 2 : 1;
        int width = mSourceMenu == null ? mPanelWidth : (int) ((columns == 2 ? 326 : 163) * mDensity);
        ((GridLayoutManager) mChannelMenu.getLayoutManager()).setSpanCount(columns);
        mChannelContent.getChildAt(mChannelContent.getChildCount() - 1).setLayoutParams(
                new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
        if (mSourceMenu != null) mSourceMenu.setLayoutParams(
                new LinearLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        boolean channels = mPanelMode != 0 && mChannelList.size() != 0;
        mChannelContent.getChildAt(mChannelContent.getChildCount() - 1).setVisibility(channels ? View.VISIBLE : View.GONE);
        mChannelPanel.setPanelWidth((channels ? width : 0) + (mSourceMenu == null ? 0 : menuWidth));
        mPinIcon.setVisibility(channels ? View.VISIBLE : View.GONE);
        mPanelHint.setVisibility(mPanelMode == 0 || mSourceMenu == null ? View.VISIBLE : View.GONE);
        mPanelHint.setScaleX(mPanelMode == 0 ? 1 : -1);
        ((FrameLayout.LayoutParams) mPanelHint.getLayoutParams()).gravity =
                (mPanelMode == 0 ? Gravity.END : Gravity.START) | Gravity.TOP;
        mPanelHint.requestLayout();
    }

    private void showChannels() {
        showPanel(mChannelList.size() == 0 ? 1 : 2);
    }

    private void showAspectRatio() {
        float[] ratios = {0, 16 / 9f, 4 / 3f};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.aspect_ratio)
                .setSingleChoiceItems(new String[]{getString(R.string.aspect_ratio_default), "16:9", "4:3"},
                        mAspectRatio == ratios[1] ? 1 : mAspectRatio == ratios[2] ? 2 : 0, (choice, index) -> {
                            mAspectRatio = ratios[index];
                            mPlayerView.setAspectRatio(mAspectRatio);
                            if (mChannel != null) PrefUtils.getConfigPref(this).edit()
                                    .putFloat(PrefUtils.TV_ASPECT_RATIO + ":" + mChannel.url, mAspectRatio).apply();
                            choice.dismiss();
                        })
                .setOnKeyListener(PanelDialog::closeOnKey).create();
        dialog.show();
        PanelDialog.immersive(dialog.getWindow());
    }

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(TvActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private class ChannelHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        Channel channel;
        ImageView iconView;
        TextView titleView;
        TextView numberView;

        public ChannelHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(view -> true);

            iconView = itemView.findViewById(R.id.icon);
            titleView = itemView.findViewById(R.id.title);
            itemView.setOnFocusChangeListener((view, focused) -> titleView.setSelected(focused));
            numberView = itemView.findViewById(R.id.number);
        }

        public void update(boolean current) {
            if (channel != null) {
                iconView.setImageDrawable(current ? mPlayIcon : null);

                numberView.setText(String.valueOf(channel.number));
                titleView.setText(channel.title);
            }
        }

        @Override
        public void onClick(View v) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || mLoadingPlaylist && mProviderChanged) return;
            playChannel(position);
            if (!mPanelPinned) mChannelPanel.dismiss();
        }
    }

    private final RecyclerView.Adapter mChannelAdapter = new RecyclerView.Adapter<ChannelHolder>() {
        @Override
        public int getItemCount() {
            return mChannelList.size();
        }

        @Override
        public ChannelHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflater.inflate(R.layout.item_channel, parent, false);
            return new ChannelHolder(view);
        }

        @Override
        public void onBindViewHolder(ChannelHolder holder, int position) {
            holder.itemView.setPaddingRelative((int) ((mSourceMenu == null && position % 2 == 0 ? 16 : 2) * mDensity),
                    holder.itemView.getPaddingTop(), (int) (2 * mDensity), holder.itemView.getPaddingBottom());
            holder.channel = mChannelList.get(position);
            holder.update(holder.channel == mChannel);
        }
    };

    private final ChannelComparator mChannelComparator = new  ChannelComparator(false) {
        @Override
        protected long onCompare(Channel bean1, Channel bean2) {
            long ret = ALPHANUM_COMPARATOR.compare(bean1.title, bean2.title);
            if (ret != 0 && !Channel.isEmpty(bean1.title) && !Channel.isEmpty(bean2.title)) {
                char c1 = bean1.title.charAt(0);
                char c2 = bean2.title.charAt(0);
                if (ret < 0 && c1 > 127 && c2 < 127)
                    ret = c1 - c2;
                else if (ret > 0 && c1 < 127 && c2 > 127)
                    ret = c1 - c2;
            }
            return ret;
        }
    };

    private final ChannelListCallback mChannelListCallback = new ChannelListCallback(mChannelAdapter) {
        @Override
        public int compare(Channel item1, Channel item2) {
            return mChannelComparator.compare(item1, item2);
        }
    };

    private final ChannelList mChannelList = new ChannelList(mChannelListCallback) {
        @Override
        public void setCurrentItem(int item) {
            int current = super.getCurrentItem();
            if (current == item)
                return;
            super.setCurrentItem(item);
            if (current != INVALID_POSITION) {
                RecyclerView.ViewHolder holder = mChannelMenu.findViewHolderForLayoutPosition(current);
                if (holder instanceof ChannelHolder)
                    ((ChannelHolder) holder).update(false);
            }
            if (item != INVALID_POSITION) {
                RecyclerView.ViewHolder holder = mChannelMenu.findViewHolderForLayoutPosition(item);
                if (holder instanceof ChannelHolder) {
                    ((ChannelHolder) holder).update(true);
                }
            }
        }
    };

    private void resetPlaybackFailures() {
        mPlaybackFailures = 0;
        mPlayerView.removeCallbacks(mResetPlaybackFailuresAction);
    }

    private final Runnable mResetPlaybackFailuresAction = this::resetPlaybackFailures;
    private PlayerView.State mLastState = PlayerView.State.NONE;
    private final PlayerView.EventListener mEventListener = new PlayerView.EventListener() {
        @Override
        public void onStateChanged(PlayerView.State state) {
            if (mLastState == state)
                return;
            mLastState = state;
            mPlayerView.removeCallbacks(mResetPlaybackFailuresAction);
            if (state == PlayerView.State.READY)
                mPlayerView.postDelayed(mResetPlaybackFailuresAction, STABLE_PLAYBACK_MS);

            updatePlaybackPresentation();
        }

        @Override
        public void onFirstFrameRendered() {
            mAudioOnly = false;
            updatePlaybackPresentation();
            mPlayerView.removeCallbacks(mResetPlaybackFailuresAction);
            mPlayerView.postDelayed(mResetPlaybackFailuresAction, STABLE_PLAYBACK_MS);
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            if (!tracks.getGroups().isEmpty()) {
                mAudioOnly = tracks.isTypeSelected(C.TRACK_TYPE_AUDIO) && !tracks.containsType(C.TRACK_TYPE_VIDEO);
                updatePlaybackPresentation();
            }
        }

        @Override
        public void onError(int param1, int param2, Exception cause) {
            mPlayerView.removeCallbacks(mResetPlaybackFailuresAction);
            String url = mChannel != null ? mChannel.url : null;
            Log.w(TAG, "onError: " + url, cause);
            if (mResumed && mChannel != null) {
                if (PlayerView.exceedsDecoderCapabilities(cause)) {
                    mPlayerView.removeCallbacks(mReplayAction);
                    showToast(getString(R.string.channel_decoder_unsupported));
                    updatePlaybackPresentation();
                    return;
                }
                if (mPlaybackFailures < PLAYBACK_FAILURE_THRESHOLD && ++mPlaybackFailures == PLAYBACK_FAILURE_THRESHOLD)
                    showToast(getString(R.string.channel_playback_failed));
                updatePlaybackPresentation();
                mPlayerView.removeCallbacks(mReplayAction);
                mPlayerView.postDelayed(mReplayAction, REPLAY_DELAY * mPlaybackFailures);
            }
        }
    };

    private final View.OnClickListener mClickListener = view -> {
        if (mChannelPanel.isShowing()) mChannelPanel.dismiss();
        else showChannels();
    };

    private float mTouchDownX;
    private float mTouchDownY;
    private int mSwipeAxis;
    private boolean mTouchMoved;
    private boolean mLongPressed;
    private boolean mNavigationGesture;
    private final View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mNavigationGesture = PanelDialog.isNavigationEdge(v, event);
                    mTouchDownX = event.getX();
                    mTouchDownY = event.getY();
                    mLongPressed = false;
                    mTouchMoved = false;
                    mSwipeAxis = View.SCROLL_AXIS_NONE;
                    if (mChannelPanel.isShowing()) return true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mSwipeAxis == View.SCROLL_AXIS_NONE) mSwipeAxis = PanelDialog.swipeAxis(v.getContext(), event.getX() - mTouchDownX, event.getY() - mTouchDownY);
                    if (Math.max(Math.abs(event.getX() - mTouchDownX),
                            Math.abs(event.getY() - mTouchDownY)) > ViewConfiguration.get(v.getContext()).getScaledTouchSlop()) {
                        mTouchMoved = true;
                        v.cancelLongPress();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - mTouchDownX;
                    float dy = event.getY() - mTouchDownY;
                    if (mChannelPanel.isShowing()) {
                        if (!mLongPressed && !mNavigationGesture && PanelDialog.swipeAxis(v.getContext(), dx, dy) == View.SCROLL_AXIS_HORIZONTAL && dx > 0)
                            showProviderMenu();
                        else if (!mLongPressed && !mTouchMoved && !mPanelPinned) mChannelPanel.dismiss();
                        return true;
                    }
                    if (mLongPressed || mTouchMoved || Math.max(Math.abs(dx), Math.abs(dy)) > ViewConfiguration.get(v.getContext()).getScaledTouchSlop()) {
                        event.setAction(MotionEvent.ACTION_CANCEL);
                        v.onTouchEvent(event);
                        event.setAction(MotionEvent.ACTION_UP);
                        int axis = mLongPressed || mNavigationGesture ? View.SCROLL_AXIS_NONE : mSwipeAxis == View.SCROLL_AXIS_NONE ? PanelDialog.swipeAxis(v.getContext(), dx, dy) : mSwipeAxis;
                        if (axis == View.SCROLL_AXIS_VERTICAL && Math.abs(dy) > 40 * mDensity) playNextItem(dy < 0);
                        else if (axis == View.SCROLL_AXIS_HORIZONTAL && Math.abs(dx) > 40 * mDensity) {
                            if (dx > 0) showProviderMenu();
                            else if (!PanelDialog.isNavigationEdge(v, event)) saveSnapshot();
                        }
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    v.cancelLongPress();
                    mLongPressed = true;
                    break;
            }
            return mChannelPanel.isShowing();
        }
    };

    private final Runnable mReplayAction = new Runnable() {
        @Override
        public void run() {
            if (mChannelPanel.isShowing() && !mPanelPinned)
                mPlayerView.postDelayed(this, REPLAY_DELAY);
            else if (mChannel != null && !isFinishing())
                playChannel(mChannel);
        }
    };

    private interface PlaylistResult {
        void onResult(List<Channel> channels);
    }

    private void requestPlaylist(Channel playlist, boolean providers, boolean refresh, PlaylistResult result) {
        int request = providers ? ++mProviderRequest : ++mChannelRequest;
        int channelRequest = mChannelRequest;
        mIptvSource.request(playlist, refresh, new PlaylistRepository.Listener() {
            private boolean isCurrent() {
                return !isDestroyed() && request == (providers ? mProviderRequest : mChannelRequest)
                        && (!providers || channelRequest == mChannelRequest);
            }

            @Override
            public void onResult(List<Channel> channels) {
                boolean fromNetwork = mIptvSource.fromNetwork;
                runOnUiThread(() -> {
                    if (!isCurrent()) return;
                    if (providers && mProviderList.isEmpty() && mSourceEditor != null) mSourceEditor.dismiss();
                    if (fromNetwork && (!providers || !isPlaylistList(channels))) showToast(getString(R.string.channel_list_loaded));
                    result.onResult(channels);
                });
            }

            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Load playlist failed: " + playlist.title, error);
                runOnUiThread(() -> {
                    if (!isCurrent()) return;
                    if (!providers && mProviderChanged) {
                        mChannelList.clear();
                        mProviderChanged = false;
                    }
                    setPlaylistLoading(false);
                    if (mResumed && (mSourceEditor == null || !mSourceEditor.isShowing())) {
                        if (providers) showSourceEditor();
                        else showProviderMenu();
                    }
                });
            }
        });
    }
}
