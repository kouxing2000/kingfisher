package com.kingfisher.proxy;

import io.netty.handler.codec.http.HttpRequest;
import org.apache.http.HttpResponse;

public class Context {
	private HttpRequest request;

	public HttpRequest getRequest() {
		return request;
	}

	public Context setRequest(HttpRequest request) {
		this.request = request;
		return this;
	}
	
	private boolean usingHttps;

	public boolean isUsingHttps() {
		return usingHttps;
	}

	public Context setUsingHttps(boolean usingHttps) {
		this.usingHttps = usingHttps;
		return this;
	}

	//TODO replace it with netty's HttpResponse and remove origin in name
	private HttpResponse originResponse;

	public HttpResponse getOriginResponse() {
		return originResponse;
	}

	public void setOriginResponse(HttpResponse originResponse) {
		this.originResponse = originResponse;
	}
}
