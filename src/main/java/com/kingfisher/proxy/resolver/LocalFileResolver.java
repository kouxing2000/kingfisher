package com.kingfisher.proxy.resolver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.io.IOUtils;

import com.kingfisher.proxy.Context;

public class LocalFileResolver implements URLResolver {

	//private static Logger logger = LoggerFactory.getLogger(LocalFileResolver.class);

	private static Map<String, String> contentTypes = new HashMap<String, String>();

	static {
		contentTypes.put("css", "text/css");
		contentTypes.put("png", "image/png");
		contentTypes.put("js", "text/javascript");
		contentTypes.put("gif", "image/gif");
		contentTypes.put("jpg", "image/jpg");
		contentTypes.put("xml", "application/xml");
	}

	private String fetchContentType(String url) {
		String[] xs = url.split("\\.");
		String contentType = null;

		if (xs != null && xs.length >= 2) {
			String fileFormat = xs[xs.length - 1];
			contentType = contentTypes.get(fileFormat);
		}

		if (contentType == null) {
//			MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
//			contentType = mimeTypesMap.getContentType(url);
			contentType = "text/html";
		}

		return contentType;

	}

	@Override
	public HttpResponse read(String url, Context context) throws Exception {
		// TODO refer
		// https://github.com/netty/netty/blob/4.0/example/src/main/java/io/netty/example/http/file/HttpStaticFileServerHandler.java

		byte[] byteArray = null;

		FileInputStream input = null;
		try {
			input = new FileInputStream(new File(url));
			byteArray = IOUtils.toByteArray(input);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		String contentType = null;

		if (context.getRequest() != null) {
			String accept = context.getRequest().headers().get("Accept");
			if (!"*/*".equals(accept) && !accept.contains(",")) {
				contentType = accept;
			}
		}

		ByteBuf buffer = null;
		if (byteArray != null) {
			buffer = Unpooled.copiedBuffer(byteArray);
			if (contentType == null) {
				contentType = fetchContentType(url);
			}
		}
		
		DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
				HttpResponseStatus.OK, buffer);

		HttpHeaders.setContentLength(defaultFullHttpResponse, buffer.readableBytes());

		if (contentType != null) {
			defaultFullHttpResponse.headers().set("Content-Type", contentType);
		}

		try {
			defaultFullHttpResponse.headers().set("Content-From", URLEncoder.encode(url, "UTF-8"));
		} catch (Exception e) {
		}

		return defaultFullHttpResponse;
	}

}
