/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.httpapi;

import org.linktechtips.process.config.ConfigEntry;
import org.linktechtips.process.config.ConfigGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static org.linktechtips.Main.configManager;

public class httpApiManage {
    private final static Logger LOGGER = LoggerFactory.getLogger(httpApiManage.class);
    public static String whazzupFile;
    public static String whazzupJsonFile;
    public static String webServerPort;
    public static int webServerPortInt;
    public httpApiManage() {
        readConfig();
        HttpServer httpServer;
        if (webServerPort == null){
            LOGGER.error("[HTTP]: WebServerPort is null");
            LOGGER.error("[HTTP]: exit");
        } else {
            LOGGER.info(String.format("[HTTP]: HTTP Server Will Run On Port %s", webServerPort));
            try {
                httpServer = HttpServer.create(new InetSocketAddress(webServerPortInt), 0);httpServer.createContext("/api/whazzup/txt", new ReadWhazzupController());
                httpServer.createContext("/api/whazzup/json", new ReadWhazzupJsonController());
                httpServer.setExecutor(Executors.newFixedThreadPool(10));
                httpServer.start();
            } catch (IOException e) {
                LOGGER.info("[HTTP]: I/O Exception");
                e.printStackTrace();
            }
        }
    }
    public void readConfig() {
        ConfigEntry entry;
        ConfigGroup system = configManager.getGroup("system");
        if (system != null) {
            if ((entry = system.getEntry("whazzup")) != null) {
                whazzupFile = entry.getData();
            }
            if ((entry = system.getEntry("whazzupjson")) != null) {
                whazzupJsonFile= entry.getData();
            }
            if ((entry = system.getEntry("webserverport")) != null) {
                webServerPort = entry.getData();
            }
            if ((entry = system.getEntry("webserverport")) != null) {
                webServerPortInt = entry.getInt();
            }
        }
    }
}