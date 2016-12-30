package com.kingfisher.proxy;

import com.kingfisher.proxy.config.RuleConfig;
import com.kingfisher.proxy.intf.HttpRequestHandler;
import com.kingfisher.proxy.resolver.Delegator;
import com.kingfisher.proxy.util.HttpResponseBuilder;
import io.netty.handler.codec.http.HttpResponse;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;

public class JavaScriptHttpRequestHandler implements HttpRequestHandler {

    private final RuleConfig ruleConfig;

    public JavaScriptHttpRequestHandler(RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    private final ThreadLocal<Delegator> delegatorLocal = new ThreadLocal<Delegator>() {
        @Override
        protected Delegator initialValue() {
            return new Delegator();
        }
    };

    @Override
    public String toString() {
        return "JavaScriptHttpRequestHandler{" +
                "ruleConfig=" + ruleConfig +
                '}';
    }

    static final ScriptEngineManager factory = new ScriptEngineManager();
    static final ScriptEngine engine = factory.getEngineByName("Nashorn");

    @Override
    public HttpResponse handle(Context context) {
        try {

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