package com.kingfisher.proxy;

import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import com.kingfisher.proxy.config.RuleConfig;
import com.kingfisher.proxy.intf.HttpRequestHandler;
import com.kingfisher.proxy.resolver.Delegator;
import com.kingfisher.proxy.util.HttpResponseBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang3.StringUtils;
import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.impl.ClientToProxyConnection;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingfisher.proxy.config.AllConfig;
import com.kingfisher.proxy.config.ProxyConfig;

public class KingfisherHttpProxy {

	private static final String HTTPS_SERVER_INTERNAL_HOST = "127.0.0.1";

	private static final String NAMED_GROUP = "%\\w+?%";

	private static final int HTTPS_SERVER_INTERNAL_PORT = 18443;
	private static final int HTTP_SERVER_INTERNAL_PORT = 18080;

	private static final String PROXY_BY = "Proxy-By";
	private static final String KINGFISH_WEB_DEBUG_PROXY = "Kingfish-Web-Debug-Proxy";

	private static final Logger logger = LoggerFactory.getLogger(KingfisherHttpProxy.class);

	private final SSLContext sslContext = SSLContextProvider.get();

	// new SelfSignedSslEngineSource(
	// "littleproxy_keystore.jks").getSslContext();

	private final ThreadLocal<Delegator> delegatorLocal = new ThreadLocal<Delegator>(){
        @Override
        protected Delegator initialValue() {
            return new Delegator();
        }
    };

	/**
	 * 
	 * @param context
	 * @return null means cannot handle by self
	 */
	private HttpResponse handleRequestBySelf(Context context) {
		HttpRequest httpRequest = context.getRequest();

		if (ProxyUtils.isCONNECT(httpRequest) && !httpRequest.getUri().contains("/")) {
			// https
			return null;
		}

		String urlWithoutHttpPrefix = httpRequest.getUri();
		if (urlWithoutHttpPrefix.startsWith("http")) {
			urlWithoutHttpPrefix = urlWithoutHttpPrefix.substring(urlWithoutHttpPrefix.indexOf("//") + 2);
		} else {
			urlWithoutHttpPrefix = httpRequest.headers().get(HttpHeaders.Names.HOST) + urlWithoutHttpPrefix;
		}

		for (Map.Entry<Pattern, HttpRequestHandler> e : urlMappings.entrySet()) {
			Pattern pattern = e.getKey();
			Matcher matcher = pattern.matcher(urlWithoutHttpPrefix);

			if (matcher.matches()) {

				HttpResponse httpResponse;

				try {

					if (logger.isInfoEnabled()) {
						logger.info("handle by proxy =>" + urlWithoutHttpPrefix);
					}

                    // handle all named group
                    if (urlMappingNamedGroupMappings.containsKey(pattern)) {
                        Map<String, Integer> namedGroupMapping = urlMappingNamedGroupMappings.get(pattern);
                        for (Map.Entry<String, Integer> me : namedGroupMapping.entrySet()) {
                            String groupValue = matcher.group(me.getValue());
                            String key = me.getKey();
                            key = key.substring(1, key.length() - 1);
                            context.getVariables().put(key, groupValue);
                            if (logger.isDebugEnabled()) {
                                logger.debug("replace named group from {} to {}", me.getKey(), groupValue);
                            }
                        }
                    }

					HttpRequestHandler handler = e.getValue();

					httpResponse = handler.handle(context);

                    if (logger.isInfoEnabled()) {
                        logger.info("httpResponse={}", httpResponse);
                    }

					/*

					if (value instanceof HttpResponseBuilder) {
						httpResponse = ((HttpResponseBuilder) value).build();
					} else if (value instanceof String) {
						URLResolver resolver = localResolver;

						String realUrl = (String) value;

						// handle all named group
						if (urlMappingNamedGroupMappings.containsKey(pattern)) {
							String oldUrl = realUrl;
							Map<String, Integer> namedGroupMapping = urlMappingNamedGroupMappings.get(pattern);
							for (Map.Entry<String, Integer> me : namedGroupMapping.entrySet()) {
								String groupValue = matcher.group(me.getValue());
								realUrl = realUrl.replaceAll(me.getKey(), groupValue);
								if (logger.isDebugEnabled()) {
									logger.debug("replace named group from {} to {}", me.getKey(), groupValue);
								}
							}
							if (logger.isInfoEnabled()) {
								logger.info("after replace all named group, url from {} to {}", oldUrl, realUrl);
							}
						}

						if (realUrl.startsWith("http")) {
							resolver = internetResolver;
						}

						httpResponse = resolver.read(realUrl, context);

					} else if (value instanceof URLResolver) {
						httpResponse = ((URLResolver) value).read((context.isUsingHttps() ? "https://" : "http://")
								+ urlWithoutHttpPrefix, context);
					} else {
						logger.error("Internal error, not support {}", value);
					}

					*/

				} catch (Throwable exp) {

                    logger.error("", exp);

					StringBuilder inCaseFailed = new StringBuilder();
                    inCaseFailed.append("Failed to handle!!! debug info:<br/>");

                    inCaseFailed.append("match by pattern:[" + pattern + "]<br/>");
                    inCaseFailed.append(" -- map to:[" + e.getValue() + "]<br/>");
                    inCaseFailed.append("httpRequest:[" + httpRequest + "]<br/>");
                    inCaseFailed.append("<br/>");

					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					PrintStream ps = new PrintStream(byteArrayOutputStream);
					exp.printStackTrace(ps);
					ps.flush();
					try {
                        inCaseFailed.append(byteArrayOutputStream.toString("UTF-8"));
						ps.close();
					} catch (UnsupportedEncodingException uex) {
					}

					ByteBuf buffer = Unpooled.copiedBuffer("<textarea style=\"width: 100%; height: 100%;\">"
							+ inCaseFailed + "</textarea>", Constants.utf8);

					httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
					httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html;charset=UTF-8");

					HttpHeaders.setContentLength(httpResponse, buffer.readableBytes());
				}

				String contentType;

				if ("XMLHttpRequest".equals(httpRequest.headers().get("X-Requested-With"))) {
					contentType = "application/json;charset=UTF-8";
					httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);
				}

				if (isKeepAlive(httpRequest)) {
					httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
				}

				httpResponse.headers().set("Generate-By", KINGFISH_WEB_DEBUG_PROXY);

				return httpResponse;
			}
		}

