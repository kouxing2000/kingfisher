package com.kingfisher.proxy.custom.cacher;

import com.kingfisher.proxy.Context;
import com.kingfisher.proxy.custom.CustomHandler;
import com.kingfisher.proxy.resolver.InternetFileResolver;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Cacher implements CustomHandler {
	
	private Logger logger = LoggerFactory.getLogger(Cacher.class);

	private static final String HEADER_KEY_CACHE_TIME = "kingfisher-cache-time";

	private InternetFileResolver internetFileResolver = new InternetFileResolver();

	protected long parameterMax = 1000;
	protected long parameterTimeToLiveSecond = -1;

	@Override
	public void initial(Map<String, String> parameters) {

		{
			String raw = parameters.get("max");
			if (raw != null) {
				parameterMax = Long.parseLong(raw);
			}
		}

		{
			String raw = parameters.get("timeToLiveSecond");
			if (raw != null) {
				parameterTimeToLiveSecond = Long.parseLong(raw);
			}
		}

	}

	@Override
	public HttpResponse read(String url, Context context) throws Exception {
		HttpRequest request = context.getRequest();
		String key = generateKey(request);
		HttpResponse response = retrieve(key);
		
		if (response != null) {
			// TODO not good enough, we need a job to scan all stored items to
			// save space
			if (parameterTimeToLiveSecond > 0) {
				if (System.currentTimeMillis() > parameterTimeToLiveSecond * 1000
						+ Long.parseLong(response.headers().get(HEADER_KEY_CACHE_TIME))) {
					// expired
					logger.debug("expire item: {}", key);
					response = null;
					remove(key);
				}
			}
		}
		
		if (response == null) {
			response = internetFileResolver.read(url, context);
			response.headers().add(HEADER_KEY_CACHE_TIME, String.valueOf(System.currentTimeMillis()));
			store(key, response);
		} 
		return response;
	}

	// a default implementation
	protected String generateKey(HttpRequest request) {
		return request.getUri();
	}

	protected abstract HttpResponse retrieve(String key);
	
	protected abstract HttpResponse remove(String key);

	protected abstract void store(String key, HttpResponse response);

}
