/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.weather;

public class WindLayer {
    private int ceiling;
    private int floor;
    private int direction;
    private int speed;
    private int gusting;
    private int turbulence;

    public WindLayer() {
    }

    public WindLayer(int ceiling, int floor) {
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

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getGusting() {
        return gusting;
    }

    public void setGusting(int gusting) {
        this.gusting = gusting;
    }

    public int getTurbulence() {
        return turbulence;
    }

    public void setTurbulence(int turbulence) {
        this.turbulence = turbulence;
    }
}
