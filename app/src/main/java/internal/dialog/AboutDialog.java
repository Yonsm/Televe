package internal.dialog;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import io.library.widget.PanelDialog;
import net.yonsm.televe.BuildConfig;
import net.yonsm.televe.R;

public class AboutDialog extends AlertDialog {
    private static final String WEB_URL = "https://Yonsm.github.io/Televe";

    public AboutDialog(Context context) {
        super(context);
        setTitle(R.string.about);
        setIcon(R.mipmap.ic_launcher_tv);
        setMessage(context.getString(R.string.app_name) + "\n"
                + context.getString(R.string.about_version, BuildConfig.VERSION_NAME + "." + BuildConfig.BUILD_TIME)
                + "\n\n" + context.getString(R.string.about_message));
        setButton(BUTTON_POSITIVE, WEB_URL, (dialog, which) -> {
            try {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL))
                        .addCategory(Intent.CATEGORY_BROWSABLE));
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        });
        setOnShowListener(dialog -> {
            PanelDialog.immersive(getWindow());
            setCanceledOnTouchOutside(true);
            getButton(BUTTON_POSITIVE).setAllCaps(false);
            getButton(BUTTON_POSITIVE).setSingleLine(false);
        });
        setOnKeyListener(PanelDialog::closeOnKey);
    }
}
