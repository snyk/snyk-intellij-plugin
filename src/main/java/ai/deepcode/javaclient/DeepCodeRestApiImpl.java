package ai.deepcode.javaclient;

import ai.deepcode.javaclient.core.Base64EncodeRequestInterceptor;
import ai.deepcode.javaclient.requests.ExtendBundleWithContentRequest;
import ai.deepcode.javaclient.requests.ExtendBundleWithHashRequest;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.requests.FileHashRequest;
import ai.deepcode.javaclient.requests.GetAnalysisRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * https://deepcode.freshdesk.com/support/solutions/articles/60000346777-sessions
 * https://deepcode.freshdesk.com/support/solutions/articles/60000357438-bundles
 */
public class DeepCodeRestApiImpl implements DeepCodeRestApi {

  public static final String API_URL = "https://deeproxy.snyk.io/";

  private static Retrofit retrofit = buildRetrofit(API_URL, false, false);

  public DeepCodeRestApiImpl(Retrofit retrofit) {
    DeepCodeRestApiImpl.retrofit = retrofit;
  }

  // Create simple REST adapter which points the baseUrl.
  private static Retrofit buildRetrofit(
    String baseUrl, boolean disableSslVerification, boolean requestLogging) {
    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
    // set your desired log level
    if (requestLogging) {
      logging.setLevel(HttpLoggingInterceptor.Level.BODY);
    } else {
      logging.setLevel(HttpLoggingInterceptor.Level.NONE);
    }

    OkHttpClient.Builder builder =
      new OkHttpClient.Builder()
        .connectTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(100, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .addInterceptor(new Base64EncodeRequestInterceptor());

    if (disableSslVerification) {
      X509TrustManager x509TrustManager = buildUnsafeTrustManager();
      final TrustManager[] trustAllCertificates = new TrustManager[]{x509TrustManager};

      try {
        final String sslProtocol = "TLSv1.3";
        SSLContext sslContext = SSLContext.getInstance(sslProtocol);
        sslContext.init(null, trustAllCertificates, new SecureRandom());
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        builder.sslSocketFactory(sslSocketFactory, x509TrustManager);
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        // TODO(pavel): extract Retrofit and OkHttpClient into configuration object to simplify API
        // client building.
        e.printStackTrace();
      }
    }

    OkHttpClient client = builder.build();
    return new Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(client)
      .addConverterFactory(GsonConverterFactory.create())
      .build();
  }

  @NotNull
  private static X509TrustManager buildUnsafeTrustManager() {
    return new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[]{};
      }
    };
  }

  private interface CreateBundleCall {
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("bundle")
    Call<CreateBundleResponse> doCreateBundle(
      @Header("snyk-org-name") String orgName,
      @Body FileContentRequest files);

    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("bundle")
    Call<CreateBundleResponse> doCreateBundle(
      @Header("snyk-org-name") String orgName,
      @Body FileHashRequest files);
  }

  private static <Req> CreateBundleResponse doCreateBundle(String orgName, Req request) {
    CreateBundleCall createBundleCall = retrofit.create(CreateBundleCall.class);
    Response<CreateBundleResponse> retrofitResponse;
    try {
      if (request instanceof FileContentRequest)
        retrofitResponse =
          createBundleCall.doCreateBundle(orgName, (FileContentRequest) request).execute();
      else if (request instanceof FileHashRequest)
        retrofitResponse =
          createBundleCall.doCreateBundle(orgName, (FileHashRequest) request).execute();
      else throw new IllegalArgumentException();
    } catch (IOException e) {
      return new CreateBundleResponse();
    }
    CreateBundleResponse result = retrofitResponse.body();
    if (result == null) {
      result = new CreateBundleResponse();
    }
    result.setStatusCode(retrofitResponse.code());
    switch (retrofitResponse.code()) {
      case 200:
        result.setStatusDescription("The bundle creation was successful");
        break;
      case 400:
        result.setStatusDescription("Request content doesn't match the specifications");
        break;
      case 401:
        result.setStatusDescription("Missing sessionToken or incomplete login process");
        break;
      case 403:
        result.setStatusDescription("Unauthorized access to requested repository");
        break;
      case 404:
        result.setStatusDescription("Unable to resolve requested oid");
        break;
      default:
        result.setStatusDescription("Unknown Status Code: " + retrofitResponse.code());
        break;
    }
    return result;
  }

  /**
   * Creates a new bundle with direct file(s) source.
   *
   * @return {@link CreateBundleResponse} instance
   */
  @Override
  @NotNull
  public CreateBundleResponse createBundle(String orgName, FileContentRequest files) {
    return doCreateBundle(orgName, files);
  }

  /**
   * Creates a new bundle for file(s) with Hash.
   *
   * @return {@link CreateBundleResponse} instance
   */
  @Override
  @NotNull
  public CreateBundleResponse createBundle(String orgName, FileHashRequest files) {
    return doCreateBundle(orgName, files);
  }

  private interface CheckBundleCall {
    //    @retrofit2.http.Headers("Content-Type: application/json")
    @GET("bundle/{bundleId}")
    Call<CreateBundleResponse> doCheckBundle(
      @Header("snyk-org-name") String orgName,
      @Path(value = "bundleId", encoded = true) String bundleId);
  }

  /**
   * Checks the status of a bundle.
   *
   * @param bundleId the parent bundle to extend
   * @return {@link CreateBundleResponse} instance
   */
  @Override
  @NotNull
  public CreateBundleResponse checkBundle(String orgName, String bundleId) {
    CheckBundleCall checkBundleCall = retrofit.create(CheckBundleCall.class);
    Response<CreateBundleResponse> retrofitResponse;
    try {
      retrofitResponse = checkBundleCall.doCheckBundle(orgName, bundleId).execute();
    } catch (IOException e) {
      return new CreateBundleResponse();
    }
    CreateBundleResponse result = retrofitResponse.body();
    if (result == null) {
      result = new CreateBundleResponse();
    }
    result.setStatusCode(retrofitResponse.code());
    switch (retrofitResponse.code()) {
      case 200:
        result.setStatusDescription("The bundle checked successfully");
        break;
      case 401:
        result.setStatusDescription("Missing sessionToken or incomplete login process");
        break;
      case 403:
        result.setStatusDescription("Unauthorized access to parent bundle");
        break;
      case 404:
        result.setStatusDescription("Uploaded bundle has expired");
        break;
      default:
        result.setStatusDescription("Unknown Status Code: " + retrofitResponse.code());
        break;
    }
    return result;
  }

  private interface ExtendBundleCall {
    @retrofit2.http.Headers("Content-Type: application/json")
    @PUT("bundle/{bundleId}")
    Call<CreateBundleResponse> doExtendBundle(

      @Header("snyk-org-name") String orgName,
      @Path(value = "bundleId", encoded = true) String bundleId,
      @Body ExtendBundleWithHashRequest extendBundleWithHashRequest);

    @retrofit2.http.Headers("Content-Type: application/json")
    @PUT("bundle/{bundleId}")
    Call<CreateBundleResponse> doExtendBundle(
      @Header("snyk-org-name") String orgName,
      @Path(value = "bundleId", encoded = true) String bundleId,
      @Body ExtendBundleWithContentRequest extendBundleWithContentRequest);
  }

  /**
   * Creates a new bundle by extending a previously uploaded one.
   *
   * @param bundleId the parent bundle to extend
   * @return {@link CreateBundleResponse} instance
   */
  @Override
  @NotNull
  public <Req> CreateBundleResponse extendBundle(
    String orgName, String bundleId, Req request) {
    ExtendBundleCall extendBundleCall = retrofit.create(ExtendBundleCall.class);
    Response<CreateBundleResponse> retrofitResponse;
    try {
      if (request instanceof ExtendBundleWithHashRequest)
        retrofitResponse =
          extendBundleCall
            .doExtendBundle(orgName, bundleId, (ExtendBundleWithHashRequest) request)
            .execute();
      else if (request instanceof ExtendBundleWithContentRequest)
        retrofitResponse =
          extendBundleCall
            .doExtendBundle(orgName, bundleId, (ExtendBundleWithContentRequest) request)
            .execute();
      else throw new IllegalArgumentException();
    } catch (IOException e) {
      return new CreateBundleResponse();
    }
    CreateBundleResponse result = retrofitResponse.body();
    if (result == null) {
      result = new CreateBundleResponse();
    }
    result.setStatusCode(retrofitResponse.code());
    switch (retrofitResponse.code()) {
      case 200:
        result.setStatusDescription("The bundle extension was successful");
        break;
      case 400:
        result.setStatusDescription(
          "Attempted to extend a git bundle, or ended up with an empty bundle after the extension");
        break;
      case 401:
        result.setStatusDescription("Missing sessionToken or incomplete login process");
        break;
      case 403:
        result.setStatusDescription("Unauthorized access to parent bundle");
        break;
      case 404:
        result.setStatusDescription("Parent bundle has expired");
        break;
      case 413:
        result.setStatusDescription("Payload too large");
        break;
      default:
        result.setStatusDescription("Unknown Status Code: " + retrofitResponse.code());
        break;
    }
    return result;
  }

  private interface GetAnalysisCall {
    @retrofit2.http.Headers("Content-Type: application/json")
    @POST("analysis")
    Call<GetAnalysisResponse> doGetAnalysis(
      @Header("snyk-org-name") String orgName,
      @Body GetAnalysisRequest filesToAnalyse);
  }

  /**
   * Starts a new bundle analysis or checks its current status and available results.
   *
   * @return {@link GetAnalysisResponse} instance}
   */
  @Override
  @NotNull
  public GetAnalysisResponse getAnalysis(
    String orgName,
    String bundleId,
    Integer severity,
    List<String> filesToAnalyse,
    String shard,
    String ideProductName
  ) {
    GetAnalysisCall getAnalysisCall = retrofit.create(GetAnalysisCall.class);
    try {
      Response<GetAnalysisResponse> retrofitResponse =
        getAnalysisCall
          .doGetAnalysis(orgName, new GetAnalysisRequest(bundleId, filesToAnalyse, severity, shard, ideProductName, orgName))
          .execute();
      GetAnalysisResponse result = retrofitResponse.body();
      if (result == null) result = new GetAnalysisResponse();
      result.setStatusCode(retrofitResponse.code());
      switch (retrofitResponse.code()) {
        case 200:
          result.setStatusDescription("The analysis request was successful");
          break;
        case 401:
          result.setStatusDescription("Missing sessionToken or incomplete login process");
          break;
        case 403:
          result.setStatusDescription("Unauthorized access to requested repository");
          break;
        default:
          result.setStatusDescription("Unknown Status Code: " + retrofitResponse.code());
          break;
      }
      return result;
    } catch (IOException e) {
      return new GetAnalysisResponse();
    }
  }

  private interface GetFiltersCall {
    @GET("filters")
    Call<GetFiltersResponse> doGetFilters();
  }

  /**
   * Requests current filtering options for uploaded bundles.
   *
   * @return {@link GetFiltersResponse} instance}
   */
  @Override
  @NotNull
  public GetFiltersResponse getFilters() {
    GetFiltersCall getFiltersCall = retrofit.create(GetFiltersCall.class);
    try {
      Response<GetFiltersResponse> retrofitResponse = getFiltersCall.doGetFilters().execute();
      GetFiltersResponse result = retrofitResponse.body();
      if (result == null) result = new GetFiltersResponse();
      result.setStatusCode(retrofitResponse.code());
      switch (retrofitResponse.code()) {
        case 200:
          result.setStatusDescription("The filters request was successful");
          break;
        case 401:
          result.setStatusDescription("Missing sessionToken or incomplete login process");
          break;
        default:
          result.setStatusDescription("Unknown Status Code: " + retrofitResponse.code());
          break;
      }
      return result;
    } catch (IOException e) {
      return new GetFiltersResponse();
    }
  }
}
