package com.kingfisher.proxy.config;

import com.kingfisher.proxy.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class HttpResponseBuilder {
	
	private static final Logger logger = LoggerFactory.getLogger(HttpResponseBuilder.class);
	
	private String version;
	private int statusCode;
	private Map<String, String> headers;

	private String body;

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
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

		HttpHeaders.setContentLength(defaultFullHttpResponse, buffer.readableBytes());

		if (headers != null) {
			HttpHeaders responseHeaders = defaultFullHttpResponse.headers();
			for (Map.Entry<String, String> header : headers.entrySet()) {
				responseHeaders.add(header.getKey(), header.getValue());
			}
		}

		return defaultFullHttpResponse;
	}

	@Override
	public String toString() {
		return "HttpResponseBuilder [version=" + version + ", statusCode=" + statusCode + ", headers=" + headers
				 + ", body=" + body + "]";
	}

	public static void main(String[] args) {
		HttpResponseBuilder httpResponseBuilder = new HttpResponseBuilder();
		httpResponseBuilder.setBody("<html>ABCD</html>");
		httpResponseBuilder.setStatusCode(302);
		httpResponseBuilder.setVersion("1.1");
		Map<String, String> headers2 = new HashMap<String, String>();
		headers2.put("Connection", "Keep-Alive");
		headers2.put("Keep-Alive", "timeout=5, max=100");
		httpResponseBuilder.setHeaders(headers2);
		System.out.println(new Gson().toJson(httpResponseBuilder));
	}
}
