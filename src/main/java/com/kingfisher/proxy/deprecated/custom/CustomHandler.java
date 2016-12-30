package com.kingfisher.proxy.deprecated.custom;

import java.util.Map;

import com.kingfisher.proxy.resolver.URLResolver;

public interface CustomHandler extends URLResolver {
	
	void initial(Map<String, String> parameters);

}
