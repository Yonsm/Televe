package io.library.utils;

import android.util.Log;
import android.util.LongSparseArray;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 *
 * A simple and fast disk cache with long type key
 */

public class DiskCache {
	private static final String TAG = "DiskCache";
	private static final String INDEX_NAME = "index.m";
	private static final String SUFFIX_HEAD = ".h";
	private static final String SUFFIX_PATH = ".part";

	// 8 for key, 8 for file size, 8 for access time
	private static final int LONG_BYTES = Long.SIZE / Byte.SIZE;
	private static final int OFFSET_SIZE = LONG_BYTES;
	private static final int OFFSET_TIME = OFFSET_SIZE + LONG_BYTES;
	private static final int ITEM_BYTES = OFFSET_TIME + LONG_BYTES;

	private int mMaxPosition;
	private final File mDir;
	private final Object mLock = new Object();
	private final LruHashMap<Long, Integer> mIndex;
	private final LinkedHashSet<Integer> mRecycledPositions = new LinkedHashSet<>();
	private final MappedFile mIndexFile = new MappedFile();

	public DiskCache(File dir, long maxSize) {
		mDir = dir;
		mIndexFile.setExpandSize(ITEM_BYTES * 128);
		mIndex = new LruHashMap<Long, Integer>((int) (maxSize / 128 / 1024), maxSize) {
//			@Override
//			public long trimToSize(long maxSize) {
//				final long trimmed = super.trimToSize(maxSize);
//				if (trimmed > 0)
//					Log.d(TAG, "trimmed=" + trimmed + ", size=" + super.size() + ", count=" + super.count());
//				return trimmed;
//			}

			@Override
			protected void onRemoved(Long key, Integer position, boolean replace) {
				final String sk = Long.toHexString(key);
				synchronized (mLock) {
					try {
						// write 0 to mark it as deleted
						mIndexFile.writeLong(position, 0);
						mIndexFile.writeLong(position + OFFSET_SIZE, 0);
						mIndexFile.writeLong(position + OFFSET_TIME, 0);
						mIndexFile.force();
						mRecycledPositions.add(position);
					} catch (Exception e) {
						Log.e(TAG, sk + " write failed", e);
					}
				}
				if (!replace) {
					final File fileCache = getCacheFile(sk);
					final File filePart = getPartFile(sk);
					final File fileHead = getHeadFile(sk);
					boolean deleted = true;
					if (fileCache.exists())
						deleted &= fileCache.delete();
					if (filePart.exists())
						deleted &= filePart.delete();
					if (fileHead.exists())
						deleted &= fileHead.delete();
					if (!deleted)
						Log.e(TAG, sk + " delete failed");
				}
			}

			@Override
			protected long sizeOf(Long key, Integer position) {
				synchronized (mLock) {
					return mIndexFile.readLong(position + OFFSET_SIZE);
				}
			}
		};
	}

	public int count() {
		return mIndex.count();
	}

	public long size() {
		return mIndex.size() + mIndexFile.size();
	}

	public boolean isOpened() {
		synchronized (mLock) {
			return mIndexFile.isOpened() && mDir.exists();
		}
	}

	public void open() {
		synchronized (mLock) {
			if (mIndexFile.isOpened() && mDir.exists())
				return;

			try {
				final long ms = System.currentTimeMillis();
				if (!mDir.exists())
					mDir.mkdirs();

				final File file = new File(mDir, INDEX_NAME);
				mIndexFile.open(file, false);
				mRecycledPositions.clear();

				int position = 0;
				final int length = mIndexFile.size();
				final LongSparseArray<Long> keys = new LongSparseArray<>(length / ITEM_BYTES);
				while (position + ITEM_BYTES <= length) {
					final long key = mIndexFile.readLong(position);
					final long size = mIndexFile.readLong(position + OFFSET_SIZE);
					final long time = mIndexFile.readLong(position + OFFSET_TIME);
					if (size != 0) {
						keys.put(time, key); // sorted by access time
						mIndex.put(key, position, size);
					} else {
						// size is 0, it was marked as deleted
						mRecycledPositions.add(position);
					}
					position += ITEM_BYTES;
				}
				mMaxPosition = position;

				// resort the access order
				final int count = keys.size();
				for (int i = 0; i < count; i++)
					mIndex.get(keys.valueAt(i));

				Log.d(TAG, "open " + mIndex.count() + " entries, " + mIndex.size() + " bytes, "
						+ (System.currentTimeMillis() - ms) + " ms");
			} catch (Throwable e) {
				Log.e(TAG, "open failed: ", e);
			}
		}
	}

