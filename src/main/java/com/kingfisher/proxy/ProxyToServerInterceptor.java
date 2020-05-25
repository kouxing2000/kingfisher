package com.kingfisher.proxy;

import com.kingfisher.proxy.util.Utils;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ProxyToServerInterceptor {

    private static Logger logger = LoggerFactory.getLogger(ProxyToServerInterceptor.class);

    private ProxyToServerInterceptor() {

    }

    private static ProxyToServerInterceptor instance = new ProxyToServerInterceptor();

    public static ProxyToServerInterceptor getInstance() {
        return instance;
    }



    private final Map<String, String> httpsHostPortMapping = new ConcurrentHashMap<String, String>();
    public Map<String, String> getHttpsHostPortMapping() {
        return httpsHostPortMapping;
    }

    private final Map<Pattern, String> httpsPatternHostPortMapping = new ConcurrentHashMap<Pattern, String>();

    public void addHttpsHostPortMapping(String sourceHostPort, String targetHostPort) {
        httpsHostPortMapping.put(sourceHostPort, targetHostPort);

        String wildCardSource = sourceHostPort;
        String regex = Utils.wildcardToRegex(wildCardSource);
        Pattern pattern = Pattern.compile(regex);
        httpsPatternHostPortMapping.put(pattern, targetHostPort);
    }

    public void clearHttpsHostPortMapping() {
        httpsHostPortMapping.clear();
        httpsPatternHostPortMapping.clear();
    }

    public String hackServerHostAndPort(HttpRequest httpRequest, String oriServerHostAndPort) {
        String serverHostAndPort = oriServerHostAndPort;

        if (!serverHostAndPort.contains(":")) {
            serverHostAndPort = serverHostAndPort + ":443";
        }

        if (httpsHostPortMapping.containsKey(serverHostAndPort)) {
            serverHostAndPort = httpsHostPortMapping.get(serverHostAndPort);
        } else {
            for (Map.Entry<Pattern, String> entry : httpsPatternHostPortMapping.entrySet()) {
                if (entry.getKey().matcher(serverHostAndPort).matches()) {
                    serverHostAndPort = entry.getValue();
                }
            }
        }

        if (!oriServerHostAndPort.equals(serverHostAndPort)) {

            httpRequest.headers().add(Constants.HEADER_FLAG_NO_PROXY, "TRUE");

            logger.debug("replace " + oriServerHostAndPort + " to " + serverHostAndPort);
        } else {
            logger.debug("no replacement for {}", oriServerHostAndPort);
        }

        return serverHostAndPort;
    }

}
