package me.internalizable.numdrassl.auth.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;

/**
 * Factory for creating configured HTTP clients.
 */
public final class HttpClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientFactory.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private HttpClientFactory() {}

    /**
     * Creates an HTTP client with TLS support.
     *
     * @param connectTimeout connection timeout
     * @return configured HTTP client
     */
    @Nonnull
    public static HttpClient createTlsClient(@Nonnull Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return HttpClient.newBuilder().sslContext(sslContext).connectTimeout(connectTimeout).build();
        } catch (Exception e) {
            LOGGER.error("Failed to create TLS HTTP client", e);
            return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        }
    }

    /**
     * Creates an HTTP client with default timeout.
     */
    @Nonnull
    public static HttpClient createTlsClient() {
        return createTlsClient(DEFAULT_CONNECT_TIMEOUT);
    }
}
