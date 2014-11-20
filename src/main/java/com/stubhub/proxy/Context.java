package com.stubhub.proxy;

import io.netty.handler.codec.http.HttpRequest;

public class Context {
	private HttpRequest request;

	public HttpRequest getRequest() {
		return request;
	}

	public Context setRequest(HttpRequest request) {
		this.request = request;
		return this;
	}
}
