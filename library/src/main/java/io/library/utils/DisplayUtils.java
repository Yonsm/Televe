package io.library.utils;

import android.content.Context;
import android.graphics.Point;
import android.view.WindowManager;

public class DisplayUtils {
    public static Point getDisplaySize(Context context) {
        Point size = new Point();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(size);
        return size;
    }
}
