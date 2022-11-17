/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.weather;

public class CloudLayer {
    private int ceiling;
    private int floor;
    private int coverage;
    private int icing;
    private int turbulence;

    public CloudLayer() {
    }

    public CloudLayer(int ceiling, int floor) {
        this.ceiling = ceiling;
        this.floor = floor;
    }

    public int getCeiling() {
        return ceiling;
    }

    public void setCeiling(int ceiling) {
        this.ceiling = ceiling;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getCoverage() {
        return coverage;
    }

    public void setCoverage(int coverage) {
        this.coverage = coverage;
    }

    public int getIcing() {
        return icing;
    }

    public void setIcing(int icing) {
        this.icing = icing;
    }

    public int getTurbulence() {
        return turbulence;
    }

    public void setTurbulence(int turbulence) {
        this.turbulence = turbulence;
    }
}
