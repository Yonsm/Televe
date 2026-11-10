package io.library.exo;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.library.client.SimpleBaseClient;
import io.library.client.SimpleHttpClient;
import io.library.client.SimpleRtspClient;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class ClientDataSource extends BaseDataSource implements HttpDataSource {
    private SimpleBaseClient mClient;
    private DataSpec mDataSpec;
    private long mBytesRemain;
    private boolean mOpened;
    private SimpleBaseClient.Response mResponse;
    private final RequestProperties mRequestProperties = new RequestProperties();

    public ClientDataSource() {
        super(true);
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        mResponse = null;
        String method = dataSpec.httpBody != null ? "POST" : "GET";
        ByteBuffer body = dataSpec.httpBody != null ? ByteBuffer.wrap(dataSpec.httpBody) : null;
        SimpleBaseClient.Request request = new SimpleBaseClient.Request(method, dataSpec.uri.toString(), body);

        for (Map.Entry<String, String> property : mRequestProperties.getSnapshot().entrySet()) {
            request.put(property.getKey(), property.getValue());
        }
        if (!(dataSpec.position == 0 && dataSpec.length == C.LENGTH_UNSET)) {
            String rangeRequest = "bytes=" + dataSpec.position + "-";
            if (dataSpec.length != C.LENGTH_UNSET)
                rangeRequest += (dataSpec.position + dataSpec.length - 1);
            request.put("Range", rangeRequest);
        }

        String scheme = dataSpec.uri.getScheme();
        mClient = ("rtsp".equalsIgnoreCase(scheme) || "rtsps".equalsIgnoreCase(scheme)) ? new SimpleRtspClient() : new SimpleHttpClient();
        mDataSpec = dataSpec;
        transferInitializing(dataSpec);

        try {
            mResponse = mClient.open(request);
            mOpened = true;
            transferStarted(dataSpec);
            long bytesToSkip = mClient instanceof SimpleHttpClient && mResponse.statusLine.code == 200
                    ? dataSpec.position : 0;
            if (bytesToSkip > 0) {
                if (mResponse.contentLength > 0 && bytesToSkip > mResponse.contentLength)
                    throw new EOFException("Requested position exceeds response length");
                byte[] buffer = new byte[4096];
                for (long remaining = bytesToSkip; remaining > 0; ) {
                    int read = mClient.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) throw new EOFException("Requested position exceeds response length");
                    remaining -= read;
                    bytesTransferred(read);
                }
            }
            long contentLength = mResponse.contentLength > 0 ? mResponse.contentLength - bytesToSkip : C.LENGTH_UNSET;
            mBytesRemain = dataSpec.length == C.LENGTH_UNSET ? contentLength
                    : contentLength == C.LENGTH_UNSET ? dataSpec.length : Math.min(dataSpec.length, contentLength);
            return mBytesRemain;
        } catch (SimpleHttpClient.InvalidResponseException e) {
            mResponse = e.response;
            throw new InvalidResponseCodeException(mResponse.statusLine.code, mResponse.statusLine.description,
                    e, mResponse.toMultiMap(), dataSpec, new byte[0]);
        } catch (IOException e) {
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        if (readLength == 0)
            return 0;
        SimpleBaseClient client = mClient;
        if (client == null || mBytesRemain == 0)
            return C.RESULT_END_OF_INPUT;

        try {
            if (mBytesRemain != C.LENGTH_UNSET && readLength > mBytesRemain)
                readLength = (int) mBytesRemain;
            int bytesRead = client.read(buffer, offset, readLength);
            if (bytesRead > 0) {
                if (mBytesRemain != C.LENGTH_UNSET) mBytesRemain -= bytesRead;
                bytesTransferred(bytesRead);
            }
            return bytesRead;
        } catch (IOException e) {
            throw new HttpDataSourceException(e, mDataSpec, HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public void setRequestProperty(String name, String value) {
        mRequestProperties.set(name, value);
    }

    @Override
    public void clearRequestProperty(String name) {
        mRequestProperties.remove(name);
    }

    @Override
    public void clearAllRequestProperties() {
        mRequestProperties.clear();
    }

    @Override
    public int getResponseCode() {
        return mResponse != null ? mResponse.statusLine.code : -1;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return mResponse != null ? mResponse.toMultiMap() : Collections.emptyMap();
    }

    @Override
    public Uri getUri() {
        SimpleBaseClient client = mClient;
        if (client == null) return null;
        String url = client.getUrl();
        return url != null ? Uri.parse(url) : mDataSpec != null ? mDataSpec.uri : null;
    }

    @Override
    public void close() throws HttpDataSourceException {
        SimpleBaseClient client = mClient;
        mClient = null;
        mResponse = null;
        try {
            if (client != null) client.close();
        } finally {
            if (mOpened) {
                mOpened = false;
                transferEnded();
            }
        }
    }

    public static class DefaultFactory implements DataSource.Factory {
        private final Map<String, String> mHeaders;

        public DefaultFactory(Map<String, String> headers) {
            mHeaders = headers;
        }

        @Override
        public DataSource createDataSource() {
            ClientDataSource source = new ClientDataSource();
            if (mHeaders != null) {
                for (Map.Entry<String, String> entry : mHeaders.entrySet())
                    source.setRequestProperty(entry.getKey(), entry.getValue());
            }
            return source;
        }
    }
}