	public void close() {
		synchronized (mLock) {
			mIndex.clear();
			mIndexFile.close();
		}
	}

	public void clear() {
		synchronized (mLock) {
			final boolean opened = isOpened();
			close();
			deleteDirectory(mDir);
			if (opened)
				open();
		}
	}

	public File getCacheFile(long key) {
		return getCacheFile(Long.toHexString(key));
	}

	protected File getCacheFile(String sk) {
		return new File(mDir, sk);
	}

	public File getHeadFile(long key) {
		return getHeadFile(Long.toHexString(key));
	}

	protected File getHeadFile(String sk) {
		return new File(mDir, sk + SUFFIX_HEAD);
	}

	public File getPartFile(long key) {
		return getPartFile(Long.toHexString(key));
	}

	protected File getPartFile(String sk) {
		return new File(mDir, sk + SUFFIX_PATH);
	}

	public File accessCache(long key) {
		final String sk = Long.toHexString(key);
		final File fileCache = getCacheFile(sk);
		if (fileCache.length() > 0) {
			synchronized (mLock) {
				final Integer position = mIndex.get(key);
				if (position != null) {
					try {
						mIndexFile.writeLong(position + OFFSET_TIME, System.currentTimeMillis());
						//mIndexFile.force();
					} catch (Exception e) {
						Log.e(TAG, sk + " write failed", e);
					}
				}
			}
		}
		return fileCache;
	}

	public void updateCache(long key) {
		synchronized (mLock) {
			try {
				long size = getSizeOfEntry(key);
				Integer position = getAvailablePosition();
				mIndexFile.writeLong(position, key);
				mIndexFile.writeLong(position + OFFSET_SIZE, size);
				mIndexFile.writeLong(position + OFFSET_TIME, System.currentTimeMillis());
				mIndexFile.force();
				mIndex.put(key, position, size);
			} catch (Exception e) {
				Log.e(TAG, Long.toHexString(key) + " write failed", e);
			}
		}
	}

	public void removeCache(long key) {
		synchronized (mLock) {
			mIndex.remove(key);
		}
	}

	protected int getAvailablePosition() {
		synchronized (mLock) {
			final Iterator<Integer> it = mRecycledPositions.iterator();
			if (it.hasNext()) {
				final int position = it.next();
				it.remove();
				return position;
			}

			final int position = mMaxPosition;
			mMaxPosition += ITEM_BYTES;
			return position;
		}
	}

	protected long getSizeOfEntry(long key) {
		final String sk = Long.toHexString(key);
		final File fileCache = getCacheFile(sk);
		final File filePart = getPartFile(sk);
		final File fileHead = getHeadFile(sk);
		return fileCache.length() + filePart.length() + fileHead.length();
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

	public static long hashCode(String str, int begin, int end) {
		final int len = str != null ? str.length() : 0;
		if (end > len)
			end = len;
		if (begin < 0)
			begin = 0;

		long h = 0;
		for (int i = begin; i < end; i++)
			h = h * 31 + (str.charAt(i) - 32); // for most url or path, most letters are in ascii table
		return h;
	}

	public static long hash64(String str) {
		final int len = str != null ? str.length() : 0;
		if (len == 0)
			return 0;

		final int half = len / 2;
		return (hashCode(str, 0, half) << 32) | (0xffffffffL & hashCode(str, half, len));
	}
}
