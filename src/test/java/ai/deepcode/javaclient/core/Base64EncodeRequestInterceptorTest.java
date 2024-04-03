package ai.deepcode.javaclient.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;

public class Base64EncodeRequestInterceptorTest {

  @Test
  public void encodesCorrectly() {
    String jsonStr = "{\"src/app.ts\"}:{\"console.log(\\\"hello\\\"}\"\n";
    Base64EncodeRequestInterceptor interceptor = new Base64EncodeRequestInterceptor();

    byte[] encodedBytes = interceptor.encodeToBase64(jsonStr.getBytes());

    assertEquals("eyJzcmMvYXBwLnRzIn06eyJjb25zb2xlLmxvZyhcImhlbGxvXCJ9Igo=",
      new String(encodedBytes, StandardCharsets.UTF_8));
  }

  @Test
  public void compressesCorrectly() throws IOException {
    String base64Str = "eyJzcmMvYXBwLnRzIn06eyJjb25zb2xlLmxvZyhcImhlbGxvXCJ9Igo=";
    Base64EncodeRequestInterceptor interceptor = new Base64EncodeRequestInterceptor();

    byte[] compressedBytes = interceptor.compress(base64Str.getBytes());

    assertEquals(base64Str, new String(decompress(compressedBytes)));
  }

  private byte[] decompress(byte[] gzip) throws IOException {
    try (
      ByteArrayInputStream byteIn = new ByteArrayInputStream(gzip);
      GZIPInputStream gzIn = new GZIPInputStream(byteIn);
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream()) {
      int res = 0;
      byte[] buf = new byte[1024];
      while (res >= 0) {
        res = gzIn.read(buf, 0, buf.length);
        if (res > 0) {
          byteOut.write(buf, 0, res);
        }
      }
      return byteOut.toByteArray();
    }
  }
}

