/*
 * Copyright (c) 2016-2019, Inversoft Inc., All Rights Reserved
 */
package com.inversoft.rest;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inversoft.http.Cookie;
import com.inversoft.http.HTTPStrings;
import com.inversoft.net.ssl.SSLTools;

/**
 * RESTful WebService call builder. This provides the ability to call RESTful WebServices using a builder pattern to
 * set up all the necessary request information and parse the response.
 *
 * @author Brian Pontarelli
 */
public class RESTClient<RS, ERS> {
  private static final Logger logger = LoggerFactory.getLogger(RESTClient.class);

  public final List<Cookie> cookies = new ArrayList<>();

  public final Map<String, List<String>> headers = new HashMap<>();

  public final Map<String, List<Object>> parameters = new LinkedHashMap<>();

  public final StringBuilder url = new StringBuilder();

  public BodyHandler bodyHandler;

  public String certificate;

  public int connectTimeout = 2000;

  public ResponseHandler<ERS> errorResponseHandler;

  public Class<ERS> errorType;

  public boolean followRedirects = true;

  public String key;

  public String method;

  public ProxyInfo proxyInfo;

  public int readTimeout = 2000;

  public boolean sniVerificationDisabled;

  public ResponseHandler<RS> successResponseHandler;

  public Class<RS> successType;

  public String userAgent = "Restify (https://github.com/inversoft/restify)";

  public RESTClient(Class<RS> successType, Class<ERS> errorType) {
    if (successType == Void.class || errorType == Void.class) {
      throw new IllegalArgumentException("Void.class isn't valid. Use Void.TYPE instead.");
    }

    this.successType = successType;
    this.errorType = errorType;
  }

  public RESTClient<RS, ERS> authorization(String key) {
    if (key != null && !key.isEmpty()) {
      this.headers.put("Authorization", Collections.singletonList(key));
    } else {
      this.headers.remove("Authorization");
    }
    return this;
  }

  public RESTClient<RS, ERS> basicAuthorization(String username, String password) {
    if (username != null && password != null) {
      this.headers.put("Authorization", Collections.singletonList(base64Basic(username, password)));
    }
    return this;
  }

  public RESTClient<RS, ERS> bodyHandler(BodyHandler bodyHandler) {
    this.bodyHandler = bodyHandler;
    return this;
  }

  public RESTClient<RS, ERS> certificate(String certificate) {
    this.certificate = certificate;
    return this;
  }

  public RESTClient<RS, ERS> connectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  public RESTClient<RS, ERS> cookie(Cookie cookie) {
    this.cookies.add(cookie);
    return this;
  }

  public RESTClient<RS, ERS> cookies(Cookie... cookies) {
    this.cookies.addAll(Arrays.asList(cookies));
    return this;
  }

  public RESTClient<RS, ERS> cookies(List<Cookie> cookies) {
    this.cookies.addAll(cookies);
    return this;
  }

  public RESTClient<RS, ERS> delete() {
    this.method = HTTPMethod.DELETE.name();
    return this;
  }

  public RESTClient<RS, ERS> disableSNIVerification() {
    this.sniVerificationDisabled = true;
    return this;
  }

  public RESTClient<RS, ERS> errorResponseHandler(ResponseHandler<ERS> errorResponseHandler) {
    this.errorResponseHandler = errorResponseHandler;
    return this;
  }

  public RESTClient<RS, ERS> followRedirects(boolean followRedirects) {
    this.followRedirects = followRedirects;
    return this;
  }

  public RESTClient<RS, ERS> get() {
    this.method = HTTPMethod.GET.name();
    return this;
  }

  public URI getURI() {
    return URI.create(url.toString());
  }

