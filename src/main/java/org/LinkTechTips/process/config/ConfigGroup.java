/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.process.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigGroup {

    private String name;

    private final @NotNull List<ConfigEntry> entries;

    private boolean changed;

    public ConfigGroup(String name) {
        this.name = name;
        entries = new ArrayList<>();
        int nEntries = 0;
        changed = true;
    }

    public @Nullable ConfigEntry getEntry(String name) {
        for (ConfigEntry entry : entries) {
            if (Objects.equals(entry.getVar(), name)) {
                return entry;
            }
        }

        return null;
    }

    public void createEntry(String var, String data) {
        ConfigEntry configEntry = new ConfigEntry(var, data);
        entries.add(configEntry);
    }

    public void handleEntry(String var, String data) {
        ConfigEntry entry = getEntry(var);
        if (entry == null) {
            createEntry(var, data);
            changed = true;
            return;
        }

        if (Objects.equals(entry.getData(), data)) {
            return;
        }

        entry.setData(data);
        changed = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }
}