  		return null;
	}

	private String fillVariable(String value, Map<String, String> variables) {
		// TODO using pattern to replace
        if (variables == null) {
            return value;
        }
		for (Map.Entry<String, String> e : variables.entrySet()) {
			value = value.replace("${" + e.getKey() + "}", e.getValue());
		}
		return value;
	}

	AllConfig config = null;

	final Map<Pattern, HttpRequestHandler> urlMappings = new LinkedHashMap<Pattern, HttpRequestHandler>();

	// <Pattern, <group_name, group_id>>
	final Map<Pattern, Map<String, Integer>> urlMappingNamedGroupMappings = new HashMap<Pattern, Map<String, Integer>>();

	private HttpProxyServer proxyServer;

	public void startProxy(AllConfig configIn) {

		loadConfig(configIn);

		final HttpFiltersSource filtersSource = new HttpFiltersSourceAdapter() {
			public HttpFilters filterRequest(HttpRequest originalRequest) {

				return new HttpFiltersAdapter(originalRequest) {
					@Override
					public HttpResponse requestPre(HttpObject httpObject) {

						if (logger.isTraceEnabled()) {
							logEvent("[proxy] requestPre:", httpObject);
						}

						if (httpObject instanceof HttpRequest) {
							return handleRequestBySelf(new Context().setRequest((HttpRequest) httpObject)
									.setUsingHttps(false));
						}

						return null;
					}

					public HttpObject responsePre(HttpObject httpObject) {
						if (logger.isTraceEnabled()) {
							logEvent("[proxy] responsePre:", httpObject);
						}

						if (httpObject instanceof HttpResponse) {
							setProxyHeader((HttpResponse) httpObject);
						}

						return httpObject;
					};
				};
			};
		};

		HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap().withAllowLocalOnly(false);
		
		if (config.getProxyHost() != null) {
			try {
				bootstrap.withAddress(new InetSocketAddress(InetAddress.getByName(config.getProxyHost()), config.getProxyPort()));
			} catch (UnknownHostException e) {
				logger.error("failed to bind", e);
				System.exit(-1);
			}
		} else {
			bootstrap.withListenOnAllAddresses(true).withPort(config.getProxyPort());
		}
		
		bootstrap.withFiltersSource(filtersSource);

		if (config.getChainedProxies() != null && !config.getChainedProxies().isEmpty()) {
			bootstrap.withChainProxyManager(new ChainedProxyManager() {
				@Override
				public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {

					if (!httpRequest.headers().contains(Constants.HEADER_FLAG_NO_PROXY)) {
						for (final ProxyConfig proxy : config.getChainedProxies()) {
							String host = httpRequest.headers().get(HttpHeaders.Names.HOST);

							if (proxy.isAllowed(host)) {
								chainedProxies.add(proxy.getChainedProxy());
							}
						}
					}

					chainedProxies.add(ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION);
				}
			});
		}

		proxyServer = bootstrap.start();

		/**
		 * start an internal https server, proxy forward the https request to it
		 */
		startHttpsInternalServer();

	}

	Pattern namedGroupPattern = Pattern.compile(NAMED_GROUP);

	public void loadConfig(AllConfig configIn) {
		this.config = configIn;

		if (config.getVariables() != null) {
			// handle variable using another variable case
			Map<String, String> variables = config.getVariables();
			for (int i = 0; i < 10; i++) {
				boolean doSomething = false;
				for (Map.Entry<String, String> e : variables.entrySet()) {
					if (e.getValue().contains("${")) {
						String newValue = fillVariable(e.getValue(), variables);
						if (!newValue.contains("${")) {
							doSomething = true;
							variables.put(e.getKey(), newValue);
						}
					}
				}
				if (!doSomething) {
					break;
				}
			}
			logger.info("variables:{}", variables);
		}

		urlMappings.clear();
		urlMappingNamedGroupMappings.clear();
		ClientToProxyConnection.clearHttpsHostPortMapping();

		for (RuleConfig ruleConfig : config.getRuleConfigs()) {

            if (ruleConfig.isDisabled()) {
                continue;
            }

            ruleConfig.setScript(fillVariable(ruleConfig.getScript(), config.getVariables()));

			com.kingfisher.proxy.intf.HttpRequestHandler handler = createHandler(ruleConfig);

			String sourceURL = addSourceUrls(ruleConfig.getUrl());

            String patternString = ProxyUtils.wildcardToRegex(sourceURL);
            Map<String, Integer> variables = null;
            Matcher matcher = namedGroupPattern.matcher(patternString);
            int groupId = 1;
            while (matcher.find()) {
                if (variables == null) {
                    variables = new HashMap<String, Integer>();
                }
                String group = matcher.group();
                variables.put(group, groupId++);
            }
            patternString = patternString.replaceAll(NAMED_GROUP, "(.*?)");
            Pattern pattern = Pattern.compile(patternString);

			urlMappings.put(pattern, handler);

            if (variables != null) {
                urlMappingNamedGroupMappings.put(pattern, variables);
            }

        }

		/*

		if (config.getAnswers() != null) {
			for (Map.Entry<String, HttpResponseBuilder> e : config.getAnswers().entrySet()) {
				String sourceURL = addSourceUrls(e.getKey());
				urlMappings.put(Pattern.compile(ProxyUtils.wildcardToRegex(sourceURL)), e.getValue());
			}
		}

		if (config.getMappings() != null) {
			for (Map.Entry<String, String> e : config.getMappings().entrySet()) {
				String sourceURL = addSourceUrls(e.getKey());
				String patternString = ProxyUtils.wildcardToRegex(sourceURL);
				Map<String, Integer> variables = null;
				Matcher matcher = namedGroupPattern.matcher(patternString);
				int groupId = 1;
				while (matcher.find()) {
					if (variables == null) {
						variables = new HashMap<String, Integer>();
					}
					variables.put(matcher.group(), groupId++);
				}
				patternString = patternString.replaceAll(NAMED_GROUP, "(.*?)");
				Pattern pattern = Pattern.compile(patternString);
				urlMappings.put(pattern, fillVariable(e.getValue(), config.getVariables()));
				if (variables != null) {
					urlMappingNamedGroupMappings.put(pattern, variables);
				}
			}
		}

		if (config.getHandlers() != null) {
			for (Map.Entry<String, HandlerConfig> e : config.getHandlers().entrySet()) {
				try {
					Class<?> clazz = Class.forName(e.getValue().getClassName());
					logger.info("load handler: {} for {}", clazz, e.getKey());
					if (CustomHandler.class.isAssignableFrom(clazz)) {
						String sourceURL = addSourceUrls(e.getKey());
						CustomHandler newInstance = (CustomHandler) (clazz.newInstance());
						newInstance.initial(e.getValue().getParameters());
						urlMappings.put(Pattern.compile(ProxyUtils.wildcardToRegex(sourceURL)), newInstance);
					} else {
						logger.error("{} is not sub-type of {}", clazz, CustomHandler.class);
					}
				} catch (Exception ex) {
					logger.error("", e);
				}
			}
		}

		*/

		logger.info("urlMappings, num:{}", urlMappings.size());

		for (Map.Entry<Pattern, HttpRequestHandler> urlMapping : urlMappings.entrySet()) {
			logger.info("mapping [{}] to [{}]", urlMapping.getKey(), urlMapping.getValue());
			if (urlMappingNamedGroupMappings.containsKey(urlMapping.getKey())) {
				logger.info(" with named group mappings {}", urlMappingNamedGroupMappings.get(urlMapping.getKey()));
			}
		}

		logger.info("Intercept HttpsHostPort : {}", ClientToProxyConnection.getHttpsHostPortMapping().keySet());
	}

	private HttpRequestHandler createHandler(final RuleConfig ruleConfig) {

        return new MyHttpRequestHandler(ruleConfig);
	}

	private String addSourceUrls(String sourceUrl) {
		String sourceURL = fillVariable(sourceUrl, config.getVariables());

		String hostAndPort = ProxyUtils.parseHostAndPort(sourceURL);
		if (sourceURL.startsWith("https")) {
			if (!hostAndPort.contains(":")) {
				hostAndPort = hostAndPort + ":443";
			}
			ClientToProxyConnection.addHttpsHostPortMapping(hostAndPort, HTTPS_SERVER_INTERNAL_HOST + ":"
					+ HTTPS_SERVER_INTERNAL_PORT);
		} else {

		}

		sourceURL = sourceURL.replace("http://", "").replace("https://", "");

		if (!sourceURL.contains("/")) {
			sourceURL = sourceURL + "/";
		}
		return sourceURL;
	}

	private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
	private final EventLoopGroup workerGroup = new NioEventLoopGroup();
	private Channel internalHttpsBindChannel;
	private final ServerBootstrap internalHttpsServerBootstrap = new ServerBootstrap();

	private void startHttpsInternalServer() {
		try {
			internalHttpsServerBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						public void initChannel(SocketChannel ch) throws Exception {
							// Create a default pipeline implementation.
							ChannelPipeline p = ch.pipeline();

							SSLEngine engine = sslContext.createSSLEngine();
							engine.setUseClientMode(false);
							p.addLast("ssl", new SslHandler(engine));

							p.addLast("decoder", new HttpRequestDecoder());
							p.addLast("aggregator", new HttpObjectAggregator(1048576));
							p.addLast("encoder", new HttpResponseEncoder());
							p.addLast("deflater", new HttpContentCompressor());
							p.addLast("handler", createHandlerForInternalServer());
						}
					});

            logger.info("try to bind internal https port, {}:{}", HTTPS_SERVER_INTERNAL_HOST, HTTPS_SERVER_INTERNAL_PORT);
			internalHttpsBindChannel = internalHttpsServerBootstrap
					.bind(HTTPS_SERVER_INTERNAL_HOST, HTTPS_SERVER_INTERNAL_PORT).sync().channel();
            logger.info("bind success!");

		} catch (Exception e) {
			logger.error("startHttpsInternalServer", new RuntimeException(e));
		}
	}

	String identifyHostAndPort(HttpRequest httpRequest) {
		String hostAndPort = ProxyUtils.parseHostAndPort(httpRequest);
		if (StringUtils.isBlank(hostAndPort)) {
			List<String> hosts = httpRequest.headers().getAll(HttpHeaders.Names.HOST);
			if (hosts != null && !hosts.isEmpty()) {
				hostAndPort = hosts.get(0);
			}
		}

		return hostAndPort;
	}

	public interface ResponseCallback {
		void handle(HttpResponse response);
	}

	private final Bootstrap clientBoostrap = new Bootstrap();
	private final EventLoopGroup clientGroup = new NioEventLoopGroup();

	private final static AttributeKey<HttpRequest> HttpRequestKey = AttributeKey.valueOf("HttpRequest");
	private final static AttributeKey<ResponseCallback> ResponseCallbackKey = AttributeKey.valueOf("ResponseCallback");

	{
		clientBoostrap.group(clientGroup);

		clientBoostrap.channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {

			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				// Create a default pipeline implementation.
				ChannelPipeline p = ch.pipeline();

				// p.addLast("log", new LoggingHandler(LogLevel.INFO));

				// Enable HTTPS if necessary.
				SSLEngine engine = sslContext.createSSLEngine();
				engine.setUseClientMode(true);

				p.addLast("ssl", new SslHandler(engine));

				p.addLast("codec", new HttpClientCodec());

				// automatic content decompression.
				p.addLast("inflater", new HttpContentDecompressor());

				// handle HttpChunks.
				p.addLast("aggregator", new HttpObjectAggregator(1048576));

				p.addLast("handler", new SimpleChannelInboundHandler<HttpObject>() {

					@Override
					public void channelRead0(ChannelHandlerContext ctx2, HttpObject msg) throws Exception {
						if (logger.isDebugEnabled()) {
							HttpRequest httpRequest = ctx2.channel().attr(HttpRequestKey).get();
							logEvent("[internal client] receive", httpRequest, msg);
						}

						if (msg instanceof HttpResponse) {
							HttpResponse response = (HttpResponse) msg;
							ctx2.channel().attr(ResponseCallbackKey).get().handle(response);
							ctx2.close();
						}

					}

					@Override
					public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
						logger.info("[internal client] exceptionCaught:{}", cause, cause.getMessage());
						ctx.close();
					}
				});
			}
		});
	}

	void forwardHttpRequestToRealServer(final HttpRequest request, final ResponseCallback calback) {
		final String url = request.headers().get(HttpHeaders.Names.HOST) + request.getUri();

		if (logger.isInfoEnabled()) {
			logger.info("[internal client] forward request={} to real server", url);
		}

		// Configure the client.
		try {
			String hostAndPort = identifyHostAndPort(request);

			String host;
			int port;
			if (hostAndPort.contains(":")) {
				host = StringUtils.substringBefore(hostAndPort, ":");
				String portString = StringUtils.substringAfter(hostAndPort, ":");
				port = Integer.parseInt(portString);
			} else {
				host = hostAndPort;
				port = 443;
			}

			if (logger.isDebugEnabled()) {
				logger.debug("connect to {}:{}", host, port);
			}

			// Make the connection attempt.
			// TODO2 : reuse channel?
			Channel channel = clientBoostrap.connect(host, port).sync().channel();

			// bind the related to channel
			channel.attr(HttpRequestKey).set(request);
			channel.attr(ResponseCallbackKey).set(calback);

			// Send the HTTP request (copied)
			FullHttpRequest req0 = ((DefaultFullHttpRequest) request).copy();

			if (logger.isDebugEnabled()) {
				logEvent("[internal client] send", req0);
			}

			channel.writeAndFlush(req0);

			// no need the following, because of async mode
			// Wait for the server to close the connection.
			// channel.closeFuture().sync();

		} catch (Exception e) {
			logger.info("[internal client] catchException ", e);
		}
	}

	private void setProxyHeader(HttpResponse httpResponse) {
		httpResponse.headers().set(PROXY_BY, KINGFISH_WEB_DEBUG_PROXY);
	}

	public void stop() {
		try {
			proxyServer.stop();
			clientGroup.shutdownGracefully();
			internalHttpsBindChannel.closeFuture().sync();
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		} catch (Exception e) {
			logger.error("when stop", e);
		}
	}

	private SimpleChannelInboundHandler<Object> createHandlerForInternalServer() {
		return new SimpleChannelInboundHandler<Object>() {

			@Override
			public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
				ctx.flush();
			}

			@Override
			protected void channelRead0(final ChannelHandlerContext httpsServerConnectionContext, Object msg) {
				if (logger.isDebugEnabled()) {
					logEvent("[internal https server] receive", msg);
				}

				if (msg instanceof HttpRequest) {

					final HttpRequest request = (HttpRequest) msg;

					if (is100ContinueExpected(request)) {
						send100Continue(httpsServerConnectionContext);
						return;
					}

					HttpResponse httpResponse = handleRequestBySelf(new Context().setRequest(request)
							.setUsingHttps(true));

					if (httpResponse != null) {
						// OK, we are not a pure PROXY, we did some thing which belong to server
						httpsServerConnectionContext.writeAndFlush(httpResponse);
						if (logger.isDebugEnabled()) {
							logEvent("[internal https server] send response", request, httpResponse);
						}
						return;
					}

					// let the real server process it like a real PROXY
					forwardHttpRequestToRealServer(request, new ResponseCallback() {
						@Override
						public void handle(HttpResponse resp) {
							// Write the response.
							FullHttpResponse response = ((FullHttpResponse) resp).copy();
							setProxyHeader(response);
							if (logger.isDebugEnabled()) {
								logEvent("[internal https server] forward response", request, response);
							}
							httpsServerConnectionContext.writeAndFlush(response);
						}
					});
				}

			}

			private void send100Continue(ChannelHandlerContext ctx) {
				FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
				ctx.write(response);
			}

			@Override
			public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
				logger.info("[internal https server] exceptionCaught:{} - {}", cause, cause.getMessage());
				ctx.close();
			}
		};
	}

	private static final String logPrefix = "    ";

	private static void logEvent(String title, Object message) {
		if (message instanceof HttpRequest) {
			HttpRequest request = (HttpRequest) message;
			logEvent(title, request, request);
		} else {
			logEvent(title, null, message);
		}
	}

	private static void logEvent(String title, HttpRequest request) {
		logEvent(title, request, request);
	}

	private static void logEvent(String title, HttpRequest request, Object message) {
		String msg = message.toString();
		msg = logPrefix + msg.replace("\n", "\n" + logPrefix);
		logger.debug("\n>>>>>>>>>>>>>\n"
				+ title + (request != null ? (" <" + request.headers().get(HttpHeaders.Names.HOST) + request
						.getUri() + ">") : "") + "\n>>>>>>>>>>>>>\n" + msg + "\n<<<<<<<<<<<<<<<\n");
	}

    private class MyHttpRequestHandler implements HttpRequestHandler {

        private final RuleConfig ruleConfig;

        public MyHttpRequestHandler(RuleConfig ruleConfig) {
            this.ruleConfig = ruleConfig;
        }

        @Override
        public String toString() {
            return "MyHttpRequestHandler{" +
                    "ruleConfig=" + ruleConfig +
                    '}';
        }

        @Override
        public HttpResponse handle(Context context) {
            try {
                final ScriptEngineManager factory = new ScriptEngineManager();
                final ScriptEngine engine = factory.getEngineByName("Nashorn");

                final Bindings bindings = engine.createBindings();

                bindings.put("context", context);
                Delegator delegator = delegatorLocal.get();
                delegator.setContext(context);
                bindings.put("delegator", delegator);
                bindings.put("responseBuilder", new HttpResponseBuilder());

                for (Map.Entry<String, String> e : context.getVariables().entrySet()) {
                    bindings.put(e.getKey(), e.getValue());
                }

                //TODO add cache binding

                return (HttpResponse) engine.eval(ruleConfig.getScript(), bindings);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }
}
