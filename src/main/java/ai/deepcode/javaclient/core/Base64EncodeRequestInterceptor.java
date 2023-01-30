package ai.deepcode.javaclient.core;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public class Base64EncodeRequestInterceptor implements Interceptor {
  private static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
  private static final String MEDIA_TYPE_OCTET_STREAM = "application/octet-stream";
  private static final String MEDIA_TYPE_OCTET_STREAM_GZIP = "gzip";

  @Override
  public @NotNull Response intercept(Chain chain) throws IOException {
    Request originalRequest = chain.request();
    Request.Builder builder = originalRequest.newBuilder();

    if (originalRequest.method().equalsIgnoreCase("POST") ||
      originalRequest.method().equalsIgnoreCase("PUT")) {

      try (Buffer buffer = new Buffer()) {
        Objects.requireNonNull(originalRequest.body()).writeTo(buffer);

        byte[] encoded = encodeToBase64(buffer.readByteArray());
        byte[] compressed = compress(encoded);

        RequestBody body = RequestBody.create(compressed, MediaType.parse(MEDIA_TYPE_OCTET_STREAM));

        builder = originalRequest.newBuilder()
          .header(CONTENT_ENCODING_HEADER, MEDIA_TYPE_OCTET_STREAM_GZIP)
          .method(originalRequest.method(), body);
      }
    }

    Request request = builder.build();

    return chain.proceed(request);
  }

  byte[] encodeToBase64(byte[] bytes) {
    return Base64.getEncoder().encode(bytes);
  }

  byte[] compress(byte[] bytes) throws IOException {
    byte[] compressed;
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
         GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
      gzip.write(bytes);
      gzip.finish();
      compressed = bos.toByteArray();
    }
    return compressed;
  }
}
