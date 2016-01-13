package com.kingfisher.proxy.resolver;

import com.kingfisher.proxy.Context;

import io.netty.handler.codec.http.HttpResponse;

public interface URLResolver {
	
	HttpResponse read(String url, Context context) throws Exception;
	
}
