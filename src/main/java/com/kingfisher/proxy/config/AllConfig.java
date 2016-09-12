package com.kingfisher.proxy.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class AllConfig {

	private int proxyPort;

	private Map<String, String> variables;

	private List<ProxyConfig> chainedProxies;

	private String proxyHost;

	private List<RuleConfig> ruleConfigs;
	
	public AllConfig() {
		proxyHost = null;
		proxyPort = 8081;
		variables = new HashMap<String, String>();
		chainedProxies = new ArrayList<ProxyConfig>();
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
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

	public List<ProxyConfig> getChainedProxies() {
		return chainedProxies;
	}

	public void setChainedProxies(List<ProxyConfig> chainedProxies) {
		this.chainedProxies = chainedProxies;
	}

	public List<RuleConfig> getRuleConfigs() {
		return ruleConfigs;
	}

	public void setRuleConfigs(List<RuleConfig> ruleConfigs) {
		this.ruleConfigs = ruleConfigs;
	}

	public static void main(String[] args) {
        
		AllConfig c = new AllConfig();
		c.proxyPort = 8081;

		c.variables.put("env", "srwd93");
		c.variables.put("branchRoot", "c:/dev/depot/main");
		c.variables.put("protocol", "https");

		ProxyConfig proxy = new ProxyConfig();
		proxy.setHost("slc-entbc-001");
		proxy.setPort(80);
		proxy.getBypassHosts().add("*slcq*");
		c.chainedProxies.add(proxy);
		System.out.println(new Gson().toJson(c));
	}
}
