package org.deri.grefine.rdf.utils;

import org.apache.any23.http.DefaultHTTPClient;
import org.apache.any23.http.DefaultHTTPClientConfiguration;
import org.apache.any23.http.HTTPClient;
import org.apache.any23.http.HTTPClientConfiguration;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Any23HTTPClient implements HTTPClient {

    private static final Pattern ESCAPED_PATTERN = Pattern.compile("%[0-9a-f]{2}", Pattern.CASE_INSENSITIVE);

    private final PoolingClientConnectionManager manager = new PoolingClientConnectionManager();

    private HTTPClientConfiguration configuration;

    private HttpClient client = null;

    private long _contentLength = -1;

    private String actualDocumentIRI = null;

    private String contentType = null;

    public static final boolean isUrlEncoded(String url) {
        return ESCAPED_PATTERN.matcher(url).find();
    }

    /**
     * Creates a {@link DefaultHTTPClient} instance already initialized
     *
     * @return populated {@link org.apache.any23.http.DefaultHTTPClient}
     */
    public static DefaultHTTPClient createInitializedHTTPClient() {
        final DefaultHTTPClient defaultHTTPClient = new DefaultHTTPClient();
        defaultHTTPClient.init(DefaultHTTPClientConfiguration.singleton());
        return defaultHTTPClient;
    }

    public void init(HTTPClientConfiguration configuration) {
        if (configuration == null) throw new NullPointerException("Illegal configuration, cannot be null.");
        this.configuration = configuration;
    }

    /**
     * Opens an {@link java.io.InputStream} from a given IRI.
     * It follows redirects.
     *
     * @param uri to be opened
     * @return {@link java.io.InputStream}
     * @throws IOException if there is an error opening the {@link java.io.InputStream}
     *                     located at the URI.
     */
    public InputStream openInputStream(String uri) throws IOException {
        HttpGet method = null;
        try {
            ensureClientInitialized();
            String uriStr;
            try {
                URI uriObj = new URI(uri); //TODO do we need to handle escaped or not separately?
                // [scheme:][//authority][path][?query][#fragment]
                uriStr = uriObj.toString();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid IRI string.", e);
            }
            method = new HttpGet(uriStr);

            HttpResponse response = client.execute(method);
            HttpEntity entity = response.getEntity();
            _contentLength = entity.getContentLength();
            final Header contentTypeHeader = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
            contentType = contentTypeHeader == null ? null : contentTypeHeader.getValue();
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new IOException(
                        "Failed to fetch " + uri + ": " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase()
                );
            }
            actualDocumentIRI = method.getURI().toString();
            byte[] responseBytes = EntityUtils.toByteArray(entity);
            EntityUtils.consume(entity);

            return new ByteArrayInputStream(responseBytes);
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    /**
     * Shuts down the connection manager.
     */
    public void close() {
        manager.shutdown();
    }

    public long getContentLength() {
        return _contentLength;
    }

    public String getActualDocumentIRI() {
        return actualDocumentIRI;
    }

    public String getContentType() {
        return contentType;
    }

    protected int getConnectionTimeout() {
        return configuration.getDefaultTimeout();
    }

    protected int getSoTimeout() {
        return configuration.getDefaultTimeout();
    }

    private void ensureClientInitialized() {
        if (configuration == null) throw new IllegalStateException("client must be initialized first.");
        if (client != null) return;
        client = new DefaultHttpClient(manager);
        HttpParams params = client.getParams();
        params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, configuration.getDefaultTimeout());
        params.setParameter(CoreConnectionPNames.SO_TIMEOUT, configuration.getDefaultTimeout());
        manager.setMaxTotal(configuration.getMaxConnections());

        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(HttpHeaders.USER_AGENT, configuration.getUserAgent()));
        if (configuration.getAcceptHeader() != null) {
            headers.add(new BasicHeader(HttpHeaders.ACCEPT, configuration.getAcceptHeader()));
        }
        headers.add(new BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-us,en-gb,en,*;q=0.3")); //TODO: this must become parametric.
        headers.add(new BasicHeader(HttpHeaders.ACCEPT_CHARSET, "utf-8,iso-8859-1;q=0.7,*;q=0.5"));
        // headers.add(new Header("Accept-Encoding", "x-gzip, gzip"));

        params.setParameter(ClientPNames.DEFAULT_HEADERS, headers);

        params.setParameter(ClientPNames.HANDLE_REDIRECTS, true);

        params.setParameter(ConnRoutePNames.DEFAULT_PROXY,
                new HttpHost("10.185.190.70", 8080, "http")
        );
    }

}
