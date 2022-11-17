/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.model;

import org.linktechtips.Main;
import org.linktechtips.constants.ClientConstants;
import org.linktechtips.support.Support;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Client {

    public static final List<Client> clients = new ArrayList<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

    private long startTime;

    private Flightplan plan;

    private int type;

    private int rating;

    private long pbh;

    private int flags;

    private long alive;

    private String cid, callsign, protocol, realName, sector, identFlag;

    private double lat, lon;

    private int transponder, altitude, groundSpeed, frequency, facilityType;

    private int positionOk, visualRange, simType;

    private Server location;

    public Client(String i, Server where, String cs, int t, int reqRating, String rev, String real, int st) {
        clients.add(this);
        cid = i;
        location = where;
        callsign = cs;
        type = t;
        protocol = rev;
        rating = reqRating;
        visualRange = 40;
        realName = real;
        simType = st;
        startTime = alive = Support.mtime();
        LOGGER.info(String.format("[User/Client]: %s as %s logged in", i, cs));
    }

    public void close() {
        Main.serverInterface.clientDropped(this);
        clients.remove(this);
    }

    public void handleFp(String @NotNull [] array) {
        int revision = plan != null ? plan.getRevision() + 1 : 0;
        plan = new Flightplan(callsign, array[0].charAt(0), array[1],
                NumberUtils.toInt(array[2]), array[3], NumberUtils.toInt(array[4]),
                NumberUtils.toInt(array[5]), array[6], array[7], NumberUtils.toInt(array[8]),
                NumberUtils.toInt(array[9]), NumberUtils.toInt(array[10]), NumberUtils.toInt(array[11]), array[12],
                array[13], array[14]);
        plan.setRevision(revision);
    }

    /* Update the client fields, given a packet array */
    public void updatePilot(String @NotNull [] array) {
        transponder = NumberUtils.toInt(array[2]);
        identFlag = array[0];
        lat = NumberUtils.toDouble(array[4]);
        lon = NumberUtils.toDouble(array[5]);
        if (lat > 90.0 || lat < -90.0 || lon > 180.0 || lon < -180.0) {
            LOGGER.debug(String.format("POSERR: s=(%s,%s) got(%f,%f)", array[4], array[5], lat, lon));
        }

        altitude = NumberUtils.toInt(array[6]);
        groundSpeed = NumberUtils.toInt(array[7]);
        pbh = NumberUtils.toLong(array[8]);

        flags = NumberUtils.toInt(array[9]);
        setAlive();
        positionOk = 1;
    }

    public void updateAtc(String @NotNull [] array) {
        frequency = NumberUtils.toInt(array[0]);
        facilityType = NumberUtils.toInt(array[1]);
        visualRange = NumberUtils.toInt(array[2]);
        lat = NumberUtils.toDouble(array[4]);
        lon = NumberUtils.toDouble(array[5]);
        altitude = NumberUtils.toInt(array[6]);
        setAlive();
        positionOk = 1;
    }

    public void setAlive() {
        alive = Support.mtime();
    }

    public double distance(@Nullable Client other) {
        if (other == null) {
            return -1;
        }
        if (positionOk == 0 || other.getPositionOk() == 0) {
            return -1;
        }
        return Support.dist(lat, lon, other.lat, other.lon);
    }

    public int getRange() {
        if (type == ClientConstants.CLIENT_PILOT) {
            if (altitude < 0) {
                altitude = 0;
            }
            return (int) (10 + 1.414 * Math.sqrt(altitude));
        }

        return switch (facilityType) {
            case 0 -> 40;       /* Unknown */
            case 1 -> 1500;      /* FSS */
            case 2 -> 5;        /* CLR_DEL */
            case 3 -> 5;        /* GROUND */
            case 4 -> 30;       /* TOWER  */
            case 5 -> 100;      /* APP/DEP */
            case 6 -> 400;      /* CENTER */
            case 7 -> 1500;     /* MONITOR */
            default -> 40;
        };
    }

    public static @Nullable Client getClient(String ident) {
        for (Client temp : clients) {
            if (temp.getCallsign().equals(ident)) {
                return temp;
            }
        }
        return null;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public Flightplan getPlan() {
        return plan;
    }

    public void setPlan(Flightplan plan) {
        this.plan = plan;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public long getPbh() {
        return pbh;
    }
    public long getHeading() {
        return ((pbh & 4092) >> 2 ) / 1024 * 360;
    }
    public void setPbh(long pbh) {
        this.pbh = pbh;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public long getAlive() {
        return alive;
    }

    public void setAlive(long alive) {
        this.alive = alive;
    }

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getIdentFlag() {
        return identFlag;
    }

    public void setIdentFlag(String identFlag) {
        this.identFlag = identFlag;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public int getTransponder() {
        return transponder;
    }

    public void setTransponder(int transponder) {
        this.transponder = transponder;
    }

    public int getAltitude() {
        return altitude;
    }

    public void setAltitude(int altitude) {
        this.altitude = altitude;
    }

    public int getGroundSpeed() {
        return groundSpeed;
    }

    public void setGroundSpeed(int groundSpeed) {
        this.groundSpeed = groundSpeed;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getFacilityType() {
        return facilityType;
    }

    public void setFacilityType(int facilityType) {
        this.facilityType = facilityType;
    }

    public int getPositionOk() {
        return positionOk;
    }

    public void setPositionOk(int positionOk) {
        this.positionOk = positionOk;
    }

    public int getVisualRange() {
        return visualRange;
    }

    public void setVisualRange(int visualRange) {
        this.visualRange = visualRange;
    }

    public int getSimType() {
        return simType;
    }

    public void setSimType(int simType) {
        this.simType = simType;
    }

    public Server getLocation() {
        return location;
    }

    public void setLocation(Server location) {
        this.location = location;
    }
}
