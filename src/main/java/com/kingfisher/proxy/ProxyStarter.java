package com.kingfisher.proxy;

import com.google.gson.Gson;
import com.kingfisher.proxy.config.Config;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static final String DEFAULT_CONFIG_JSON = "kingfisherproxy.config.json";

    public static void main(String[] args) {

        // read from arguments
        final KingfisherHttpProxy proxy = new KingfisherHttpProxy();

        Config config = null;

        if (args != null && args.length >= 1) {
            final File configFile = new File(args[0]);

            if (configFile.exists()) {
                jobScheduledExecutor.scheduleAtFixedRate(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            if (configFile.lastModified() > lastConfigFileModifiedTS) {
                                if (logger.isInfoEnabled()) {
                                    logger.info("Detect config file {} changed, reloading...",
                                            configFile.getAbsolutePath());
                                }
                                Config config = new Gson().fromJson(FileUtils.readFileToString(configFile),
                                        Config.class);
                                proxy.loadConfig(config);
                                lastConfigFileModifiedTS = configFile.lastModified();
                                if (logger.isInfoEnabled()) {
                                    logger.info("Reloading complete");
                                }
                            }
                        } catch (Exception e) {
                            logger.error("reload config", e);
                        }
                    }
                }, 5, 5, TimeUnit.SECONDS);

                try {
                    config = new Gson().fromJson(FileUtils.readFileToString(configFile), Config.class);
                    lastConfigFileModifiedTS = configFile.lastModified();
                } catch (Exception e) {
                    logger.error("Failed to load config, abort!", e);
                    return;
                }
            } else {
                logger.error("Failed to load config file: {} , use default config", args[0]);
            }

        } else {
            logger.error("Usage: java -jar porxy.jar <config file name>, use default config for this time");
        }

        if (config == null) {
            try {
                InputStream resourceAsStream = ClassLoader.getSystemResourceAsStream(DEFAULT_CONFIG_JSON);
                config = new Gson().fromJson(new InputStreamReader(resourceAsStream, Constants.utf8), Config.class);
                resourceAsStream.close();
            } catch (Exception e) {
                logger.error("while read default config", e);
                return;
            }
        }

        proxy.startProxy(config);
    }
}
