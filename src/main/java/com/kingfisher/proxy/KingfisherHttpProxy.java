package com.kingfisher.proxy;

import com.kingfisher.proxy.config.AllConfig;
import com.kingfisher.proxy.config.ProxyConfig;
import com.kingfisher.proxy.config.RuleConfig;
import com.kingfisher.proxy.intf.HttpRequestHandler;
import com.kingfisher.proxy.util.Utils;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.littleshoot.proxy.*;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kingfisher.proxy.SSLContextProvider.checkAndGenerateCertification;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class KingfisherHttpProxy {

    private static final String HTTPS_SERVER_INTERNAL_HOST = "127.0.0.1";

    private static final AtomicInteger HTTPS_SERVER_INTERNAL_PORT = new AtomicInteger(18443);
    //private static int HTTP_SERVER_INTERNAL_PORT = 18080)

    private static final String PROXY_BY = "Proxy-By";
    private static final String KINGFISH_WEB_DEBUG_PROXY = "Kingfisher-Web-Debug-Proxy";

    private static final Logger logger = LoggerFactory.getLogger(KingfisherHttpProxy.class);

    private SSLContext sslContext;

    {
        try {
            String targetDomain = "example.com";
            checkAndGenerateCertification(targetDomain);
            sslContext = SSLContextProvider.get(targetDomain);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
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

        if (logger.isDebugEnabled()) {
            logger.debug("try to match {} to {}", urlWithoutHttpPrefix, urlMappings.keySet());
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

                    if (httpResponse == null) {
                        //forward to real server
                        return null;
                    }

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

                if (!httpResponse.headers().contains(HttpHeaders.Names.CONTENT_TYPE)) {
                    if ("XMLHttpRequest".equals(httpRequest.headers().get("X-Requested-With"))) {
                        contentType = "application/json;charset=UTF-8";
                        httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);
                    }
                }

                if (!httpResponse.headers().contains(HttpHeaders.Names.CONNECTION)) {
                    if (isKeepAlive(httpRequest)) {
                        httpResponse.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                    }
                }

                httpResponse.headers().set("Generate-By", KINGFISH_WEB_DEBUG_PROXY);

                return httpResponse;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Nothing matched, {}", urlWithoutHttpPrefix);
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
    final Map<Pattern, Map<String, Integer>> urlMappingNamedGroupMappings = new HashMap<>();

    private HttpProxyServer proxyServer;

    public void startProxy(AllConfig configIn) {

        loadConfig(configIn);

        final HttpFiltersSource filtersSource = new HttpFiltersSourceAdapter() {
            public HttpFilters filterRequest(HttpRequest originalRequest) {

                return new HttpFiltersAdapter(originalRequest) {
                    @Override
                    public HttpResponse clientToProxyRequest(HttpObject httpObject) {

                        if (logger.isDebugEnabled()) {
                            logEvent("[proxy] requestPre:", httpObject);
                        }

                        if (httpObject instanceof HttpRequest) {
                            return handleRequestBySelf(new Context().setRequest((HttpRequest) httpObject)
                                    .setUsingHttps(false));
                        }

                        return null;
                    }

                    @Override
                    public HttpObject serverToProxyResponse(HttpObject httpObject) {
                        if (logger.isDebugEnabled()) {
                            logEvent("[proxy] responsePre:", httpObject);
                        }

                        if (httpObject instanceof HttpResponse) {
                            setProxyHeader((HttpResponse) httpObject);
                        }

                        return httpObject;
                    }

                    ;
                };
            }

            ;
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

    }

    private static final String NAMED_GROUP = "%\\w+?%";

    private static final Pattern namedGroupPattern = Pattern.compile(NAMED_GROUP);
    private static final Pattern domainNamePattern = Pattern.compile("([\\w\\d\\-\\_]+\\.)?([\\w\\d\\-\\_]+\\.[\\w\\d\\-\\_]+)");

    public void loadConfig(AllConfig configIn) {

        /**
         * clean up existing
         */
        for (Channel channel : internalHttpsBindChannels) {
            channel.close();
        }

        internalHttpsBindChannels.clear();
        urlMappings.clear();
        urlMappingNamedGroupMappings.clear();

        httpsDomainSet.clear();

        ProxyToServerInterceptor.getInstance().clearHttpsHostPortMapping();

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


        for (RuleConfig ruleConfig : config.getRuleConfigs()) {

            if (ruleConfig.isDisabled()) {
                continue;
            }

            ruleConfig.setScript(fillVariable(ruleConfig.getScript(), config.getVariables()));

            com.kingfisher.proxy.intf.HttpRequestHandler handler = createHandler(ruleConfig);

            String sourceURL = addSourceUrls(ruleConfig.getUrl());

            String patternString = Utils.wildcardToRegex(sourceURL);
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

        logger.info("urlMappings, num:{}", urlMappings.size());

        for (Map.Entry<Pattern, HttpRequestHandler> urlMapping : urlMappings.entrySet()) {
            logger.info("mapping [{}] to [{}]", urlMapping.getKey(), urlMapping.getValue());
            if (urlMappingNamedGroupMappings.containsKey(urlMapping.getKey())) {
                logger.info(" with named group mappings {}", urlMappingNamedGroupMappings.get(urlMapping.getKey()));
            }
        }

        logger.info("Intercept HttpsHostPort : {}", ProxyToServerInterceptor.getInstance().getHttpsHostPortMapping().keySet());
    }

    private HttpRequestHandler createHandler(final RuleConfig ruleConfig) {
        return new JavaScriptHttpRequestHandler(ruleConfig);
    }

    private Set<String> httpsDomainSet = new HashSet<String>();

    private String addSourceUrls(String sourceUrl) {
        String sourceURL = fillVariable(sourceUrl, config.getVariables());

        String hostAndPort = ProxyUtils.parseHostAndPort(sourceURL);
        if (sourceURL.startsWith("https")) {

            if (!hostAndPort.contains(":")) {
                hostAndPort = hostAndPort + ":443";
            }

            String targetDomain = hostAndPort.substring(0, hostAndPort.indexOf(":"));
//            targetDomain = InternetDomainName.from(targetDomain).topPrivateDomain().name();
            Matcher matcher = domainNamePattern.matcher(targetDomain);
            matcher.find();
            targetDomain = matcher.group(2);

            if (!httpsDomainSet.contains(targetDomain)) {

                checkAndGenerateCertification(targetDomain);

                httpsDomainSet.add(targetDomain);

                /**
                 * start an internal https server, proxy forward the https request to it
                 */
                String httpsServerInternalHost = HTTPS_SERVER_INTERNAL_HOST;
                int httpsServerInternalPort = HTTPS_SERVER_INTERNAL_PORT.incrementAndGet();

                //TODO on demand load
                startHttpsInternalServer(targetDomain,
                        httpsServerInternalHost, httpsServerInternalPort,
                        // load the certificate for the particular domain
                        SSLContextProvider.get(targetDomain));

                ProxyToServerInterceptor.getInstance().addHttpsHostPortMapping(hostAndPort, httpsServerInternalHost + ":"
                        + httpsServerInternalPort);
            }

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
    private List<Channel> internalHttpsBindChannels = new ArrayList<Channel>();

    private void startHttpsInternalServer(String domain, String host, int port, final SSLContext sslContextParameter) {

        logger.info("try to bind internal https port for domain:{}, {}:{}", new Object[]{domain, host, port});

        try {
            ServerBootstrap internalHttpsServerBootstrap = new ServerBootstrap();
            internalHttpsServerBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            // Create a default pipeline implementation.
                            ChannelPipeline p = ch.pipeline();

                            SSLEngine engine = sslContextParameter.createSSLEngine();
                            engine.setUseClientMode(false);
                            p.addLast("ssl", new SslHandler(engine));

                            p.addLast("decoder", new HttpRequestDecoder());
                            p.addLast("aggregator", new HttpObjectAggregator(1048576));
                            p.addLast("encoder", new HttpResponseEncoder());
                            p.addLast("deflater", new HttpContentCompressor());
                            p.addLast("handler", createHandlerForInternalServer());
                        }
                    });

            Channel channel = internalHttpsServerBootstrap
                    .bind(host, port).sync().channel();
            internalHttpsBindChannels.add(channel);
            logger.info("bind success!");

        } catch (Exception e) {
            logger.error("startHttpsInternalServer", e);
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
                        logger.info("[internal client] exceptionCaught", cause);
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

            if (request instanceof DefaultFullHttpRequest) {
                // Send the HTTP request (copied)
                FullHttpRequest req0 = ((DefaultFullHttpRequest) request).copy();

                if (logger.isDebugEnabled()) {
                    logEvent("[internal client] send", req0);
                }

                channel.writeAndFlush(req0);
            } else {
                channel.writeAndFlush(request);
            }

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
            logger.info("stop proxy ...");
            //proxyServer.stop();
            clientGroup.shutdownNow();
            for (Channel channel : internalHttpsBindChannels) {
                channel.close();
            }
            bossGroup.shutdownNow();
            workerGroup.shutdownNow();
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


}
