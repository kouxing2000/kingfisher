package com.stubhub.proxy.resolver;

import com.stubhub.proxy.Context;

import io.netty.handler.codec.http.HttpResponse;

public interface URLResolver {
	
	HttpResponse read(String url, Context context) throws Exception;
	
}
