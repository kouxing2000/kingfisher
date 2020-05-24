package com.kingfisher.proxy.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.kingfisher.proxy.util.Utils;
import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.impl.ProxyUtils;

public class ProxyConfig {
	private String host;
	private int port;
	private boolean disabled;

	private List<String> bypassHosts;

	public ProxyConfig() {
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

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
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
					throw new RuntimeException(cause);
				}

			};
		}
		return chainedProxy;
	}

	private transient List<Pattern> bypassHostPatterns;

	public boolean isAllowed(String host) {
		if (disabled) {
			return false;
		}

		if (bypassHostPatterns == null) {
			synchronized (this) {
				bypassHostPatterns = new ArrayList<Pattern>();
				for (String bypassHost : bypassHosts) {
					String regex = Utils.wildcardToRegex(bypassHost);
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