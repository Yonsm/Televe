package io.library.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;

/** An independent side window keeps controls above hardware video overlays. */
public class PanelDialog extends Dialog {
    private static final SparseBooleanArray sRepeatedKeys = new SparseBooleanArray();
    protected final View mPanel;
    private final boolean mRight;
    private int mWidth;
    private float mStartX;
    private float mStartY;
    private int mSwipeAxis;
    private boolean mNavigationGesture;
    private Runnable mOnOppositeSwipe;

    public PanelDialog(Context context, View panel, boolean right, int width) {
        super(context);
        mPanel = panel;
        mRight = right;
        mWidth = width;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(panel);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setGravity(Gravity.TOP | (right ? Gravity.RIGHT : Gravity.LEFT));
        window.setWindowAnimations(0);
        immersive(window);
        window.getDecorView().setPadding(0, 0, 0, 0);
        setCanceledOnTouchOutside(true);
    }

    public void setOnOppositeSwipe(Runnable action) {
        mOnOppositeSwipe = action;
    }

    public void setPanelWidth(int width) {
        mWidth = width;
        getWindow().setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void show() {
        setPanelWidth(mWidth);
        super.show();
    }

    public static void immersive(Window window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_LAYOUT_FLAGS | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // Some senders report key repeats without a reliable release timestamp.
    public static void noteKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return;
        if (event.getRepeatCount() > 0 || event.isLongPress()) sRepeatedKeys.put(event.getKeyCode(), true);
        else sRepeatedKeys.delete(event.getKeyCode());
    }

    public static boolean isShortPress(KeyEvent event) {
        return !event.isCanceled() && !sRepeatedKeys.get(event.getKeyCode())
                && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout();
    }

    public static boolean closeOnKey(DialogInterface dialog, int key, KeyEvent event) {
        if (key != KeyEvent.KEYCODE_ESCAPE && key != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) dialog.dismiss();
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return closeOnKey(this, event.getKeyCode(), event) || super.dispatchKeyEvent(event);
    }

    public static int swipeAxis(Context context, float dx, float dy) {
        float threshold = 40 * context.getResources().getDisplayMetrics().density;
        if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy) * 2) return View.SCROLL_AXIS_HORIZONTAL;
        if (Math.abs(dy) > threshold && Math.abs(dy) > Math.abs(dx) * 2) return View.SCROLL_AXIS_VERTICAL;
        return View.SCROLL_AXIS_NONE;
    }

    public static boolean isNavigationEdge(View view, MotionEvent event) {
        android.graphics.Point size = new android.graphics.Point();
        view.getDisplay().getRealSize(size);
        int edge = (int) (24 * view.getResources().getDisplayMetrics().density);
        float x = event.getRawX(), y = event.getRawY();
        if (x < edge || x >= size.x - edge || y < edge || y >= size.y - edge) return true;
        View root = view.getRootView();
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
                || root.getRootWindowInsets() == null) return false;
        android.graphics.Insets insets = root.getRootWindowInsets().getSystemGestureInsets();
        return x < insets.left || x >= size.x - insets.right
                || y < insets.top || y >= size.y - insets.bottom;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float dx = event.getX() - mStartX, dy = event.getY() - mStartY;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mNavigationGesture = isNavigationEdge(mPanel, event);
            mStartX = event.getX();
            mStartY = event.getY();
            mSwipeAxis = View.SCROLL_AXIS_NONE;
        } else if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mSwipeAxis = View.SCROLL_AXIS_VERTICAL;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && mSwipeAxis == View.SCROLL_AXIS_NONE) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (mNavigationGesture && Math.max(Math.abs(dx), Math.abs(dy)) > slop
                    || Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 2) {
                mSwipeAxis = View.SCROLL_AXIS_HORIZONTAL;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            } else if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) mSwipeAxis = View.SCROLL_AXIS_VERTICAL;
        }
        if (mSwipeAxis == View.SCROLL_AXIS_HORIZONTAL) {
            if (!mNavigationGesture && event.getActionMasked() == MotionEvent.ACTION_UP
                    && swipeAxis(getContext(), dx, dy) == View.SCROLL_AXIS_HORIZONTAL) {
                if (mRight ? dx > 0 : dx < 0) dismiss();
                else if (mOnOppositeSwipe != null) mOnOppositeSwipe.run();
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
}
