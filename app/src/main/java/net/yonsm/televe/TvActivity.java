package net.yonsm.televe;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import android.app.AlertDialog;
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
import io.library.widget.DrawerDelegate;
import io.library.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
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
import internal.menu.ToolMenuItem;
import internal.utils.PrefUtils;
import internal.utils.SnapshotStore;
import android.Manifest;
import android.content.pm.PackageManager;
import internal.widget.IToolbar;
import internal.widget.IToolbarDelegate;
import internal.widget.ToolbarV21;
import io.library.exo.PlayerView;

public class TvActivity extends ComponentActivity {
    private static final String TAG = "TvActivity";

    private static final int REPLAY_DELAY = 1000;

    private float mDensity;
    private float mAspectRatio;
    private boolean mOnlyHD;
    private Channel mChannel;
    private Drawable mPlayIcon;
    private Drawable mHDIcon;
    private Drawable m4KIcon;
    private IToolbar mToolbar;
    private RecyclerView mChannelMenu;
    private DrawerLayout mDrawerLayout;
    private ProgressBar mLoadingView;
    private PlayerView mPlayerView;
    private TextView mProviderView;
    private TextView mTimeView;
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

        final SharedPreferences pref = PrefUtils.getConfigPref(this);
        mAspectRatio = pref.getFloat(PrefUtils.TV_ASPECT_RADIO, 16 / 9f);
        mOnlyHD = pref.getBoolean(PrefUtils.CONFIG_ONLY_HD, false);
        mHDIcon = ToolMenuItem.getIcon(this, R.drawable.ic_logo_hd, 0x80FFFFFF);
        m4KIcon = ToolMenuItem.getIcon(this, R.drawable.ic_logo_4k, 0x80FFFFFF);
        mPlayIcon = ToolMenuItem.getIcon(this, R.drawable.ic_action_play);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerLayout.setScrimColor(Color.TRANSPARENT);
        int vis = View.SYSTEM_UI_LAYOUT_FLAGS | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        vis |= View.SYSTEM_UI_FLAG_IMMERSIVE;
        mDrawerLayout.setSystemUiVisibility(vis);

        ToolbarV21 toolbar = mDrawerLayout.findViewById(R.id.toolbar);
        setActionBar(toolbar);
        mToolbar = toolbar;
        DrawerDelegate delegate = new IToolbarDelegate(mToolbar);
        mToolbar.setBackgroundColor(Color.TRANSPARENT);
        mToolbar.getView().setVisibility(View.GONE);
        mDrawerLayout.init(mToolbar.getView(), delegate, mDrawerListener);
        mDrawerLayout.setOnClickListener(mClickListener);
        mDrawerLayout.setStatusBarStyle(Color.TRANSPARENT, 0);

        mChannelMenu = mDrawerLayout.findViewById(R.id.channel_menu);
        mChannelMenu.setAdapter(mChannelAdapter);
        mChannelMenu.setHasFixedSize(true);
        mChannelMenu.setLayoutManager(new GridLayoutManager(this, 2));
        mIptvSource = new PlaylistRepository(this,
                PrefUtils.getConfigPref(this).getString(PrefUtils.CONFIG_PLAYLIST_URL, PlaylistRepository.DEFAULT_URL));


        mLoadingView = mDrawerLayout.findViewById(R.id.loading);

        mPlayerView = mDrawerLayout.findViewById(R.id.player);
        mPlayerView.addEventListener(mEventListener);
        mPlayerView.setKeepScreenOn(true);
        mPlayerView.setOnClickListener(mClickListener);
        mPlayerView.setOnKeyListener(mKeyListener);
        mPlayerView.setOnLongClickListener(v -> {
            mLongPressed = true;
            saveSnapshot();
            return true;
        });
        mPlayerView.setOnTouchListener(mTouchListener);
        mPlayerView.requestFocus();

        if (mPlayerView.getAspectRatio() != mAspectRatio) {
            mPlayerView.setAspectRatio(mAspectRatio);
        }


        mProviderView = mDrawerLayout.findViewById(R.id.provider);
        mProviderView.setCompoundDrawablesWithIntrinsicBounds(ToolMenuItem.getIcon(this, R.drawable.ic_action_arrow_down), null, null, null);
        mProviderView.setOnClickListener(mClickListener);
        mProviderView.setOnFocusChangeListener(mFocusListener);

