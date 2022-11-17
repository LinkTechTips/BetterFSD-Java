/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips;

import org.LinkTechTips.constants.FsdPath;
import org.LinkTechTips.constants.GlobalConstants;
import org.LinkTechTips.support.Support;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class MainLauncher {
    private static String[] args;
    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("server.name", "BetterFSD");
        final Logger LOGGER = LoggerFactory.getLogger(MainLauncher.class);
        MainLauncher.args = args;
        String property = System.getProperty("user.dir");
        Path path = Paths.get(FsdPath.PATH_FSD_CONF);
        File file = new File(FsdPath.PATH_FSD_CONF);
        LOGGER.info(String.format("BetterFSD Java Edition Version %s", GlobalConstants.VERSION));
        LOGGER.info(String.format("Operating System: %s", System.getProperty("os.name")));
        LOGGER.info(String.format("Java Version: %s, %s", System.getProperty("java.version"),
                System.getProperty("java.vendor")));
        LOGGER.info(String.format("Java VM Version: %s, %s", System.getProperty("java.vm.specification.version"),
                System.getProperty("java.vm.specification.vendor")));
        List<String> JvmFlag = ManagementFactory.getRuntimeMXBean().getInputArguments();
        LOGGER.info(String.format("JVM Flags: %s", JvmFlag));
        LOGGER.info(String.format("Cores: %s", Runtime.getRuntime().availableProcessors()));
        LOGGER.info(String.format("[BetterFSD]: Using config file: %s", file.getAbsolutePath()));
        String configFile = FsdPath.PATH_FSD_CONF;
        doSignals();
        run(configFile);
    }
    private static void run(String configFile) {
        Main main = new Main(configFile);
            while (true) {
                main.run();
            }
        }
    private static void doSignals() {
        Support.startTimer();
    }
}
