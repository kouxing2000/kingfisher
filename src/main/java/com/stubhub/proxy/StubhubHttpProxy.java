package com.stubhub.proxy;

import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.commons.io.FileUtils;
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

import com.google.gson.Gson;
import com.stubhub.proxy.Config.Proxy;
import com.stubhub.proxy.resolver.InternetFileResover;
import com.stubhub.proxy.resolver.LocalFileResolver;
import com.stubhub.proxy.resolver.URLResolver;

public class StubhubHttpProxy {

	private static final String NAMED_GROUP = "<\\w+?>";

	private static final String DEFAULT_CONFIG_JSON = "stubhubproxy.config.json";

	private static final int HTTPS_SERVER_INTERNAL_PORT = 18443;
	private static final int HTTP_SERVER_INTERNAL_PORT = 18080;

	private static final String PROXY_BY = "Proxy-By";
	private static final String STUBHUB_PROXY = "Stubhub-Proxy";

	private static final Logger logger = LoggerFactory.getLogger(StubhubHttpProxy.class);

	private final SSLContext sslContext = SSLContextProvider.get();

	// new SelfSignedSslEngineSource(
	// "littleproxy_keystore.jks").getSslContext();

	private URLResolver localResolver = new LocalFileResolver();
	private URLResolver internetResolver = new InternetFileResover();

	private HttpResponse handleRequestBySelf(HttpRequest httpRequest) {
		// logger.info(httpRequest.toString());

		if (ProxyUtils.isCONNECT(httpRequest) && !httpRequest.getUri().contains("/")) {
			// https
			return null;
		}

		String url = httpRequest.getUri();
		if (url.startsWith("http")) {
			url = url.substring(url.indexOf("//") + 2);
		} else {
			url = httpRequest.headers().get("Host") + url;
		}

		// logger.info("URL:" + url);

		for (Map.Entry<Pattern, Object> e : urlMappings.entrySet()) {
			Pattern pattern = e.getKey();
			Matcher matcher = pattern.matcher(url);

			if (matcher.matches()) {

				HttpResponse httpResponse = null;

				try {

					logger.info("handle by proxy =>" + url);

					Object value = e.getValue();

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
								logger.debug("replace named group from {} to {}", me.getKey(), groupValue);
							}
							logger.info("after replace all named group, url from {} to {}", oldUrl, realUrl);
						}

						if (realUrl.startsWith("http")) {
							resolver = internetResolver;
						}

						httpResponse = resolver.read(realUrl, new Context().setRequest(httpRequest));
					}

				} catch (Exception exp) {

					String inCaseFailed = "failed to handle!!! debug info:\n";

					inCaseFailed += "match by pattern:[" + pattern + "]\n";
					inCaseFailed += " map to:[" + e.getValue() + "]\n";
					inCaseFailed += "httpRequest:[" + httpRequest + "]\n";
					inCaseFailed += "\n";

					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					PrintStream ps = new PrintStream(byteArrayOutputStream);
					exp.printStackTrace(ps);
					ps.flush();
					try {
						inCaseFailed += byteArrayOutputStream.toString("UTF-8");
						ps.close();
					} catch (UnsupportedEncodingException uex) {
					}

					ByteBuf buffer = Unpooled.copiedBuffer("<textarea style=\"width: 100%; height: 100%;\">"
							+ inCaseFailed + "</textarea>", Constants.utf8);

					httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
					httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html;charset=UTF-8");

					HttpHeaders.setContentLength(httpResponse, buffer.readableBytes());
				}

				String contentType = httpResponse.headers().get(HttpHeaders.Names.CONTENT_TYPE);

