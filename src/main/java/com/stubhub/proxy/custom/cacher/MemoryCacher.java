package com.stubhub.proxy.custom.cacher;

import io.netty.handler.codec.http.HttpResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

public class MemoryCacher extends Cacher {
	
	private ConcurrentMap<String, HttpResponse> cache;

	@Override
	public void initial(Map<String, String> parameters) {
		super.initial(parameters);
		
		cache = new ConcurrentLinkedHashMap.Builder<String, HttpResponse>()
			    .maximumWeightedCapacity(parameterMax)
			    .build();
	}
	
	@Override
	protected HttpResponse remove(String key) {
		return cache.remove(key);
	}

	@Override
	protected HttpResponse retrieve(String key) {
		return cache.get(key);
	}

	@Override
	protected void store(String key, HttpResponse response) {
		cache.put(key, response);
	}

}
