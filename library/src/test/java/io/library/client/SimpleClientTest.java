package io.library.client;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class SimpleClientTest {
    @Test
    public void writeFullyRespectsBufferPositionAndLimit() throws IOException {
        byte[] backing = "0123456789".getBytes(StandardCharsets.UTF_8);
        ByteBuffer slice = ByteBuffer.wrap(backing, 2, 5).slice();
        slice.position(1);
        slice.limit(4);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SimpleBaseClient.writeFully(slice, out);

        assertEquals("345", out.toString(StandardCharsets.UTF_8.name()));
        assertFalse(slice.hasRemaining());
    }

    @Test(expected = IOException.class)
    public void readUtf8LineStrictlyRejectsUnterminatedStream() throws IOException {
        byte[] buf = new byte[16];
        ByteArrayInputStream in = new ByteArrayInputStream("HTTP/1.1 200 OK".getBytes(StandardCharsets.UTF_8));
        SimpleBaseClient.readUtf8LineStrictly(in, buf);
    }

    @Test
    public void readUtf8LineStrictlyReadsCrLfAndLfLines() throws IOException {
        byte[] buf = new byte[64];
        ByteArrayInputStream in = new ByteArrayInputStream("ICY 200 OK\r\nSecond\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("ICY 200 OK", SimpleBaseClient.readUtf8Line(in, buf));
        assertEquals("Second", SimpleBaseClient.readUtf8Line(in, buf));
    }
}
