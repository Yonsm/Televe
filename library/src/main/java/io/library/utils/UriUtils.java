package io.library.utils;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.net.Uri;

import java.io.File;

public class UriUtils {

	public static final String SCHEME_CONTENT = ContentResolver.SCHEME_CONTENT;
	public static final String SCHEME_FILE = ContentResolver.SCHEME_FILE;
	public static final String SCHEME_RESOURCE = ContentResolver.SCHEME_ANDROID_RESOURCE;
	public static final String SCHEME_PACKAGE = "package";

	public static boolean isLocalUrl(String url) {
		return url != null && (url.startsWith("/")
				|| url.startsWith(SCHEME_CONTENT)
				|| url.startsWith(SCHEME_FILE)
				|| url.startsWith(SCHEME_RESOURCE)
				|| url.startsWith(SCHEME_PACKAGE));
	}

	public static boolean isLocalUri(Uri uri) {
		return uri != null && isLocalUrl(uri.toString());
	}

	public static boolean isWebUri(Uri uri) {
		final String scheme = uri != null ? uri.getScheme() : "";
		return "http".equals(scheme)
				|| "https".equals(scheme)
				|| "smb".equals(scheme);
	}

	public static Uri parseUrl(String url) {
		if (url == null)
			return Uri.EMPTY;
		else if (url.startsWith("/"))
			return Uri.fromFile(new File(url));
		else
			return Uri.parse(url);
	}

	public static Uri fromResourceId(Resources res, int id) {
		return new Uri.Builder()
				.scheme(SCHEME_RESOURCE)
				.authority(res.getResourcePackageName(id))
				.appendEncodedPath(res.getResourceTypeName(id))
				.appendEncodedPath(res.getResourceEntryName(id)).build();
	}

	public static Uri fromPackageName(String pkgName) {
		return new Uri.Builder()
				.scheme(SCHEME_PACKAGE)
				.authority(pkgName).build();
	}

	public static String pathToUrl(String path) {
		return SCHEME_FILE + "://" + path;
	}
}
