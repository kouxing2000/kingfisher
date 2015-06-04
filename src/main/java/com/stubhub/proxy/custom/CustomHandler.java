package com.stubhub.proxy.custom;

import java.util.Map;

import com.stubhub.proxy.resolver.URLResolver;

public interface CustomHandler extends URLResolver {
	
	public void initial(Map<String, String> parameters);

}
