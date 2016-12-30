package com.kingfisher.proxy.resolver;

import com.kingfisher.proxy.Context;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Created by weili5 on 12/29/16.
 */
public class Delegator {

    private final URLResolver localResolver = new LocalFileResolver();
    private final URLResolver internetResolver = new InternetFileResolver();

    private Context context;

    public void setContext(Context context) {
        this.context = context;
    }

    public HttpResponse read(String url) throws Exception {
        if (url.startsWith("http")) {
            return internetResolver.read(url, context);
        }
        return localResolver.read(url, context);
    }
}
