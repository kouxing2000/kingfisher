package com.kingfisher.proxy.resolver;

import com.kingfisher.proxy.util.HttpClientUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingfisher.proxy.Context;

public class InternetFileResolver implements URLResolver {

	private static Logger logger = LoggerFactory.getLogger(InternetFileResolver.class);


	private static final List<String> requestCopyHeaderNames = new ArrayList<String>();
	static {
		requestCopyHeaderNames.add(HttpHeaders.Names.AUTHORIZATION);
		requestCopyHeaderNames.add(HttpHeaders.Names.COOKIE);
		requestCopyHeaderNames.add(HttpHeaders.Names.USER_AGENT);
		requestCopyHeaderNames.add(HttpHeaders.Names.ACCEPT);
		requestCopyHeaderNames.add(HttpHeaders.Names.ACCEPT_ENCODING);
		requestCopyHeaderNames.add(HttpHeaders.Names.ACCEPT_LANGUAGE);
	}

	private static final List<String> responseCopyHeaderNames = new ArrayList<String>();
	static {
		responseCopyHeaderNames.add(HttpHeaders.Names.CONTENT_LENGTH);
		responseCopyHeaderNames.add(HttpHeaders.Names.CONNECTION);
		responseCopyHeaderNames.add(HttpHeaders.Names.CONTENT_TYPE);
		responseCopyHeaderNames.add(HttpHeaders.Names.CONTENT_ENCODING);
		responseCopyHeaderNames.add(HttpHeaders.Names.COOKIE);
	}

	@Override
	public HttpResponse read(String url, Context context) throws Exception {

		byte[] bytearray = null;

		HttpClient httpclient = HttpClientUtils.getNewHttpClient();
		HttpGet httpget = new HttpGet(url);
		HttpHeaders headers = context.getRequest().headers();
		if (headers != null) {
			// copy headers
			for (String header : requestCopyHeaderNames) {
				if (headers.contains(header)) {
					httpget.setHeader(header, headers.get(header));
				}
			}
		}
		org.apache.http.HttpResponse response = null;
		try {
			response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream instream = entity.getContent();
				try {
					bytearray = IOUtils.toByteArray(instream);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					IOUtils.closeQuietly(instream);
				}
			}
		} finally {
			httpclient.getConnectionManager().shutdown();
		}

		ByteBuf buffer = null;
		if (bytearray != null) {
			buffer = Unpooled.copiedBuffer(generateResponseBytes(bytearray, context));
		}

		DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
				HttpResponseStatus.OK, buffer);

		// bug here
		// HttpHeaders.setContentLength(defaultFullHttpResponse, buffer.readableBytes());
		if (response != null) {
			Header[] allHeaders = response.getAllHeaders();
			for (Header header : allHeaders) {
				if (header != null) {
					defaultFullHttpResponse.headers().add(header.getName(), header.getValue());
				}
			}
		}

		try {
			defaultFullHttpResponse.headers().set("Content-From", URLEncoder.encode(url, "UTF-8"));
		} catch (Exception e) {
		}

		return defaultFullHttpResponse;
	}

	protected byte[] generateResponseBytes(byte[] originalBytes, Context context) {
		return originalBytes;
	}

}
