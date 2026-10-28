package net.yonsm.televe;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only access to artwork used by the television launcher. */
public class SimpleFileProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".SimpleFileProvider";

    public static Uri encodeUri(File file) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY).appendPath(file.getAbsolutePath()).build();
    }

    private File artwork(Uri uri) throws FileNotFoundException {
        try {
            if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPath() == null) {
                throw new IOException("Invalid artwork URI");
            }
            File file = new File(uri.getPath()).getCanonicalFile();
            File parent = file.getParentFile();
            File cache = getContext().getCacheDir();
            File external = getContext().getExternalCacheDir();
            boolean generated = new File(cache, "tv_artwork").getCanonicalFile().equals(parent)
                    && file.getName().endsWith(".jpg");
            boolean downloaded = file.getName().matches("[0-9a-f]+")
                    && (new File(cache, "http_cache").getCanonicalFile().equals(parent)
                    || (external != null && new File(external, "http_cache").getCanonicalFile().equals(parent)));
            if (!generated && !downloaded) throw new IOException("Not television artwork");
            return file;
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "image/*";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Artwork is read-only");
        return ParcelFileDescriptor.open(artwork(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        if (projection == null) projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(projection, 1);
        try {
            File file = artwork(uri);
            Object[] values = new Object[projection.length];
            for (int i = 0; i < projection.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) values[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(projection[i])) values[i] = file.length();
            }
            cursor.addRow(values);
        } catch (FileNotFoundException ignored) {
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Artwork is read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("Artwork is read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("Artwork is read-only");
    }
}
