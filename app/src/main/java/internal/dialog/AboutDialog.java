package internal.dialog;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.yonsm.televe.R;

public class AboutDialog extends AlertDialog {

	private static final String REPO_URL = "https://github.com/Yonsm/Televe";

	public AboutDialog(Context context) {
		super(context);
		context = getContext();

		setTitle(context.getString(R.string.about));

		String version = "";
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			version = info.versionName;
		} catch (PackageManager.NameNotFoundException ignored) {
		}
		if (version == null)
			version = "";

		float density = context.getResources().getDisplayMetrics().density;
		int padding = Math.round(density * 24);

		LinearLayout layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(0, 0, 0, padding);

		// 顶部一行：左侧应用图标，右侧应用名 + 版本
		LinearLayout header = new LinearLayout(context);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(padding, (int) (density * 16), padding, 0);

		ImageView icon = new ImageView(context);
		icon.setImageResource(R.mipmap.ic_launcher_tv);
		int iconSize = (int) (density * 56);
		header.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

		TextView titleView = new TextView(context);
		titleView.setText(context.getString(R.string.app_name) + "\n" + context.getString(R.string.about_version, version));
		titleView.setPadding((int) (density * 16), 0, 0, 0);
		titleView.setGravity(Gravity.CENTER_VERTICAL);
		header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		layout.addView(header);

		// 描述
		final TextView textView = new TextView(context);
		textView.setText(context.getString(R.string.about_message));
		textView.setPadding(padding, (int) (density * 16), padding, 0);
		layout.addView(textView);

		// 代码库链接(隔一行、链接色)，点击打开
		final TextView linkView = new TextView(context);
		linkView.setText(REPO_URL);
		linkView.setTextColor(context.getResources().getColor(R.color.colorAccent));
		linkView.setPaintFlags(linkView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
		linkView.setPadding(padding, (int) (density * 16), padding, (int) (density * 16));
		linkView.setMinimumHeight(Math.round(density * 48));
		linkView.setGravity(Gravity.CENTER_VERTICAL);
		linkView.setFocusable(true);
		TypedArray background = context.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
		linkView.setBackground(background.getDrawable(0));
		background.recycle();
		linkView.setOnClickListener(v -> {
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
						.addCategory(Intent.CATEGORY_BROWSABLE);
				getContext().startActivity(intent);
			} catch (ActivityNotFoundException | SecurityException error) {
				Toast.makeText(getContext(), R.string.browser_unavailable, Toast.LENGTH_SHORT).show();
			}
		});
		layout.addView(linkView);

		ScrollView scroll = new ScrollView(context);
		scroll.setFocusableInTouchMode(true);
		scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
		scroll.addView(layout, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		setView(scroll);
		setOnShowListener(dialog -> scroll.requestFocus());

		setCanceledOnTouchOutside(true);
	}
}
