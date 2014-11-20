package com.stubhub.proxy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;
import javax.management.RuntimeErrorException;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.impl.ProxyUtils;

import com.google.gson.Gson;

public class Config {

	private int proxyPort;

	private Map<String, String> variables;

	private Map<String, String> mappings;
	private Map<String, HttpResponseBuilder> answers;

	public static class Proxy {
		private String host;
		private int port;
		private List<String> bypassHosts;

		public Proxy() {
			bypassHosts = new ArrayList<String>();
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public List<String> getBypassHosts() {
			return bypassHosts;
		}

		public void setBypassHosts(List<String> bypassHosts) {
			this.bypassHosts = bypassHosts;
		}

		private transient ChainedProxy chainedProxy;

		public ChainedProxy getChainedProxy() {
			if (chainedProxy == null) {
				chainedProxy = new ChainedProxyAdapter() {
					@Override
					public InetSocketAddress getChainedProxyAddress() {
						try {
							return new InetSocketAddress(InetAddress.getByName(host), port);
						} catch (UnknownHostException e) {
							throw new RuntimeException(e);
						}
					}

					@Override
					public void connectionFailed(Throwable cause) {
					}

				};
			}
			return chainedProxy;
		}

		private transient List<Pattern> bypassHostPatterns;

		public boolean isAllowed(String host) {
			if (bypassHostPatterns == null) {
				synchronized (this) {
					bypassHostPatterns = new ArrayList<Pattern>();
					for (String bypassHost : bypassHosts) {
						String regex = ProxyUtils.wildcardToRegex(bypassHost);
						bypassHostPatterns.add(Pattern.compile(regex));
					}
				}
			}

			for (Pattern bypassPattern : bypassHostPatterns) {
				if (bypassPattern.matcher(host).matches()) {
					return false;
				}
			}

			return true;
		}

	}

	private List<Proxy> chainedProxies;

	public Config() {
		proxyPort = 8081;
		variables = new HashMap<String, String>();
		mappings = new HashMap<String, String>();
		chainedProxies = new ArrayList<Config.Proxy>();
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

	public List<Proxy> getChainedProxies() {
		return chainedProxies;
	}

	public void setChainedProxies(List<Proxy> chainedProxies) {
		this.chainedProxies = chainedProxies;
	}

	public Map<String, HttpResponseBuilder> getAnswers() {
		return answers;
	}

	public void setAnswers(Map<String, HttpResponseBuilder> answers) {
		this.answers = answers;
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

		Proxy proxy = new Proxy();
		proxy.host = "slc-entbc-001";
		proxy.port = 80;
		proxy.bypassHosts.add("*srwd*");
		proxy.bypassHosts.add("*stubhub*");
		c.chainedProxies.add(proxy);
		System.out.println(new Gson().toJson(c));
	}
}
