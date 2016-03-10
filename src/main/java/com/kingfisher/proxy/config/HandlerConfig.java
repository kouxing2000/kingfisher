package com.kingfisher.proxy.config;

import java.util.Collections;
import java.util.Map;

public class HandlerConfig {
	
	private String className;
	private Map<String, String> parameters;
	
	public HandlerConfig() {
	}
	
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}

	public Map<String, String> getParameters() {
		return parameters == null ? (Map<String, String>) Collections.EMPTY_MAP : parameters;
	}

	public void setParameters(Map<String, String> parameters) {
		this.parameters = parameters;
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("HandlerConfig{");
		sb.append("className='").append(className).append('\'');
		sb.append(", parameters=").append(parameters);
		sb.append('}');
		return sb.toString();
	}
}