  public ClientResponse<RS, ERS> go() {
    if (url.length() == 0) {
      throw new IllegalStateException("You must specify a URL");
    }

    Objects.requireNonNull(method, "You must specify a HTTP method");

    if (successType != Void.TYPE && successResponseHandler == null) {
      throw new IllegalStateException("You specified a success response type, you must then provide a success response handler.");
    }

    if (errorType != Void.TYPE && errorResponseHandler == null) {
      throw new IllegalStateException("You specified an error response type, you must then provide an error response handler.");
    }

    ClientResponse<RS, ERS> response = new ClientResponse<>();
    response.request = (bodyHandler != null) ? bodyHandler.getBodyObject() : null;
    response.method = method;

    HttpURLConnection huc;
    try {
      if (parameters.size() > 0) {
        if (url.indexOf("?") == -1) {
          url.append("?");
        }

        for (Iterator<Entry<String, List<Object>>> i = parameters.entrySet().iterator(); i.hasNext(); ) {
          Entry<String, List<Object>> entry = i.next();

          for (Iterator<Object> j = entry.getValue().iterator(); j.hasNext(); ) {
            Object value = j.next();
            url.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(value.toString(), "UTF-8"));
            if (j.hasNext()) {
              url.append("&");
            }
          }

          if (i.hasNext()) {
            url.append("&");
          }
        }
      }

      response.url = new URL(url.toString());

      Proxy proxy = Proxy.NO_PROXY;
      if (proxyInfo != null) {
        if (proxyInfo.host != null && proxyInfo.port != -1) {
          proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyInfo.host, proxyInfo.port));
        }

