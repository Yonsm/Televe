package io.library.exo;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import io.library.client.SimpleBaseClient;
import io.library.client.SimpleHttpClient;
import io.library.client.SimpleRtspClient;

public class ClientDataSource extends BaseDataSource implements HttpDataSource {
    private SimpleBaseClient mClient;
    private DataSpec mDataSpec;
    private long mBytesRemain;
    private SimpleBaseClient.Response mResponse;
    private final RequestProperties mRequestProperties = new RequestProperties();

    public ClientDataSource() {
        super(true);
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
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
        mClient = ("rtsp".equals(scheme) || "rtsps".equals(scheme)) ? new SimpleRtspClient() : new SimpleHttpClient();
        mDataSpec = dataSpec;

        try {
            mResponse = mClient.open(request);
            mBytesRemain = dataSpec.length != C.LENGTH_UNSET ? dataSpec.length
                    : (mResponse.contentLength > 0 ? mResponse.contentLength : C.LENGTH_UNSET);
            return mBytesRemain;
        } catch (IOException e) {
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        SimpleBaseClient client = mClient;
        if (client == null || mBytesRemain == 0)
            return C.RESULT_END_OF_INPUT;
        else if (readLength == 0)
            return 0;

        try {
            if (mBytesRemain != C.LENGTH_UNSET && readLength > mBytesRemain)
                readLength = (int) mBytesRemain;
            int bytesRead = client.read(buffer, offset, readLength);
            if (bytesRead > 0 && mBytesRemain != C.LENGTH_UNSET)
                mBytesRemain -= bytesRead;
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
        return mResponse.statusLine.code;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return mResponse.toMultiMap();
    }

    @Override
    public Uri getUri() {
        SimpleBaseClient client = mClient;
        return client != null ? Uri.parse(client.getUrl()) : null;
    }

    @Override
    public void close() throws HttpDataSourceException {
        SimpleBaseClient client = mClient;
        mClient = null;
        if (client != null)
            client.close();
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
