package io.library.utils;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

public class DisplayUtils {

    public static DisplayMetrics getDisplayMetrics(Context context) {
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            display.getRealMetrics(metrics);
        else
            display.getMetrics(metrics);
        return metrics;
    }

    public static Size getDisplaySize(Context context) {
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final Point point = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            display.getRealSize(point);
        else
            display.getSize(point);
        return new Size(point.x, point.y);
    }
}
