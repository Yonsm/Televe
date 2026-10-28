package internal.tv;

import android.annotation.TargetApi;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import net.yonsm.televe.R;
import net.yonsm.televe.SimpleFileProvider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;

import internal.playlist.Channel;
import io.library.image.ImageDrawable;
import io.library.image.ImageResolver;
import io.library.utils.IoUtils;
import io.library.utils.Size;
import io.library.utils.UriUtils;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public abstract class ChannelPublisher {
    private static final String TAG = "ChannelPublisher";

    private final int mIconResId;
    private final int mTitleResId;
    private final int mCardWidth;
    private final int mCardHeight;
    protected final Context mContext;

    private long mChannelId = -1L;
    private float mDensity;
    private NotificationManager mNotificationManager;

    public ChannelPublisher(Context context, int iconResId, int titleResId) {
        mIconResId = iconResId;
        mTitleResId = titleResId;
        mContext = context.getApplicationContext();

        mDensity = mContext.getResources().getDisplayMetrics().density;
        mCardWidth = Math.round(mDensity * 156);
        mCardHeight = Math.round(mDensity * 88);
    }

    public abstract Class<?> getActivityClass();

    public void publish(Channel... beans) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createChannel();

        for (Channel bean : beans) {
            createProgram(bean);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void createChannel() {
        Intent intent = new Intent(mContext, getActivityClass());
        String title = mContext.getString(mTitleResId);
        ContentValues values = new ContentValues(4);
        values.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, intent.toUri(Intent.URI_INTENT_SCHEME));
        values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, title);
        values.put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW);

        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = null;
        try {
            String[] columns = { TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME };
            cursor = resolver.query(TvContract.Channels.CONTENT_URI, columns, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (title.equals(cursor.getString(1))) {
                        mChannelId = cursor.getLong(0);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null)
                cursor.close();
        }

        if (mChannelId == -1L) {
            Uri channelUri = resolver.insert(TvContract.Channels.CONTENT_URI, values);
            mChannelId = ContentUris.parseId(channelUri);

            // Store icon for channel
            Bitmap bitmap = null;
            OutputStream outStream = null;
            try {
                outStream = new BufferedOutputStream(resolver.openOutputStream(TvContract.buildChannelLogoUri(mChannelId)));
                int size = ((ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE)).getLauncherLargeIconSize() * 2; // *2 to make clear when become large
                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Drawable icon = mContext.getResources().getDrawable(mIconResId).mutate();
                icon.setBounds(0, 0, size, size);
                icon.draw(canvas);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            } catch (Throwable e) {
                Log.e(TAG, "store channel icon failed", e);
            } finally {
                IoUtils.closeSilently(outStream);
                if (bitmap != null)
                    bitmap.recycle();
            }
        }
        TvContract.requestChannelBrowsable(mContext, mChannelId);
    }

    protected Intent createIntent(Channel channel) {
        return new Intent(mContext, getActivityClass())
                .setDataAndType(Uri.parse(channel.url), "video/*")
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_TITLE, channel.title)
                .putExtra(Channel.EXTRA_CHANNEL, channel);
    }

    protected void createProgram(Channel bean) {
        Uri iconUri = null;
        if (!Channel.isEmpty(bean.thumbnail)) {
            Uri thumbUri = Uri.parse(bean.thumbnail);
            if (UriUtils.isWebUri(thumbUri)) {
                try {
                    Future<File> future = ImageResolver.getInstance(mContext).downloadUri(thumbUri, null, null);
                    File file = future.get();
                    iconUri = SimpleFileProvider.encodeUri(file);
                } catch (Throwable e) {
                    Log.e(TAG, "load failed: " + bean.thumbnail, e);
                }
            } else {
                 // iconUri = thumbUri;
            }
        }

        try {
            if (iconUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int iconId = mIconResId;
                File file = createIconFileByTitle(bean, iconId);
                if (file != null) iconUri = SimpleFileProvider.encodeUri(file);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                updateProgramForTvChannel(bean, iconUri);
            } else {
                updateProgramForNotification(bean, iconUri);
            }
        } catch (Throwable e) {
            Log.e(TAG, "update failed: " + bean.thumbnail, e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private File createIconFileByTitle(Channel bean, int iconId) {
        File file = null;
        Bitmap bitmap = null;
        OutputStream outStream = null;
        try {
            File directory = new File(mContext.getCacheDir(), "tv_artwork");
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            file = File.createTempFile("logo", ".jpg", directory);
            outStream = new BufferedOutputStream(new FileOutputStream(file));

            bitmap = Bitmap.createBitmap(mCardWidth, mCardHeight, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            bitmap.eraseColor(Color.rgb(66,66,66));

            float textSize = 30;
            int maxLines = 2;
            int padding = Math.round(mDensity * 8);
            int iconSize = Math.round(mCardHeight - padding * 3 - textSize * maxLines);

            int left = (mCardWidth - iconSize) / 2;
            int top = padding;
            Drawable icon = mContext.getResources().getDrawable(iconId).mutate();
            icon.setBounds(left, top, left + iconSize, top + iconSize);
            icon.draw(canvas);

            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTextSize(textSize);

            String title = bean.title;
            StaticLayout.Builder builder = StaticLayout.Builder.obtain(title, 0, title.length(), paint, mCardWidth - padding * 2);
            builder.setAlignment(Layout.Alignment.ALIGN_CENTER);
            builder.setMaxLines(maxLines);
            canvas.translate(padding, top + iconSize + padding);
            builder.build().draw(canvas);

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
        } catch (Throwable e) {
            Log.e(TAG, "create failed", e);
        } finally {
            IoUtils.closeSilently(outStream);
            if (bitmap != null)
                bitmap.recycle();
        }
        return file;
    }

    private Bitmap createLargeIcon(Uri uri) {
        Bitmap result = null;
        ImageDrawable image = null;
        try {
            image = ImageDrawable.decodeImage(mContext, uri, 0, new Size(mCardWidth, mCardHeight), null);

            result = Bitmap.createBitmap(mCardWidth, mCardHeight, Bitmap.Config.RGB_565);
            result.eraseColor(Color.WHITE);

            Canvas canvas = new Canvas(result);
            Matrix matrix = new Matrix();
            image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
            image.getBaseMatrix(matrix);
            image.getCenterOrCropMatrix(matrix, mCardWidth, mCardHeight, true);
            canvas.concat(matrix);
            image.draw(canvas);
        } catch (Throwable e) {
            Log.e(TAG, "create failed", e);
        } finally {
            if (image != null)
                image.release();
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void updateProgramForTvChannel(Channel bean, Uri iconUri) {
        Intent intent = createIntent(bean);
        ContentValues values = new ContentValues(10);
        values.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, mChannelId);
        values.put(TvContract.PreviewPrograms.COLUMN_CONTENT_ID, bean.id);
        values.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, intent.toUri(Intent.URI_INTENT_SCHEME));
        values.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, bean.id);
        values.put(TvContract.PreviewPrograms.COLUMN_LIVE, true);
        values.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9);
        values.put(TvContract.PreviewPrograms.COLUMN_TITLE, bean.title);
        values.put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_CLIP);

        String artUrl = iconUri != null ? iconUri.toString() : null;
        if (iconUri != null)
            values.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, artUrl);

        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = null;
        long programId = -1L;
        try {
            String[] columns = { TvContract.PreviewPrograms._ID, TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID };
            cursor = resolver.query(TvContract.buildPreviewProgramsUriForChannel(mChannelId), columns, null, null);
            if (cursor != null) {
                while ((cursor.moveToNext())) {
                    if (bean.id.equals(cursor.getString(1))) {
                        programId = cursor.getLong(0);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "query program failed", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        if (programId != -1L)
            resolver.delete(TvContract.buildPreviewProgramUri(programId), null, null);
        resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values);
        Log.d(TAG, "update program: " + bean.title);
    }

    // Only Android 7 uses notification recommendations; newer systems use TvContract.
    @SuppressLint("NotificationPermission")
    private void updateProgramForNotification(Channel bean, Uri iconUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return;
        Bitmap icon = createLargeIcon(iconUri);
        Intent intent = createIntent(bean);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(mContext)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setContentInfo(bean.title)
                .setContentTitle(bean.title)
                .setContentIntent(pendingIntent)
                .setGroup(mContext.getString(mTitleResId))
                .setLargeIcon(icon)
                .setLocalOnly(true)
                .setOngoing(true)
                .setPriority(Integer.MAX_VALUE - bean.order)
                .setSmallIcon(R.drawable.ic_logo_tv);

        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(bean.hashCode(), builder.build());
        Log.d(TAG, "notify program: " + bean.title);
    }
}
