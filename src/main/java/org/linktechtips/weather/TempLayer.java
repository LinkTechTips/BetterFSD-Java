/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.weather;

public class TempLayer {
    private int ceiling;
    private int temp;

    public TempLayer() {
    }

    public TempLayer(int ceiling) {
        this.ceiling = ceiling;
    }

    public int getCeiling() {
        return ceiling;
    }

    public void setCeiling(int ceiling) {
        this.ceiling = ceiling;
    }

    public int getTemp() {
        return temp;
    }

    public void setTemp(int temp) {
        this.temp = temp;
    }
}
