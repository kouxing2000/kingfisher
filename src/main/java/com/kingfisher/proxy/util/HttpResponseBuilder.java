package com.kingfisher.proxy.util;

import com.kingfisher.proxy.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.HashMap;
import java.util.Map;


public class HttpResponseBuilder {

    private String version = "1.1";
    private int statusCode = 200;
    private Map<String, String> headers;

    private String body;

    public void setVersion(String version) {
        this.version = version;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getVersion() {
        return version;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public HttpResponseBuilder version(String version) {
        this.version = version;
        return this;
    }

    public HttpResponseBuilder status(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public HttpResponseBuilder setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }


    public HttpResponseBuilder body(String body) {
        this.body = body;
        return this;
    }

    public HttpResponseBuilder header(String key, String value) {
        if (headers == null) {
            this.headers = new HashMap<String, String>();
        }
        headers.put(key, value);
        return this;
    }

    public HttpResponseBuilder from(DefaultFullHttpResponse existingResponse) {
        version = existingResponse.getProtocolVersion().toString();
        statusCode = existingResponse.getStatus().code();
        body = existingResponse.content().toString(Constants.utf8);
        HttpHeaders responseHeaders = existingResponse.headers();
        this.headers = new HashMap<String, String>();

        if (responseHeaders.entries() != null) {
            for (Map.Entry<String, String> e : responseHeaders.entries()) {
                if ("Content-Length".equalsIgnoreCase(e.getKey())) {
                    continue;
                }
                this.headers.put(e.getKey(), e.getValue());
            }
        }

        return this;
    }

    public DefaultFullHttpResponse build() {
        ByteBuf buffer = null;
        if (body != null) {
            buffer = Unpooled.copiedBuffer(body, Constants.utf8);
        }

        HttpVersion httpVersion = HttpVersion.HTTP_1_1;
        if (version == null) {
            httpVersion = HttpVersion.valueOf(version);
        }
        HttpResponseStatus status = HttpResponseStatus.OK;
        if (statusCode > 0) {
            status = HttpResponseStatus.valueOf(statusCode);
        }
        DefaultFullHttpResponse defaultFullHttpResponse = buffer == null ? new DefaultFullHttpResponse(httpVersion,
                status) : new DefaultFullHttpResponse(httpVersion, status, buffer);

        if (headers != null) {
            HttpHeaders responseHeaders = defaultFullHttpResponse.headers();
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if ("Content-Length".equalsIgnoreCase(header.getKey())) {
                    continue;
                }
                responseHeaders.add(header.getKey(), header.getValue());
            }
        }

        HttpHeaders.setContentLength(defaultFullHttpResponse, buffer.readableBytes());

        return defaultFullHttpResponse;
    }

    @Override
    public String toString() {
        return "HttpResponseBuilder [version=" + version + ", statusCode=" + statusCode + ", headers=" + headers
                + ", body=" + body + "]";
    }

    public static void main(String[] args) {
        HttpResponseBuilder httpResponseBuilder = new HttpResponseBuilder();
        httpResponseBuilder.body("<html>ABCD</html>");
        httpResponseBuilder.status(302);
        httpResponseBuilder.version("1.1")
                .header("Connection", "Keep-Alive")
                .header("Keep-Alive", "timeout=5, max=100");
    }
}
