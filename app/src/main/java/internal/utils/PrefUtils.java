package internal.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


public class PrefUtils {

	public static final String CONFIG_ONLY_HD = "only_hd";
	public static final String CONFIG_PLAYLIST_URL = "playlist_url";
	public static final String TV_ASPECT_RADIO = "tv_aspect_ratio";

	public static final String STATE_CHANNEL_SERVER = "channel_server";
	public static final String STATE_CHANNEL_URL = "channel_url";

	public static SharedPreferences getConfigPref(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context);
	}

	public static SharedPreferences getStatePref(Context context) {
		return context.getSharedPreferences("state", Context.MODE_PRIVATE);
	}
}
