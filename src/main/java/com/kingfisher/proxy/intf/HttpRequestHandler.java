package com.kingfisher.proxy.intf;

import com.kingfisher.proxy.Context;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Created by weili5 on 12/28/16.
 */
public interface HttpRequestHandler {
    HttpResponse handle(Context context);
}
