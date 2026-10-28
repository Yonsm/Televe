package io.library.utils;

import android.content.res.AssetFileDescriptor;
import android.os.Build;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.regex.Pattern;


public class IoUtils {

	public static File changeFileName(File file, boolean add, String prefix, String suffix) {
		String name = file.getName();
		if (add) {
			if (prefix != null && prefix.length() > 0 && !name.startsWith(prefix))
				name = prefix + name;
			if (suffix != null && suffix.length() > 0 && !name.endsWith(suffix))
				name = name + suffix;
		} else {
			if (prefix != null && prefix.length() > 0 && name.startsWith(prefix))
				name = name.substring(prefix.length());
			if (suffix != null && suffix.length() > 0 && name.endsWith(suffix))
				name = name.substring(0, name.length() - suffix.length());
		}
		return new File(file.getParentFile(), name);
	}

	public static void closeSilently(Closeable obj) {
		try {
			obj.close();
		} catch (Exception ignored) {
		}
	}

	public static void closeSilently(AssetFileDescriptor obj) {
		try {
			obj.close();
		} catch (Exception ignored) {
		}
	}

	public static void closeSilently(AutoCloseable obj) {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
				obj.close();
		} catch (Exception ignored) {
		}
	}

	public static void closeSilently(Closeable... objects) {
		for (Closeable obj : objects)
			closeSilently(obj);
	}

	public static void copyFile(File srcFile, File dstFile) throws IOException {
		FileInputStream fis = null;
		FileOutputStream fos = null;
		try {
			File parent = dstFile.getParentFile();
			if (!parent.exists())
				parent.mkdirs();
			fis = new FileInputStream(srcFile);
			fos = new FileOutputStream(dstFile);
			copyFile(fis, fos);
		} finally {
			closeSilently(fos, fis);
		}
	}

	private static final long FILE_COPY_BUFFER_SIZE = 1024 * 1024 * 30;

	public static void copyFile(FileInputStream fis, FileOutputStream fos) throws IOException {
		FileChannel input = null;
		FileChannel output = null;
		try {
			input = fis.getChannel();
			output = fos.getChannel();
			final long size = input.size();
			long pos = 0;
			while (pos < size) {
				final long remain = size - pos;
				long count = remain > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : remain;
				final long bytesCopied = output.transferFrom(input, pos, count);
				if (bytesCopied == 0) { // IO-385 - can happen if file is truncated after caching the size
					break; // ensure we don't loop forever
				}
				pos += bytesCopied;
			}
		} finally {
			closeSilently(output, input);
		}
	}

	public static void deleteDirectory(File dir) {
		final File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory())
					deleteDirectory(file);
				file.delete();
			}
		}
	}

	public static File getAvailableNewFile(File file) {
		return getAvailableNewFile(file, null);
	}

	public static File getAvailableNewFile(File file, String suffix) {
		if (!file.exists())
			return file;

		final File parent = file.getParentFile();
		String title = file.getName();
		int pos = title.lastIndexOf('.');
		if (pos > 0) {
			if (suffix == null)
				suffix = title.substring(pos);
			title = title.substring(0, pos);
		}

		synchronized (IoUtils.class) {
			for (int i = 1; ; i++) {
				final File newFile = new File(parent, title + (i < 10 ? "~0" : "~") + Integer.toString(i) + suffix);
				if (!newFile.exists())
					return newFile;
			}
		}
	}

	public static final Pattern sFileNamePattern = Pattern.compile("[\\\\/:*?\"<>|]");

	public static boolean isValidFileName(CharSequence name) {
		return name != null && !sFileNamePattern.matcher(name).find();
	}

	public static String validateFileName(String name, String extension) {
		if (name == null || name.isEmpty())
			name = "unknown";
		else
			name = sFileNamePattern.matcher(name).replaceAll("_");
		if (extension != null && extension.length() > 0) {
			int pos = name.lastIndexOf('.');
			if (!extension.equalsIgnoreCase(name.substring(pos + 1)))
				name += "." + extension;
		}
		return name;
	}
}
