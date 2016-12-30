package com.kingfisher.proxy;

import io.netty.handler.codec.http.HttpRequest;
import org.apache.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;

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

	private Map<String, String> variables = new HashMap<String, String>();

	public Map<String, String> getVariables() {
		return variables;
	}

	//TODO replace it with netty's HttpResponse and remove origin in name
	@Deprecated
	private HttpResponse originResponse;

	@Deprecated
	public HttpResponse getOriginResponse() {
		return originResponse;
	}

	@Deprecated
	public void setOriginResponse(HttpResponse originResponse) {
		this.originResponse = originResponse;
	}
}
