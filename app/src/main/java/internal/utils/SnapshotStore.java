package internal.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.yonsm.televe.R;

public final class SnapshotStore {
    private SnapshotStore() {}

    public static void save(Context context, Bitmap bitmap, String name) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + context.getString(R.string.app_name));
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Cannot create snapshot");
            try {
                try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
                    if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                        throw new IOException("Cannot write snapshot");
                    }
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);
            } catch (IOException | RuntimeException error) {
                context.getContentResolver().delete(uri, null, null);
                throw error;
            }
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), context.getString(R.string.app_name));
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create snapshot directory");
            File file = new File(directory, name);
            try (OutputStream stream = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) throw new IOException("Cannot write snapshot");
            } catch (IOException | RuntimeException error) {
                file.delete();
                throw error;
            }
            MediaScannerConnection.scanFile(context, new String[]{file.getPath()}, new String[]{"image/jpeg"}, null);
        }
    }
}
