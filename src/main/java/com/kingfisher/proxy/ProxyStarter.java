package com.kingfisher.proxy;

import com.kingfisher.proxy.config.AllConfig;
import com.thoughtworks.xstream.XStream;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by weili5 on 2016/1/25.
 */
public class ProxyStarter {

    private static final Logger logger = LoggerFactory.getLogger(ProxyStarter.class);

    static final ScheduledExecutorService jobScheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    static long lastConfigFileModifiedTS = 0;

    private static final String DEFAULT_CONFIG = "sample.config.xml";

    public static void main(String[] args) throws Exception {

        // read from arguments
        final KingfisherHttpProxy proxy = new KingfisherHttpProxy();

        AllConfig config = null;

        File configFile = null;

        if (args != null && args.length >= 1) {
            String inputFile = args[0];
            configFile = new File(inputFile);

            if (!configFile.exists()) {
                URL resource = ClassLoader.getSystemResource(inputFile);
                if (resource != null) {
                    configFile = new File(resource.toURI());
                } else {
                    logger.error("Failed to load config file: {} , use default config", inputFile);
                    configFile = null;
                }
            }

        } else {
            logger.error("Usage: java -jar porxy.jar <config file name>, use default config for this time");

        }

        if (configFile == null) {
            configFile = new File(ClassLoader.getSystemResource(DEFAULT_CONFIG).toURI());
            logger.info("default config:" + configFile.toString());
        }

        final File finalConfigFile = configFile;

        jobScheduledExecutor.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    //System.out.println("reload check : " + finalConfigFile.lastModified());
                    if (finalConfigFile.lastModified() > lastConfigFileModifiedTS) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Detect config file {} changed, reloading...",
                                    finalConfigFile.getAbsolutePath());
                        }
                        AllConfig config = readConfig(FileUtils.readFileToString(finalConfigFile, "UTF-8"));
                        proxy.loadConfig(config);
                        lastConfigFileModifiedTS = finalConfigFile.lastModified();
                        if (logger.isInfoEnabled()) {
                            logger.info("Reloading complete");
                        }
                    }
                } catch (Exception e) {
                    logger.error("reload config", e);
                }
                //check every 5 seconds
            }
        }, 5, 5, TimeUnit.SECONDS);

        try {
            config = readConfig(FileUtils.readFileToString(configFile, "UTF-8"));
            lastConfigFileModifiedTS = configFile.lastModified();
        } catch (Exception e) {
            logger.error("Failed to load config, abort!", e);
            return;
        }

        proxy.startProxy(config);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                logger.error("detect JVM exit, stop proxy!");
                proxy.stop();
            }
        });
    }

    private static AllConfig readConfig(String configuration) throws IOException {
        return (AllConfig) new XStream().fromXML(configuration);
    }
}
