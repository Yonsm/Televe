package internal.utils;

import android.content.Context;
import android.content.SharedPreferences;


public class PrefUtils {

	public static final String CONFIG_PLAYLIST_URL = "playlist_url";
	public static final String TV_ASPECT_RATIO = "aspect_ratio";

	public static final String STATE_CHANNEL_SERVER = "channel_server";
	public static final String STATE_CHANNEL_URL = "channel_url";

	public static SharedPreferences getConfigPref(Context context) {
		return context.getSharedPreferences("config", Context.MODE_PRIVATE);
	}

	public static SharedPreferences getStatePref(Context context) {
		return context.getSharedPreferences("playback", Context.MODE_PRIVATE);
	}
}