				if ("XMLHttpRequest".equals(httpRequest.headers().get("X-Requested-With"))) {
					contentType = "application/json;charset=UTF-8";
					httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);
				}

				if (isKeepAlive(httpRequest)) {
					httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
				}

				httpResponse.headers().set("Generate-By", STUBHUB_PROXY);

				return httpResponse;
			}
		}

		return null;
	}

	private String fillVariable(String value, Map<String, String> variables) {
		//TODO using string pattern to replace
		for (Map.Entry<String, String> e : variables.entrySet()) {
			value = value.replace("${" + e.getKey() + "}", e.getValue());
		}
		return value;
	}

	Config config = null;

	final Map<Pattern, Object> urlMappings = new LinkedHashMap<Pattern, Object>();

	// <Pattern, <group_name, group_id>>
	final Map<Pattern, Map<String, Integer>> urlMappingNamedGroupMappings = new HashMap<Pattern, Map<String, Integer>>();

	static final ScheduledExecutorService newSingleThreadScheduledExecutor = Executors
			.newSingleThreadScheduledExecutor();

	static long lastConfigFileModifiedTS = 0;

	public static void main(String[] args) {

		// read from arguments
		final StubhubHttpProxy proxy = new StubhubHttpProxy();

		Config config = null;

		if (args != null && args.length >= 1) {
			final File configFile = new File(args[0]);

			if (configFile.exists()) {
				newSingleThreadScheduledExecutor.scheduleAtFixedRate(new Runnable() {

					@Override
					public void run() {
						try {
							if (configFile.lastModified() > lastConfigFileModifiedTS) {
								logger.info("detect config file {} changed", configFile.getAbsolutePath());
								Config config = new Gson().fromJson(FileUtils.readFileToString(configFile),
										Config.class);
								proxy.loadConfig(config);
								lastConfigFileModifiedTS = configFile.lastModified();
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}, 5, 5, TimeUnit.SECONDS);

				try {
					config = new Gson().fromJson(FileUtils.readFileToString(configFile), Config.class);
					lastConfigFileModifiedTS = configFile.lastModified();
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			} else {
				logger.error("Failed to load config file: {} , use default config", args[0]);
			}

		} else {
			logger.error("Usage: java -jar porxy.jar <config file name>, use default config");
		}

		if (config == null) {
			try {
				InputStream resourceAsStream = ClassLoader.getSystemResourceAsStream(DEFAULT_CONFIG_JSON);
				config = new Gson().fromJson(new InputStreamReader(resourceAsStream, Constants.utf8), Config.class);
				resourceAsStream.close();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}

		proxy.startProxy(config);
	}

	private HttpProxyServer proxyServer;

	private void startProxy(Config configIn) {

		loadConfig(configIn);

		final HttpFiltersSource filtersSource = new HttpFiltersSourceAdapter() {
			public HttpFilters filterRequest(HttpRequest originalRequest) {

				return new HttpFiltersAdapter(originalRequest) {
					@Override
					public HttpResponse requestPre(HttpObject httpObject) {
						// logger.info("\n----------->" + httpObject.toString()
						// + ">-----------");

						if (httpObject instanceof HttpRequest) {
							return handleRequestBySelf((HttpRequest) httpObject);
						}

						return null;
					}

					public HttpObject responsePre(HttpObject httpObject) {
						// logger.info("\n-----------<" + httpObject.toString()
						// + "<-----------");

						if (httpObject instanceof HttpResponse) {
							setProxyHeader((HttpResponse) httpObject);
						}
						return httpObject;
					};
				};
			};
		};

		HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap().withPort(config.getProxyPort())
				.withFiltersSource(filtersSource);

		if (!config.getChainedProxies().isEmpty()) {
			bootstrap.withChainProxyManager(new ChainedProxyManager() {
				@Override
				public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {

					if (!httpRequest.headers().contains(Constants.HEADER_FLAG_NO_PROXY)) {
						for (final Proxy proxy : config.getChainedProxies()) {
							String host = httpRequest.headers().get("Host");

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
		 * start a internal https server, proxy forward the https request to it
		 */
		startHttpsInternalServer();

	}

	Pattern namedGroupPattern = Pattern.compile(NAMED_GROUP);

	private void loadConfig(Config configIn) {
		this.config = configIn;

		{
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

		logger.info("urlMappings, num:{}", urlMappings.size());

		for (Map.Entry<Pattern, Object> urlMapping : urlMappings.entrySet()) {
			logger.info("mapping [{}] to [{}]", urlMapping.getKey(), urlMapping.getValue());
			if (urlMappingNamedGroupMappings.containsKey(urlMapping.getKey())) {
				logger.info(" with named group mappings {}", urlMappingNamedGroupMappings.get(urlMapping.getKey()));
			}
		}

		logger.info("Intercept HttpsHostPort : {}", ClientToProxyConnection.getHttpsHostPortMapping().keySet());
	}

	private String addSourceUrls(String sourceUrl) {
		String sourceURL = fillVariable(sourceUrl, config.getVariables());

		String hostAndPort = ProxyUtils.parseHostAndPort(sourceURL);
		if (sourceURL.startsWith("https")) {
			if (!hostAndPort.contains(":")) {
				hostAndPort = hostAndPort + ":443";
			}
			ClientToProxyConnection.addHttpsHostPortMapping(hostAndPort, "localhost:" + HTTPS_SERVER_INTERNAL_PORT);
		} else {

		}

		sourceURL = sourceURL.replace("http://", "").replace("https://", "");

		if (!sourceURL.contains("/")) {
			sourceURL = sourceURL + "/";
		}
		return sourceURL;
	}

	EventLoopGroup bossGroup = new NioEventLoopGroup(1);
	EventLoopGroup workerGroup = new NioEventLoopGroup();

	private Thread inernalHttpsThread;

	private void startHttpsInternalServer() {
		inernalHttpsThread = new Thread() {
			public void run() {
				try {
					ServerBootstrap b = new ServerBootstrap();
					b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
							.childHandler(new ChannelInitializer<SocketChannel>() {
								@Override
								public void initChannel(SocketChannel ch) throws Exception {
									// Create a default pipeline implementation.
									ChannelPipeline p = ch.pipeline();

									// Uncomment the following line if you want
									// HTTPS
									SSLEngine engine = sslContext.createSSLEngine();
									engine.setUseClientMode(false);
									p.addLast("ssl", new SslHandler(engine));

									p.addLast("decoder", new HttpRequestDecoder());
									// Uncomment the following line if you don't
									// want to handle HttpChunks.
									p.addLast("aggregator", new HttpObjectAggregator(1048576));
									p.addLast("encoder", new HttpResponseEncoder());
									// Remove the following line if you don't
									// want automatic content compression.
									p.addLast("deflater", new HttpContentCompressor());
									p.addLast("handler", createHandlerForInternalServer());
								}
							});

					Channel ch = b.bind(HTTPS_SERVER_INTERNAL_PORT).sync().channel();
					// ch.closeFuture().sync();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					// bossGroup.shutdownGracefully();
					// workerGroup.shutdownGracefully();
				}
			};
		};
		inernalHttpsThread.start();
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

	final EventLoopGroup clientGroup = new NioEventLoopGroup();

	private void forwardHttpRequestToRealServer(final HttpRequest request, final ResponseCallback calback) {
		String url = request.headers().get("Host") + request.getUri();

		if (logger.isInfoEnabled()) {
			logger.info("forward to server:" + url);
		}

		// Configure the client.
		// TODO reuse?
		final Bootstrap clientBoostrap = new Bootstrap();
		try {
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

					// Remove the following line if you don't want automatic
					// content decompression.
					p.addLast("inflater", new HttpContentDecompressor());

					// Uncomment the following line if you don't want to handle
					// HttpChunks.
					p.addLast("aggregator", new HttpObjectAggregator(1048576));

					p.addLast("handler", new SimpleChannelInboundHandler<HttpObject>() {

						@Override
						public void channelRead0(ChannelHandlerContext ctx2, HttpObject msg) throws Exception {
							// logger.info("<<<<<<<<<<<<");
							// logger.info(msg);
							// logger.info("<<<<<<<<<<<<");

							if (msg instanceof HttpResponse) {
								HttpResponse response = (HttpResponse) msg;
								calback.handle(response);
								ctx2.close();
							}

						}

						@Override
						public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
							cause.printStackTrace();
							ctx.close();
						}
					});
				}
			});

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
				logger.debug("connect to " + host + ":" + port);
			}

			// Make the connection attempt.
			// TODO : reuse channel
			Channel ch = clientBoostrap.connect(host, port).sync().channel();

			// Send the HTTP request.
			FullHttpRequest req0 = ((DefaultFullHttpRequest) request).copy();
			req0.setUri("https://" + host + request.getUri());

			if (logger.isDebugEnabled()) {
				logger.debug("access " + req0.getUri());
			}

			ch.writeAndFlush(req0);

			// Wait for the server to close the connection.
			// ch.closeFuture().sync();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// Shut down executor threads to exit.
			// clientGroup.shutdownGracefully();
		}
	}

	private void setProxyHeader(HttpResponse httpResponse) {
		httpResponse.headers().set(PROXY_BY, STUBHUB_PROXY);
	}

	public void stop() {
		try {
			proxyServer.stop();
			clientGroup.shutdownGracefully();
			inernalHttpsThread.interrupt();
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		} catch (Exception e) {
			e.printStackTrace();
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
					logger.debug("\n>>>>>>>>>>>>>\n" + "http(s) server receive:" + "\n>>>>>>>>>>>>>\n" + msg
							+ "\n>>>>>>>>>>>>>\n");
				}

				if (msg instanceof HttpRequest) {

					HttpRequest request = (HttpRequest) msg;

					if (is100ContinueExpected(request)) {
						send100Continue(httpsServerConnectionContext);
						return;
					}

					HttpResponse handleRequestBySelf = handleRequestBySelf(request);

					if (handleRequestBySelf != null) {
						httpsServerConnectionContext.writeAndFlush(handleRequestBySelf);
						return;
					}

					forwardHttpRequestToRealServer(request, new ResponseCallback() {
						@Override
						public void handle(HttpResponse resp) {
							// Write the response.
							FullHttpResponse response = ((FullHttpResponse) resp).copy();
							setProxyHeader(response);
							if (logger.isDebugEnabled()) {
								logger.debug("\n<<<<<<<<<<<\n" + "http(s) server sent back:" + "\n<<<<<<<<<<<\n"
										+ response + "\n<<<<<<<<<<<\n");
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
				cause.printStackTrace();
				ctx.close();
			}
		};
	}
}
