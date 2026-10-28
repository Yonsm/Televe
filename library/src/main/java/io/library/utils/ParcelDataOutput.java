package io.library.utils;

import android.os.Parcel;

import java.io.DataOutput;
import java.io.IOException;


public class ParcelDataOutput implements DataOutput {
	private final Parcel mParcel;

	public ParcelDataOutput(Parcel parcel) {
		mParcel = parcel;
	}

	@Override
	public void write(int b) throws IOException {
		mParcel.writeByte((byte) b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		mParcel.writeByteArray(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		mParcel.writeByteArray(b, off, len);
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
		mParcel.writeByte(v ? (byte)1 : 0);
	}

	@Override
	public void writeByte(int v) throws IOException {
		mParcel.writeByte((byte)v);
	}

	@Override
	public void writeShort(int v) throws IOException {
		mParcel.writeInt(v);
	}

	@Override
	public void writeChar(int v) throws IOException {
		mParcel.writeInt(v);
	}

	@Override
	public void writeInt(int v) throws IOException {
		mParcel.writeInt(v);
	}

	@Override
	public void writeLong(long v) throws IOException {
		mParcel.writeLong(v);
	}

	@Override
	public void writeFloat(float v) throws IOException {
		mParcel.writeFloat(v);
	}

	@Override
	public void writeDouble(double v) throws IOException {
		mParcel.writeDouble(v);
	}

	@Override
	public void writeBytes(String s) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeChars(String s) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeUTF(String s) throws IOException {
		mParcel.writeString(s);
	}
}
