package internal.tv;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.util.Log;

import net.yonsm.televe.R;
import net.yonsm.televe.SimpleFileProvider;
import net.yonsm.televe.TvActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import internal.playlist.Channel;
import io.library.net.HttpCache;
import io.library.utils.DiskCache;
import io.library.utils.IoUtils;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class TvChannelPublisher extends ChannelPublisher {
    private static final String TAG = "ChannelPublisher";
    private static final String TV_SNAPSHOT_ID = "tv_snapshot_id";

    public TvChannelPublisher(Context context) {
        super(context, R.mipmap.ic_launcher_tv, R.string.app_name);
    }

    public Class<?> getActivityClass() {
        return TvActivity.class;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void updateWatchNextProgram(Channel bean, Bitmap snapshot) {
        long now = System.currentTimeMillis();
        DiskCache diskCache = HttpCache.getDiskCache(mContext);
        if (!diskCache.isOpened()) diskCache.open();
        long key = DiskCache.hash64(bean.url + now);
        File file = diskCache.getCacheFile(key);
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            snapshot.compress(Bitmap.CompressFormat.JPEG, 90, os);
        } catch (Exception e) {
            Log.e(TAG, "save snapshot failed", e);
            return;
        } finally {
            IoUtils.closeSilently(os);
            diskCache.updateCache(key);
        }

        Intent intent = createIntent(bean);
        ContentValues values = new ContentValues(10);
        values.put(TvContract.WatchNextPrograms.COLUMN_CONTENT_ID, TV_SNAPSHOT_ID);
        values.put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, intent.toUri(Intent.URI_INTENT_SCHEME));
        values.put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, TV_SNAPSHOT_ID);
        values.put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, now);
        values.put(TvContract.WatchNextPrograms.COLUMN_LIVE, true);
        values.put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, SimpleFileProvider.encodeUri(file).toString());
        values.put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.WatchNextPrograms.ASPECT_RATIO_16_9);
        values.put(TvContract.WatchNextPrograms.COLUMN_TITLE, bean.title);
        values.put(TvContract.WatchNextPrograms.COLUMN_TYPE, TvContract.WatchNextPrograms.TYPE_CLIP);
        values.put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);

        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = null;
        long programId = -1L;
        try {
            String[] columns = { TvContract.WatchNextPrograms._ID, TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID };
            cursor = resolver.query(TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null);
            if (cursor != null) {
                while ((cursor.moveToNext())) {
                    if (TV_SNAPSHOT_ID.equals(cursor.getString(1))) {
                        programId = cursor.getLong(0);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "query watch next failed", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        if (programId != -1L)
            resolver.update(TvContract.buildWatchNextProgramUri(programId), values, null, null);
        else
            resolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, values);
    }
}
