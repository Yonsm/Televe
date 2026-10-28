package io.library.utils;

import android.os.Parcel;

import java.io.DataInput;
import java.io.IOException;


public class ParcelDataInput implements DataInput {
	private final Parcel mParcel;

	public ParcelDataInput(Parcel parcel) {
		mParcel = parcel;
	}

	@Override
	public void readFully(byte[] b) throws IOException {
		mParcel.readByteArray(b);
	}

	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		byte[] data = new byte[len];
		mParcel.readByteArray(data);
		System.arraycopy(data, 0, b, off, len);
	}

	@Override
	public int skipBytes(int n) throws IOException {
		mParcel.setDataPosition(mParcel.dataPosition() + n);
		return n;
	}

	@Override
	public boolean readBoolean() throws IOException {
		return mParcel.readByte() != 0;
	}

	@Override
	public byte readByte() throws IOException {
		return mParcel.readByte();
	}

	@Override
	public int readUnsignedByte() throws IOException {
		return mParcel.readInt();
	}

	@Override
	public short readShort() throws IOException {
		return (short) mParcel.readInt();
	}

	@Override
	public int readUnsignedShort() throws IOException {
		return mParcel.readInt();
	}

	@Override
	public char readChar() throws IOException {
		return (char) mParcel.readInt();
	}

	@Override
	public int readInt() throws IOException {
		return mParcel.readInt();
	}

	@Override
	public long readLong() throws IOException {
		return mParcel.readLong();
	}

	@Override
	public float readFloat() throws IOException {
		return mParcel.readFloat();
	}

	@Override
	public double readDouble() throws IOException {
		return mParcel.readDouble();
	}

	@Override
	public String readLine() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String readUTF() throws IOException {
		return mParcel.readString();
	}
}
