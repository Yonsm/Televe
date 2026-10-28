package io.library.utils;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;


public abstract class ParcelableObject<T> implements Parcelable {

    public abstract void writeTo(DataOutput out) throws IOException;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        try {
            writeTo(new ParcelDataOutput(dest));
        } catch (Exception ignored) {
        }
    }

    public static void writeUTF(String str, DataOutput out) throws IOException {
        out.writeUTF(str != null ? str : "");
    }

    @SuppressWarnings("unchecked")
    public static <T extends ParcelableObject<T>> T readObject(Class<T> cls, DataInput in) throws IOException {
        if (!in.readBoolean())
            return null;
        try {
            return cls.getConstructor(DataInput.class).newInstance(in);
        } catch (Throwable e) {
            throw new IOException("Read object failed: " + cls, e);
        }
    }

    public static <T extends ParcelableObject<T>> void writeObject(T obj, DataOutput out) throws IOException {
        out.writeBoolean(obj != null);
        if (obj != null)
            obj.writeTo(out);
    }

    @SuppressWarnings("unchecked")
    public static <T extends ParcelableObject<T>> T[] readArray(Class<T> cls, DataInput in) throws IOException {
        int count = in.readShort();
        if (count == 0)
            return null;
        try {
            Field field = cls.getField("CREATOR");
            ParcelableCreator<T> creator = (ParcelableCreator<T>) field.get(null);
            T[] a = creator.newArray(count);
            Constructor<T> constructor = cls.getConstructor(DataInput.class);
            for (int i = 0; i < count; i++)
                a[i] = constructor.newInstance(in);
            return a;
        } catch (Throwable e) {
            throw new IOException("Read array failed: " + cls, e);
        }
    }

    public static <T extends ParcelableObject<T>> void writeArray(T[] a, DataOutput out) throws IOException {
        int count = a != null ? a.length : 0;
        out.writeShort(count);
        for (int i = 0; i < count; i++)
            a[i].writeTo(out);
    }

    public static class ParcelableCreator<T extends ParcelableObject<T>> implements Creator<T> {
        private final Class<T> mTClass;

        public ParcelableCreator(Class<T> cls) {
            mTClass = cls;
        }

        @Override
        public T createFromParcel(Parcel in) {
            try {
                return mTClass.getConstructor(DataInput.class).newInstance(new ParcelDataInput(in));
            } catch (Throwable e) {
                Log.e("ParcelableCreator", "Create failed: ", e);
                return null;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T[] newArray(int size) {
            return (T[]) Array.newInstance(mTClass, size);
        }
    }
}
