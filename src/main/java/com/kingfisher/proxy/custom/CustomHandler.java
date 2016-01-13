package com.kingfisher.proxy.custom;

import java.util.Map;

import com.kingfisher.proxy.resolver.URLResolver;

public interface CustomHandler extends URLResolver {
	
	public void initial(Map<String, String> parameters);

}