        mTimeView = mDrawerLayout.findViewById(R.id.time);
        scheduleTimeUpdate();

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
        if (channel == null) {
            String url = loadStatusString(PrefUtils.STATE_CHANNEL_URL, null);
            if (!Channel.isEmpty(url)) {
                channel = Channel.fromUrl(url);
                channel.url = url;
            }
        }
        mChannel = channel;
        setupProviderMenu(false);
    }

    @Override
    protected void onDestroy() {
        if (mPlayerView != null) {
            mPlayerView.setKeepScreenOn(false);
            mPlayerView.removeCallbacks(mReplayAction);
            mPlayerView.removeEventListener(mEventListener);
            mPlayerView.release();
        }
        if (mTimeView != null)
            mTimeView.removeCallbacks(mUpdateTimeAction);
        if (mIptvSource != null) mIptvSource.close();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mResumed = false;
        mPlayerView.removeCallbacks(mReplayAction);
        mPlayerView.stop();
        if (mTimeView != null)
            mTimeView.removeCallbacks(mUpdateTimeAction);
    }

    @Override
    public void onResume() {
        super.onResume();

        mResumed = true;
        scheduleTimeUpdate();

        if (mChannel != null)
            playChannel(mChannel);
    }

    private long mLastBackPressedTime;
    private void handleBackPressed() {
        long now = System.currentTimeMillis();

        if (mDrawerLayout.isDrawerOpened()) {
            mDrawerLayout.closeDrawer(true);
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

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showProviderMenu();
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

    private void focusToCurrentChannel() {
        fixCurrentIndexIfNecessary();
        mChannelMenu.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                int index = mChannelList.getCurrentItem();
                RecyclerView.ViewHolder holder = mChannelMenu.findViewHolderForLayoutPosition(index);
                if (holder != null) {
                    holder.itemView.requestFocus();
                } else if (index >= 0 && index < mChannelList.size()) {
                    mChannelMenu.smoothScrollToPosition(index);
                    mChannelMenu.postOnAnimation(this);
                } else {
                    mProviderView.requestFocus();
                }
            }
        });
    }

    private void playChannel(Channel channel) {
        mToolbar.setTitle(channel.title);
        if (!mResumed) return;
        Uri uri = Uri.parse(channel.url);
        int sourceType = PlayerView.getFixedSourceType(uri, true);
        mPlayerView.removeCallbacks(mReplayAction);
        mPlaybackStartTime = System.currentTimeMillis();
        mPlayerView.setUri(this, uri, null, sourceType);
    }

    private void playChannel(int index) {
        if (index < 0 || index >= mChannelList.size())
            return;

        Channel channel = mChannelList.get(index);
        if (channel == null)
            return;
        if (Channel.equals(channel, mChannel) && mLastState == PlayerView.State.READY)
            return;

        final int animate = index - mChannelList.getCurrentItem();
        mChannel = channel;
        mChannelList.setCurrentItem(index);
        playChannel(mChannel);

        saveStatusString(PrefUtils.STATE_CHANNEL_URL, mChannel.url);

        if (animate != 0) {
            // 切台淡入，不抓帧、不平移双 View，省内存与 CPU
            mPlayerView.setAlpha(0f);
            mLoadingView.setVisibility(View.VISIBLE);
            mAnimateWhenFirstFrame = animate;
        }
    }

    private boolean playNextItem(boolean forward) {
        int index = fixCurrentIndexIfNecessary();
        int count = mChannelList.size();
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

    private String loadStatusString(String key, String defValue) {
        return PrefUtils.getStatePref(this).getString(key, defValue);
    }

    private void saveStatusString(String key, String value) {
        PrefUtils.getStatePref(this).edit().putString(key, value).apply();
    }

    private Bitmap getSnapshotBitmap() {
        if (mLastState != PlayerView.State.READY)
            return null;

        Bitmap bitmap = null;
        try {
            bitmap = mPlayerView.getBitmap();
        } catch (Throwable e) {
            showToast("截图失败！\n" + e.getMessage());
        }
        return bitmap;
    }

    private static final int REQUEST_SNAPSHOT_PERMISSION = 1;

    private void saveSnapshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_SNAPSHOT_PERMISSION);
            return;
        }
        final Bitmap snapshot = getSnapshotBitmap();
        if (snapshot == null) return;
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

        final Channel previousFolder = mCurrentFolder;
        final Channel folder = mCurrentFolder = mProviderList.get(index);
        final int request = ++mChannelRequest;
        mProviderChanged |= previousFolder != null && previousFolder != folder;
        mProviderView.setText(folder.title);
        mChannelList.clear();
        saveStatusString(PrefUtils.STATE_CHANNEL_SERVER, folder.id);

        mIptvSource.request(folder, refresh, new ChannelListener() {
            private List<Channel> mResult;

            @Override
            public void onResult(List<Channel> list) {
                mResult = list;
                runOnUiThread(this);
            }

            @Override
            public void onError(Throwable err) {
                showToast("Load list failed: " + folder.id);
                Log.e(TAG, "Load list failed: " + folder.title, err);
            }

            @Override
            public void run() {
                List<Channel> list = mResult;
                if (!isDestroyed() && request == mChannelRequest && mCurrentFolder == folder && list != null) {
                    if (mOnlyHD) {
                        for (int i = list.size() - 1; i >= 0; i--) {
                            if (!isHD(list.get(i).title)) list.remove(i);
                        }
                    }
                    mChannelList.replaceAll(list);
                    if (mProviderChanged && !list.isEmpty()) {
                        mChannel = null;
                        mProviderChanged = false;
                    }
                    if (mChannel == null) {
                        playChannel(0);
                    } else {
                        fixCurrentIndexIfNecessary();
                    }
                }
            }
        });
    }

    private void setupProviderMenu(boolean refresh) {
        final Channel root = mIptvSource.getRoot();
        mIptvSource.request(root, refresh, new ChannelListener() {
            private List<Channel> mResult;
            private int mIndex = 0;

            @Override
            public void onResult(List<Channel> list) {
                if (list != null) {
                    String id = loadStatusString(PrefUtils.STATE_CHANNEL_SERVER, null);
                    int index = 0;
                    for (Channel bean : list) {
                        if (Channel.equals(bean.id, id)) {
                            mIndex = index;
                            break;
                        }
                        index++;
                    }
                    mResult = list;
                }
                runOnUiThread(this);
            }

            @Override
            public void onError(Throwable err) {
                showToast("Load lists failed!");
                Log.e(TAG, "Load lists failed!", err);
            }

            @Override
            public void run() {
                List<Channel> list = mResult;
                if (!isDestroyed() && mIptvSource.getRoot() == root && list != null && !list.isEmpty()) {
                    mProviderList.clear();
                    if (isPlaylistList(list)) {
                        mProviderList.addAll(list);
                        setChannelProvider(mIndex, false);
                    } else {
                        mProviderList.add(root);
                        setChannelProvider(0, false);
                    }
                }
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
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(builder.getContext());
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.source_url_hint);
        input.setText(mIptvSource.getRoot().url);

        int padding = (int) (mDensity * 24);
        FrameLayout content = new FrameLayout(builder.getContext());
        content.setPadding(padding, 0, padding, 0);
        content.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = builder
                .setTitle(R.string.source)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String url = input.getText().toString().trim();
            if (!isSourceUrl(url)) {
                input.setError(getString(R.string.source_url_invalid));
                return;
            }
            updateSource(url);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static boolean isSourceUrl(String value) {
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    private void updateSource(String url) {
        boolean changed = !Channel.equals(url, mIptvSource.getRoot().url);
        if (changed) {
            mChannel = null;
            mPlayerView.removeCallbacks(mReplayAction);
            mPlayerView.stop();
            PrefUtils.getStatePref(this).edit()
                    .remove(PrefUtils.STATE_CHANNEL_SERVER)
                    .remove(PrefUtils.STATE_CHANNEL_URL)
                    .apply();
        }
        mCurrentFolder = null;
        mProviderChanged = false;
        mProviderList.clear();
        mChannelList.clear();
        mProviderView.setText(url);
        PrefUtils.getConfigPref(this).edit().putString(PrefUtils.CONFIG_PLAYLIST_URL, url).apply();
        mIptvSource.setRootUrl(url);
        setupProviderMenu(true);
    }

    private void setSystemUiVisible(boolean visible) {
        final int curVis = mDrawerLayout.getSystemUiVisibility();
        int vis = visible ? (curVis & ~View.SYSTEM_UI_FLAG_LOW_PROFILE) : (curVis | View.SYSTEM_UI_FLAG_LOW_PROFILE);
        if ((curVis & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0)
            vis = visible ? (vis & ~View.SYSTEM_UI_FLAG_FULLSCREEN) : (vis | View.SYSTEM_UI_FLAG_FULLSCREEN);
        if ((curVis & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0)
            vis = visible ? (vis & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) : (vis | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        if (vis != curVis)
            mDrawerLayout.setSystemUiVisibility(vis);
    }

    private static final int MENU_GROUP_PROVIDER = 1;

    private void showProviderMenu() {
        final PopupMenu.OnMenuItemClickListener clickListener = new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getGroupId() == MENU_GROUP_PROVIDER) {
                    setChannelProvider(item.getItemId(), true);
                    return true;
                }

                int itemId = item.getItemId();
                if (itemId == R.id.action_only_hd) {
                    mOnlyHD = !mOnlyHD;
                    setChannelProvider(mProviderList.indexOf(mCurrentFolder), false);
                    PrefUtils.getConfigPref(TvActivity.this).edit().putBoolean(PrefUtils.CONFIG_ONLY_HD, mOnlyHD).apply();
                    return true;
                } else if (itemId == R.id.aspect_ratio_default) {
                    mAspectRatio = 0;
                } else if (itemId == R.id.aspect_ratio_16_9) {
                    mAspectRatio = 16 / 9f;
                } else if (itemId == R.id.aspect_ratio_4_3) {
                    mAspectRatio = 4 / 3f;
                } else if (itemId == R.id.source) {
                    showSourceEditor();
                    return true;
                } else if (itemId == R.id.about) {
                    new AboutDialog(TvActivity.this).show();
                    return true;
                }
                if (mAspectRatio != mPlayerView.getAspectRatio()) {
                    mPlayerView.setAspectRatio(mAspectRatio);
                    PrefUtils.getConfigPref(TvActivity.this).edit().putFloat(PrefUtils.TV_ASPECT_RADIO, mAspectRatio).apply();
                    return true;
                }
                return false;
            }
        };

        int ratioId = R.id.aspect_ratio_default;
        if (mAspectRatio == 16 / 9f) ratioId = R.id.aspect_ratio_16_9;
        else if (mAspectRatio == 4 / 3f) ratioId = R.id.aspect_ratio_4_3;

        PopupMenu popup = new PopupMenu(this, mProviderView);
        popup.setOnMenuItemClickListener(clickListener);
        Menu menu = popup.getMenu();
        getMenuInflater().inflate(R.menu.tv_actions, menu);
        menu.findItem(R.id.action_only_hd).setCheckable(true).setChecked(mOnlyHD);
        menu.findItem(ratioId).setCheckable(true).setChecked(true);
        int id = 0;
        for (Channel bean : mProviderList) {
            boolean checked = bean.equals(mCurrentFolder);
            menu.add(MENU_GROUP_PROVIDER, id++, Menu.NONE, bean.title).setCheckable(checked).setChecked(checked);
        }
        popup.show();
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TvActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class ChannelHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        Channel channel;
        ImageView iconView;
        TextView titleView;

        public ChannelHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            itemView.setOnFocusChangeListener(mFocusListener);

            iconView = itemView.findViewById(R.id.icon);
            titleView = itemView.findViewById(R.id.title);
        }

        public void update(boolean current) {
            if (channel != null) {
                iconView.setImageDrawable(current ? mPlayIcon : null);

                String title = channel.title != null ? channel.title : "";
                boolean isHD = isHD(title);
                Drawable icon = null;
                if (isHD) {
                    icon = is4K(title) ? m4KIcon : mHDIcon;
                    title = title.substring(0, title.length() - 2).trim();
                }
                titleView.setText(title);
                titleView.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
            }
        }

        @Override
        public void onClick(View v) {
            playChannel(getLayoutPosition());
            mDrawerLayout.closeDrawer(true);
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
                    mChannelMenu.smoothScrollToPosition(item);
                }
            }
        }
    };

    private int mAnimateWhenFirstFrame;
    private long mPlaybackStartTime;
    private PlayerView.State mLastState = PlayerView.State.NONE;
    private final PlayerView.EventListener mEventListener = new PlayerView.EventListener() {
        @Override
        public void onStateChanged(PlayerView.State state) {
            if (mLastState == state)
                return;
            mLastState = state;

            int visibility = state == PlayerView.State.BUFFERING ? View.VISIBLE : View.GONE;
            mLoadingView.setVisibility(visibility);

            if (state == PlayerView.State.READY) {
                final int width = mPlayerView.getVideoWidth();
                final int height = mPlayerView.getVideoHeight();
                Log.d(TAG, "Load " + (mChannel != null ? mChannel.url : null) + ", " + width + "x" + height
                        + ", used=" + (System.currentTimeMillis() - mPlaybackStartTime));
                if (!mDrawerLayout.isDrawerOpened())
                    setSystemUiVisible(false);
            }
            Log.d(TAG, "onPlayerStateChanged: " + state);
        }

        @Override
        public void onFirstFrameRendered() {
            if (mAnimateWhenFirstFrame != 0) {
                mAnimateWhenFirstFrame = 0;
                // 新画面淡入
                mPlayerView.animate().alpha(1f).start();
            } else if (mPlayerView.getAlpha() != 1f) {
                // 兜底：重播/失败恢复后确保画面可见
                mPlayerView.setAlpha(1f);
            }
        }

        @Override
        public void onError(int param1, int param2, Exception cause) {
            if (mResumed && mChannel != null) {
                mPlayerView.removeCallbacks(mReplayAction);
                mPlayerView.postDelayed(mReplayAction, REPLAY_DELAY);
            }
            // 失败时确保画面可见，避免停在淡入的 alpha=0
            mPlayerView.setAlpha(1f);
            String url = mChannel != null ? mChannel.url : null;
            showToast("Play failed: " + url);
            Log.w(TAG, "onError: " + url, cause);
        }
    };

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view == mDrawerLayout || view == mPlayerView) {
                mDrawerLayout.openDrawer(true);
            } else if (view == mProviderView) {
                showProviderMenu();
            }
        }
    };

    private float mTouchDownY;
    private boolean mLongPressed;

    private final View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchDownY = event.getY();
                    mLongPressed = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getY() - mTouchDownY) > 40 * mDensity)
                        v.cancelLongPress();
                    break;
                case MotionEvent.ACTION_UP:
                    float dy = event.getY() - mTouchDownY;
                    if (!mLongPressed && Math.abs(dy) > 40 * mDensity) {
                        event.setAction(MotionEvent.ACTION_CANCEL);
                        v.onTouchEvent(event);
                        event.setAction(MotionEvent.ACTION_UP);
                        playNextItem(dy > 0);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mLongPressed = true;
                    break;
            }
            return false;
        }
    };

    private final Runnable mDelayHideSystemUiAction = new Runnable() {
        @Override
        public void run() {
            setSystemUiVisible(false);
        }
    };

    private final DrawerLayout.Listener mDrawerListener = new DrawerLayout.Listener() {
        @Override
        public void onDrawerOpened(DrawerLayout layout) {
            ((ViewGroup) mToolbar.getView()).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            focusToCurrentChannel();
            mDrawerLayout.removeCallbacks(mDelayHideSystemUiAction);
            setSystemUiVisible(true);
        }

        @Override
        public void onDrawerClosed(DrawerLayout layout) {
            ((ViewGroup) mToolbar.getView()).setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            mPlayerView.requestFocus();
            mDrawerLayout.postDelayed(mDelayHideSystemUiAction, 1000); // setSystemUiVisible(false);
        }
    };

    private final View.OnFocusChangeListener mFocusListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            float scale = hasFocus ? 1.1f : 1;
            view.animate().scaleX(scale).scaleY(scale).start();
            view.setElevation(hasFocus ? 4 * mDensity : 0);
        }
    };

    private final View.OnKeyListener mKeyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        return playNextItem(false);
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        return playNextItem(true);
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (event.getRepeatCount() == 0) saveSnapshot();
                        return true;
                }
            }
            return false;
        }
    };

    private final Runnable mReplayAction = new Runnable() {
        @Override
        public void run() {
            if (mDrawerLayout.isDrawerOpened())
                mPlayerView.postDelayed(this, REPLAY_DELAY);
            else if (mChannel != null && !isFinishing())
                playChannel(mChannel);
        }
    };

    private final Runnable mUpdateTimeAction = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            mTimeView.setText(DateUtils.formatDateTime(mTimeView.getContext(), now, DateUtils.FORMAT_SHOW_TIME));
            mTimeView.postDelayed(this, 60_000 - now % 60_000);
        }
    };

    private void scheduleTimeUpdate() {
        if (mTimeView != null) {
            mTimeView.removeCallbacks(mUpdateTimeAction);
            mTimeView.post(mUpdateTimeAction);
        }
    }

    private static boolean isHD(String title) {
        return title != null && (title.endsWith("HD") || title.endsWith("高清") || title.endsWith("4K"));
    }

    private static boolean is4K(String title) {
        return title != null && title.endsWith("4K");
    }

    private static abstract class ChannelListener implements PlaylistRepository.Listener, Runnable {
    }
}
