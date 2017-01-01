package com.kingfisher.proxy;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLContextProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSLContextProvider.class);

    public static SSLContext get(String pkcs12FileName) {

        SSLContext sslContext = null;

        try {
            sslContext = SSLContext.getInstance("TLS");

            //TODO support use a external certification file
            KeyStore ks = KeyStore.getInstance("PKCS12");
            InputStream resourceAsStream = ClassLoader.getSystemResourceAsStream(pkcs12FileName);

            if (resourceAsStream == null) {
                throw new RuntimeException(pkcs12FileName + " not found!");
            }

            ks.load(resourceAsStream, "123456".toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, "123456".toCharArray());

            TrustManager[] trustManagers = null;
            trustManagers = new TrustManager[]{new X509TrustManager() {
                // TrustManager that trusts all servers
                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                        throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                        throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            }};
            sslContext.init(kmf.getKeyManagers(), trustManagers, null);
        } catch (Exception e) {
            LOGGER.error("System Exit, due to unable to create SSLContext", e);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
            }

            System.exit(1);
        }

        return sslContext;
    }
}