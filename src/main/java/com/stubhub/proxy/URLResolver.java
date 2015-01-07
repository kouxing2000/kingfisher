package com.stubhub.proxy;

import io.netty.handler.codec.http.HttpResponse;

public interface URLResolver {
	
	HttpResponse read(String url, Context context) throws Exception;
	
}
