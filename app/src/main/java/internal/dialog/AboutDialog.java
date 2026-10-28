package internal.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.text.util.Linkify;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.yonsm.televe.R;

public class AboutDialog extends AlertDialog {

	/** 代码库网站(about_message 文本末尾亦展示此链接)。 */
	private static final String REPO_URL = "https://github.com/Yonsm/Televe";

	public AboutDialog(Context context) {
		super(context);

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

		LinearLayout layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.VERTICAL);

		// 顶部一行：左侧应用图标，右侧应用名 + 版本
		LinearLayout header = new LinearLayout(context);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding((int) (density * 20), (int) (density * 10), (int) (density * 20), 0);

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
		Linkify.addLinks(textView, Linkify.WEB_URLS);
		textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
		textView.setPadding((int) (density * 20), (int) (density * 12), (int) (density * 20), 0);
		layout.addView(textView);

		// 代码库链接(隔一行、链接色)，点击打开
		final TextView linkView = new TextView(context);
		linkView.setText(REPO_URL);
		linkView.setTextColor(context.getResources().getColor(R.color.colorAccent));
		linkView.setPaintFlags(linkView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
		linkView.setPadding((int) (density * 20), (int) (density * 16), (int) (density * 20), 0);
		linkView.setOnClickListener(v -> {
			try {
				context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)));
			} catch (Exception ignored) { // 设备可能没有浏览器
			}
		});
		layout.addView(linkView);

		setView(layout);

		setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), (OnClickListener) null);
	}
}
