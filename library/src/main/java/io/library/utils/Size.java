package io.library.utils;

import android.os.Parcel;
import android.os.Parcelable;


public class Size implements Parcelable {
	public static final int ROUND_SIDE = 128;

	public int width;
	public int height;

	public Size() {
	}

	public Size(int width, int height) {
		this.width = width;
		this.height = height;
	}

	protected Size(Parcel in) {
		width = in.readInt();
		height = in.readInt();
	}

	public int min() {
		return Math.min(width, height);
	}

	public int max() {
		return Math.max(width, height);
	}

	public int computeSampleSize(int cx, int cy) {
		if (width <= 0 && height <= 0)
			return 1;

		int w = cx;
		int h = cy;
		int sample = 1;
		while ((width <= 0 || w >= width) && (height <= 0 || h >= height)) {
			sample <<= 1;
			w >>>= 1;
			h >>>= 1;
		}
		return sample <= 1 ? 1 : sample >> 1;
	}

	public float computeCropScale(int cx, int cy) {
		float scaleX = width / (float) cx;
		float scaleY = height / (float) cy;
		return Math.max(scaleX, scaleY);
	}

	public String getKey() {
		int w = round(width);
		int h = round(height);
		return Integer.toString(w) + "x" + h;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Size)) {
			return false;
		}
		Size that = (Size) obj;
		return width == that.width && height == that.height;
	}

	@Override
	public int hashCode() {
		return width * 31 + height;
	}

	@Override
	public String toString() {
		return Integer.toString(width) + "x" + height;
	}

	public static int round(int side) {
		return (side + ROUND_SIDE - 1) / ROUND_SIDE * ROUND_SIDE;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(width);
		dest.writeInt(height);
	}

	public static final Creator<Size> CREATOR = new Creator<Size>() {
		@Override
		public Size createFromParcel(Parcel in) {
			return new Size(in);
		}

		@Override
		public Size[] newArray(int size) {
			return new Size[size];
		}
	};
}
