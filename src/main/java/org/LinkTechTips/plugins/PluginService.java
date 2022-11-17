/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.plugins;

public interface PluginService {
    // 插件功能主入口方法
    void PluginService();
    // 插件名称
    // @return 插件名称
    String PluginName();
    // 插件版本
    // @return 插件版本
    String PlugunVersion();
}
