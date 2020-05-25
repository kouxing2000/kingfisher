package com.kingfisher.proxy;

import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLContextProvider {

    private static final Logger logger = LoggerFactory.getLogger(SSLContextProvider.class);

    public static SSLContext get(String targetDomain) {

        SSLContext sslContext = null;

        try {
            sslContext = SSLContext.getInstance("TLS");

            KeyStore ks = KeyStore.getInstance("PKCS12");
            InputStream resourceAsStream = loadCert(targetDomain);

            if (resourceAsStream == null) {
                throw new RuntimeException("cert for " + targetDomain +
                        " not found!");
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
            logger.error("System Exit, due to unable to create SSLContext", e);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
            }

            System.exit(1);
        }

        return sslContext;
    }

    public static InputStream loadCert(String targetDomain) {
        String certFile = "server_cert" + File.separator
                + targetDomain + "_cert.p12";
        File file = new File(certFile);
        if (file.exists()) {
            try {
                logger.info("found from {}", file.getAbsolutePath());
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
        return ClassLoader.getSystemResourceAsStream(certFile);
    }

    private static ExecutorService executorService = Executors.newCachedThreadPool();

    public static void checkAndGenerateCertification(String targetDomain) {

        if (SSLContextProvider.loadCert(targetDomain) != null) {
            //if already generated
            return;
        }

        try {
            File folder = new File("proxy_cert");
            boolean exists = folder.exists();

            if (!exists) {
                logger.error("no proxy_cert folder found!");
                System.exit(0);
            }

            final Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", "sh sign_server.sh " + targetDomain}, new String[]{}, folder);

            Future<String> future = executorService.submit(new Callable<String>() {
                @Override
                public String call() {
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line = reader.readLine();
                        while (line != null) {
                            System.out.println("process info :" + line);

                            line = reader.readLine();
                        }
                        return null;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    } finally {
                        IOUtils.closeQuietly(reader);
                        System.out.println(Thread.currentThread() + " over with " + process);
                    }

                }


            });
            Future<String> errFuture = executorService.submit(new Callable<String>() {
                @Override
                public String call() {
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String line = reader.readLine();
                        while (line != null) {
                            System.out.println("process error:" + line);

                            line = reader.readLine();
                        }
                        return null;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    } finally {
                        IOUtils.closeQuietly(reader);
                        System.out.println(Thread.currentThread() + " over with " + process);
                    }

                }


            });

            future.get();
            errFuture.get();
            
            if (process.waitFor() == 1) {
                System.exit(1);
            }

            if (SSLContextProvider.loadCert(targetDomain) != null) {
                logger.info("generate cert successfully for {}", targetDomain);
            }

        } catch (Exception e) {
            logger.error("failed to generate certification file for domain:" + targetDomain, e);
            logger.error("you need run " + "'sh sign_server.sh " + targetDomain + "' in proxy_cert folder manually!");
            System.exit(1);
        }
    }
}