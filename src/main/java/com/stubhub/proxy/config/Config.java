package com.stubhub.proxy.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class Config {

	private int proxyPort;

	private Map<String, String> variables;

	private Map<String, String> mappings;
	private Map<String, HttpResponseBuilder> answers;
	private Map<String, HandlerConfig> handlers;

	private List<ProxyConfig> chainedProxies;

	public Config() {
		proxyPort = 8081;
		variables = new HashMap<String, String>();
		mappings = new HashMap<String, String>();
		chainedProxies = new ArrayList<ProxyConfig>();
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public Map<String, String> getVariables() {
		return variables;
	}

	public void setVariables(Map<String, String> variables) {
		this.variables = variables;
	}

	public Map<String, String> getMappings() {
		return mappings;
	}

	public void setMappings(Map<String, String> mappings) {
		this.mappings = mappings;
	}

	public List<ProxyConfig> getChainedProxies() {
		return chainedProxies;
	}

	public void setChainedProxies(List<ProxyConfig> chainedProxies) {
		this.chainedProxies = chainedProxies;
	}

	public Map<String, HttpResponseBuilder> getAnswers() {
		return answers;
	}

	public void setAnswers(Map<String, HttpResponseBuilder> answers) {
		this.answers = answers;
	}

	public Map<String, HandlerConfig> getHandlers() {
		return handlers;
	}

	public void setHandlers(Map<String, HandlerConfig> handlers) {
		this.handlers = handlers;
	}
	
	public static void main(String[] args) {
        
		Config c = new Config();
		c.proxyPort = 8081;

		c.variables.put("env", "srwd93");
		c.variables.put("branchRoot", "c:/dev/depot/main");
		c.variables.put("protocol", "https");

		c.mappings
				.put("${protocol}://*.stubhubstatic.com/resources/mojito/js/feature/bundle-event-blueprint-seatmaps-*.js",
						"${branchRoot}/reorganized_code/resourceswebapp/src/main/webapp/resources/mojito/js/feature/seatmap.js");
		c.mappings
				.put("${protocol}://*.stubhubstatic.com/resources/mojito/js/feature/bundle-event-blueprint-seatmaps2-*.js",
						"${branchRoot}/reorganized_code/resourceswebapp/src/main/webapp/resources/mojito/js/feature/seatmap2.js");

		ProxyConfig proxy = new ProxyConfig();
		proxy.setHost("slc-entbc-001");
		proxy.setPort(80);
		proxy.getBypassHosts().add("*srwd*");
		proxy.getBypassHosts().add("*stubhub*");
		c.chainedProxies.add(proxy);
		System.out.println(new Gson().toJson(c));
	}
}
