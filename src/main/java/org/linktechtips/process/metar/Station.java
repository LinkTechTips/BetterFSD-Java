/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.process.metar;

public class Station {

    private String name;

    private long location;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLocation() {
        return location;
    }

    public void setLocation(long location) {
        this.location = location;
    }
}