        if (proxyInfo.username != null && proxyInfo.password != null) {
          headers.put("Proxy-Authorization", Collections.singletonList(base64Basic(proxyInfo.username, proxyInfo.password)));
        }
      }

      huc = (HttpURLConnection) response.url.openConnection(proxy);
      if (response.url.getProtocol().equalsIgnoreCase("https")) {
        HttpsURLConnection hsuc = (HttpsURLConnection) huc;
        if (certificate != null) {
          if (key != null) {
            hsuc.setSSLSocketFactory(SSLTools.getSSLServerContext(certificate, key).getSocketFactory());
          } else {
            hsuc.setSSLSocketFactory(SSLTools.getSSLSocketFactory(certificate));
          }
        }

        if (sniVerificationDisabled) {
          hsuc.setHostnameVerifier((hostname, session) -> true);
        }
      }

      huc.setInstanceFollowRedirects(followRedirects);
      huc.setDoOutput(bodyHandler != null);
      huc.setConnectTimeout(connectTimeout);
      huc.setReadTimeout(readTimeout);
      huc.setRequestMethod(method);

      if (headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase(HTTPStrings.Headers.UserAgent))) {
        headers.put(HTTPStrings.Headers.UserAgent, Collections.singletonList(userAgent));
      }

      headers.forEach((name, values) -> values.forEach(value -> huc.addRequestProperty(name, value)));

      if (headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase(HTTPStrings.Headers.Cookie)) && cookies.size() > 0) {
        String header = cookies.stream()
                               .map(Cookie::toRequestHeader)
                               .collect(Collectors.joining("; "));
        huc.addRequestProperty(HTTPStrings.Headers.Cookie, header);
      }

      if (bodyHandler != null) {
        bodyHandler.setHeaders(huc);
      }

      huc.connect();

      if (bodyHandler != null) {
        try (OutputStream os = huc.getOutputStream()) {
          bodyHandler.accept(os);
          os.flush();
        }
      }
    } catch (Exception e) {
      logger.debug("Error calling REST WebService at [" + url + "]", e);
      response.status = -1;
      response.exception = e;
      return response;
    }

    int status;
    try {
      status = huc.getResponseCode();
    } catch (Exception e) {
      logger.debug("Error calling REST WebService at [" + url + "]", e);
      response.status = -1;
      response.exception = e;
      return response;
    }

    response.setHeaders(huc.getHeaderFields());
    response.status = status;

    if (status < 200 || status > 299) {
      if (errorResponseHandler == null) {
        return response;
      }

      try (InputStream is = huc.getErrorStream()) {
        response.errorResponse = errorResponseHandler.apply(is);
      } catch (Exception e) {
        logger.debug("Error calling REST WebService at [" + url + "]", e);
        response.exception = e;
        return response;
      }
    } else {
      if (successResponseHandler == null || method.equalsIgnoreCase(HTTPMethod.HEAD.name())) {
        return response;
      }

      try (InputStream is = huc.getInputStream()) {
        response.successResponse = successResponseHandler.apply(is);
      } catch (Exception e) {
        logger.debug("Error calling REST WebService at [" + url + "]", e);
        response.exception = e;
        return response;
      }
    }

    return response;
  }

  public RESTClient<RS, ERS> head() {
    this.method = HTTPMethod.HEAD.name();
    return this;
  }

  public RESTClient<RS, ERS> header(String name, String value) {
    if (name == null) {
      return this;
    }

    if (value == null) {
      this.headers.remove(name);
    } else {
      this.headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
    }

    return this;
  }

  public RESTClient<RS, ERS> headers(Map<String, String> headers) {
    if (headers == null) {
      return this;
    }

    headers.forEach(this::header);
    return this;
  }

  public RESTClient<RS, ERS> headersMap(Map<String, List<String>> headers) {
    this.headers.putAll(headers);
    return this;
  }

  public RESTClient<RS, ERS> key(String key) {
    this.key = key;
    return this;
  }

  public RESTClient<RS, ERS> method(String method) {
    try {
      // Set the override for PATCH
      if (method.equals(HTTPMethod.PATCH.name())) {
        this.method = HTTPMethod.POST.name();
        this.headers.put("X-HTTP-Method-Override", Collections.singletonList(method));
      } else {
        this.method = HTTPMethod.valueOf(method).name();
      }
    } catch (Exception e) {
      this.method = HTTPMethod.POST.name();
      this.headers.put("X-HTTP-Method-Override", Collections.singletonList(method));
    }

    return this;
  }

  public RESTClient<RS, ERS> method(HTTPMethod method) {
    this.method = method.name();
    return this;
  }

  public RESTClient<RS, ERS> patch() {
    this.method = HTTPMethod.POST.name();
    this.headers.put("X-HTTP-Method-Override", Collections.singletonList("PATCH"));
    return this;
  }

  public RESTClient<RS, ERS> post() {
    this.method = HTTPMethod.POST.name();
    return this;
  }

  public RESTClient<RS, ERS> proxy(ProxyInfo proxyInfo) {
    this.proxyInfo = proxyInfo;
    return this;
  }

  public RESTClient<RS, ERS> put() {
    this.method = HTTPMethod.PUT.name();
    return this;
  }

  public RESTClient<RS, ERS> readTimeout(int readTimeout) {
    this.readTimeout = readTimeout;
    return this;
  }

  public RESTClient<RS, ERS> successResponseHandler(ResponseHandler<RS> successResponseHandler) {
    this.successResponseHandler = successResponseHandler;
    return this;
  }

  public RESTClient<RS, ERS> uri(String uri) {
    if (url.length() == 0) {
      return this;
    }

    if (url.charAt(url.length() - 1) == '/' && uri.startsWith("/")) {
      url.append(uri.substring(1));
    } else if (url.charAt(url.length() - 1) != '/' && !uri.startsWith("/")) {
      url.append("/").append(uri);
    } else {
      url.append(uri);
    }

    return this;
  }

  public RESTClient<RS, ERS> url(String url) {
    this.url.delete(0, this.url.length());
    this.url.append(url);
    return this;
  }

  /**
   * Add a URL parameter as a key value pair.
   *
   * @param name  The URL parameter name.
   * @param value The url parameter value. The <code>.toString()</code> method will be used to
   *              get the <code>String</code> used in the URL parameter. If the object type is a
   *              {@link Collection} a key value pair will be added for each value in the collection.
   *              {@link ZonedDateTime} will also be handled uniquely in that the <code>long</code> will
   *              be used to set in the request using <code>ZonedDateTime.toInstant().toEpochMilli()</code>
   * @return This.
   */
  public RESTClient<RS, ERS> urlParameter(String name, Object value) {
    if (value == null) {
      return this;
    }

    List<Object> values = this.parameters.computeIfAbsent(name, k -> new ArrayList<>());

    if (value instanceof ZonedDateTime) {
      values.add(((ZonedDateTime) value).toInstant().toEpochMilli());
    } else if (value instanceof Collection) {
      //noinspection rawtypes
      values.addAll((Collection) value);
    } else {
      values.add(value);
    }
    return this;
  }

  /**
   * Add URL parameters from a {@code Map<String, Object>}.
   *
   * @param urlParameters The url parameters <code>Map</code> to add.  For each item in the <code>Map</code>
   *                      this will call <code>urlParameter(String, Object)</code>
   * @return This.
   */
  public RESTClient<RS, ERS> urlParameters(Map<String, Object> urlParameters) {
    if (urlParameters != null) {
      urlParameters.forEach(this::urlParameter);
    }
    return this;
  }

  /**
   * Append a url path segment. <p>
   * For Example: <pre>
   *     .url("http://www.foo.com")
   *     .urlSegment("bar")
   *   </pre>
   * This will result in a url of <code>http://www.foo.com/bar</code>
   *
   * @param value The url path segment. A null value will be ignored.
   * @return This.
   */
  public RESTClient<RS, ERS> urlSegment(Object value) {
    if (value != null) {
      if (url.charAt(url.length() - 1) != '/') {
        url.append('/');
      }
      url.append(value.toString());
    }
    return this;
  }

  private String base64Basic(String username, String password) {
    String credentials = username + ":" + password;
    Base64.Encoder encoder = Base64.getEncoder();
    return "Basic " + encoder.encodeToString(credentials.getBytes());
  }

  /**
   * Standard HTTP methods.
   */
  public enum HTTPMethod {
    CONNECT,
    DELETE,
    GET,
    HEAD,
    OPTIONS,
    PATCH,
    POST,
    PUT,
    TRACE
  }

  /**
   * Body handler that manages sending the bytes of the HTTP request body to the HttpURLConnection. This also is able to
   * manage any HTTP headers that are associated with the body such as Content-Type and Content-Length.
   */
  public interface BodyHandler {
    /**
     * Accepts the OutputStream and writes the bytes of the HTTP request body to it.
     *
     * @param os The OutputStream to write the body to.
     * @throws IOException If the write failed.
     */
    void accept(OutputStream os) throws IOException;

    /**
     * Returns the processed body. This may be used if there is use externally to get the body length or to generate a body signature. This
     * may or may not be called, so any serialization must be done before returning from {@link #setHeaders(HttpURLConnection)} or this
     * method. The request processing to build the body should only be performed once.
     *
     * @return a byte array representing the body to be written to the output stream.
     */
    byte[] getBody();

    /**
     * @return The unprocessed body object. This might be a JSON object, a Map of key value pairs or a String. By default, this returns
     * null.
     */
    default Object getBodyObject() {
      return null;
    }

    /**
     * Sets any headers for the HTTP body that will be written.
     *
     * @param huc The HttpURLConnection to set headers into.
     */
    void setHeaders(HttpURLConnection huc);
  }

  /**
   * Handles responses from the HTTP server.
   *
   * @param <T> The type that is returned from the handler.
   */
  public interface ResponseHandler<T> {
    /**
     * Handles the InputStream that is the HTTP response and reads it in and converts it to a value.
     *
     * @param is The InputStream to read from.
     * @return The value.
     *
     * @throws IOException If the read failed.
     */
    T apply(InputStream is) throws IOException;
  }
}