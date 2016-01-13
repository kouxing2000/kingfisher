package com.kingfisher.proxy.custom;

import com.kingfisher.proxy.resolver.InternetFileResolver;
import com.kingfisher.proxy.Context;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by weili5 on 2015/11/2.
 */
public class ReplaceHandler extends InternetFileResolver implements CustomHandler{

    private static final Logger logger = LoggerFactory.getLogger(ReplaceHandler.class);

    private InternetFileResolver internetFileResolver = new InternetFileResolver();

    private Map<String, String> mappings = new HashMap<String, String>();

    @Override
    public void initial(Map<String, String> parameters) {
        logger.info("initial with parameter={}", parameters);
        mappings.clear();
        mappings.putAll(parameters);
    }

    @Override
    protected byte[] generateResponseBytes(byte[] originalBytes, Context context) {

        if (mappings.isEmpty()) {
            return originalBytes;
        }

        //TEMP only for html request
        if (!"text/html;charset=UTF-8".equals(context.getOriginResponse().getHeaders(HttpHeaders.Names.CONTENT_TYPE)[0].getValue())) {
            return originalBytes;
        }

        try {
            String originalContent = new String(originalBytes, "UTF-8");

            String newContent = originalContent;

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                logger.info("try replace {} to {}", entry.getKey(), entry.getValue());

                newContent = newContent.replaceAll(entry.getKey(), entry.getValue());
            }

            return newContent.getBytes("UTF-8");

        } catch (Exception e) {
            logger.error("", e);
            return originalBytes;
        }
    }

}
