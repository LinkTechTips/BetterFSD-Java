/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.plugins;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class PluginLoader {
    public static final String PLUGIN_PATH = "plugins";

    public static List<IPluginService> loadPlugins() throws MalformedURLException {
        List<IPluginService> plugins = new ArrayList<>();

        File parentDir = new File(PLUGIN_PATH);
        File[] files = parentDir.listFiles();
        if (null == files) {
            return Collections.emptyList();
        }

        // 从目录下筛选出所有jar文件
        List<File> jarFiles = Arrays.stream(files)
                .filter(file -> file.getName().endsWith(".jar")).toList();

        URL[] urls = new URL[jarFiles.size()];
        for (int i = 0; i < jarFiles.size(); i++) {
            // 加上 "file:" 前缀表示本地文件
            urls[i] = new URL("file:" + jarFiles.get(i).getAbsolutePath());
        }

        URLClassLoader urlClassLoader = new URLClassLoader(urls);
        // 使用 ServiceLoader 以SPI的方式加载插件包中的 PluginService 实现类
        ServiceLoader<IPluginService> serviceLoader = ServiceLoader.load(IPluginService.class, urlClassLoader);
        for (IPluginService IPluginService : serviceLoader) {
            plugins.add(IPluginService);
        }
        return plugins;
    }
}
