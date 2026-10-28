package io.library.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;


public abstract class PostBody {
	public abstract long contentLength();
	public abstract void writeTo(OutputStream os) throws Exception;

	public static PostBody fromByteArray(final byte[] data) {
		return fromByteArray(data, 0, data.length);
	}

	public static PostBody fromByteArray(final byte[] data, final int offset, final int size) {
		return new PostBody() {
			@Override
			public long contentLength() {
				return size;
			}

			@Override
			public void writeTo(OutputStream os) throws IOException {
				os.write(data, offset, size);
			}
		};
	}

	public static PostBody fromString(String str) {
		return fromString(str, HttpClient.UTF8_CHARSET);
	}

	public static PostBody fromString(String str, Charset charset) {
		final byte[] data = str.getBytes(charset);
		return new PostBody() {
			@Override
			public long contentLength() {
				return data.length;
			}

			@Override
			public void writeTo(OutputStream os) throws IOException {
				os.write(data);
			}
		};
	}

	public static PostBody fromFile(final File file) {
		return new PostBody() {
			@Override
			public long contentLength() {
				return file.length();
			}

			@Override
			public void writeTo(OutputStream os) throws IOException {
				FileInputStream fis = null;
				try {
					fis = new FileInputStream(file);
					HttpClient.copyStream(fis, os, file.length(), null);
				} finally {
					HttpClient.closeSilently(fis);
				}
			}
		};
	}
}
