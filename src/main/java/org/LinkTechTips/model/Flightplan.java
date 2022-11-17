/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.model;

public class Flightplan {
    private String callsign;
    private int revision;
    private char type;
    private String aircraft;
    private int tasCruise;
    private String depAirport;
    private int depTime;
    private int actDepTime;
    private String alt;
    private String destAirport;
    private int hrsEnroute, minEnroute;
    private int hrsFuel, minFuel;
    private String altAirport;
    private String remarks;
    private String route;

    public Flightplan(String cs, char type, String aircraft, int tasCruise,
                      String depAirport, int depTime, int actDepTime, String alt,
                      String destAirport, int hrsEnroute, int minEnroute, int hrsFuel,
                      int minFuel, String altAirport, String remarks, String route) {
        this.callsign = cs;
        this.type = type;
        this.aircraft = aircraft;
        this.tasCruise = tasCruise;
        this.depAirport = depAirport;
        this.depTime = depTime;
        this.actDepTime = actDepTime;
        this.alt = alt;
        this.destAirport = destAirport;
        this.hrsEnroute = hrsEnroute;
        this.minEnroute = minEnroute;
        this.hrsFuel = hrsFuel;
        this.minFuel = minFuel;
        this.altAirport = altAirport;
        this.remarks = remarks;
        this.route = route;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public char getType() {
        return type;
    }

    public void setType(char type) {
        this.type = type;
    }

    public String getAircraft() {
        return aircraft;
    }

    public void setAircraft(String aircraft) {
        this.aircraft = aircraft;
    }

    public int getTasCruise() {
        return tasCruise;
    }

    public void setTasCruise(int tasCruise) {
        this.tasCruise = tasCruise;
    }

    public String getDepAirport() {
        return depAirport;
    }

    public void setDepAirport(String depAirport) {
        this.depAirport = depAirport;
    }

    public int getDepTime() {
        return depTime;
    }

    public void setDepTime(int depTime) {
        this.depTime = depTime;
    }

    public int getActDepTime() {
        return actDepTime;
    }

    public void setActDepTime(int actDepTime) {
        this.actDepTime = actDepTime;
    }

    public String getAlt() {
        return alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public String getDestAirport() {
        return destAirport;
    }

    public void setDestAirport(String destAirport) {
        this.destAirport = destAirport;
    }

    public int getHrsEnroute() {
        return hrsEnroute;
    }

    public void setHrsEnroute(int hrsEnroute) {
        this.hrsEnroute = hrsEnroute;
    }

    public int getMinEnroute() {
        return minEnroute;
    }

    public void setMinEnroute(int minEnroute) {
        this.minEnroute = minEnroute;
    }

    public int getHrsFuel() {
        return hrsFuel;
    }

    public void setHrsFuel(int hrsFuel) {
        this.hrsFuel = hrsFuel;
    }

    public int getMinFuel() {
        return minFuel;
    }

    public void setMinFuel(int minFuel) {
        this.minFuel = minFuel;
    }

    public String getAltAirport() {
        return altAirport;
    }

    public void setAltAirport(String altAirport) {
        this.altAirport = altAirport;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }
}
