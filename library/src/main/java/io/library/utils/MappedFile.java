package io.library.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;


public class MappedFile {
	private static final int EXPAND_SIZE = 4096;
	private static final int LONG_SIZE = Long.SIZE / Byte.SIZE;

	private FileChannel mChannel;
	private MappedByteBuffer mBuffer;
	private int mExpandSize = EXPAND_SIZE;

	public MappedFile() {
	}

	public void open(File file, boolean readOnly) throws IOException {
		open(file, readOnly, ByteOrder.nativeOrder());
	}

	public void open(File file, boolean readOnly, ByteOrder order) throws IOException {
		if (mChannel != null)
			close();

		mChannel = new RandomAccessFile(file, readOnly ? "r" : "rw").getChannel();
		mBuffer = mChannel.map(readOnly ? MapMode.READ_ONLY : MapMode.READ_WRITE, 0, mChannel.size());
		mBuffer.order(order);
	}

	public void close() {
		mBuffer = null;
		try {
			mChannel.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		mChannel = null;
	}

	public boolean isOpened() {
		return mBuffer != null;
	}

	public int size() {
		MappedByteBuffer buffer = mBuffer;
		return buffer != null ? buffer.limit() : 0;
	}

	public void expand() throws IOException {
		final int pos = mBuffer.position();
		final int size = mBuffer.limit();
		final int newSize = size + mExpandSize;
		final ByteOrder order = mBuffer.order();

		mBuffer.force();
		// re-map the file with expanded size
		final MappedByteBuffer buffer = mBuffer = mChannel.map(MapMode.READ_WRITE, 0, newSize);
		buffer.order(order);
		// write 0 to expanded buffer
		for (int i = size; i < newSize - LONG_SIZE; i += LONG_SIZE)
			buffer.putLong(i, 0);
		for (int i = size + mExpandSize / LONG_SIZE * LONG_SIZE; i < newSize; i++)
			buffer.put(i, (byte) 0);
		buffer.position(pos);
	}

	public void force() {
		MappedByteBuffer buffer = mBuffer;
		if (buffer != null)
			buffer.force();
	}

	public final short readShort(int index) {
		return mBuffer.getShort(index);
	}

	public void writeShort(int index, short value) throws IOException {
		for (;;) {
			try {
				mBuffer.putShort(index, value);
				return;
			} catch (BufferOverflowException e) {
				expand();
			} catch (IndexOutOfBoundsException e) {
				expand();
			}
		}
	}

	public final int readInt(int index) {
		return mBuffer.getInt(index);
	}

	public void writeInt(int index, int value) throws IOException {
		for (;;) {
			try {
				mBuffer.putInt(index, value);
				return;
			} catch (BufferOverflowException e) {
				expand();
			} catch (IndexOutOfBoundsException e) {
				expand();
			}
		}
	}

	public final long readLong(int index) {
		return mBuffer.getLong(index);
	}

	public void writeLong(int index, long value) throws IOException {
		for (;;) {
			try {
				mBuffer.putLong(index, value);
				return;
			} catch (BufferOverflowException e) {
				expand();
			} catch (IndexOutOfBoundsException e) {
				expand();
			}
		}
	}

	public void setExpandSize(int size) {
		size = size / LONG_SIZE * LONG_SIZE;
		mExpandSize = size > 0 ? size : 128;
	}
}
