/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.process.network;

import org.LinkTechTips.constants.ClientConstants;
import org.LinkTechTips.constants.GlobalConstants;
import org.LinkTechTips.constants.NetworkConstants;
import org.LinkTechTips.constants.ProtocolConstants;
import org.LinkTechTips.model.Client;
import org.LinkTechTips.model.Flightplan;
import org.LinkTechTips.model.Server;
import org.LinkTechTips.user.AbstractUser;
import org.LinkTechTips.user.ClientUser;
import org.LinkTechTips.support.Support;
import org.LinkTechTips.weather.CloudLayer;
import org.LinkTechTips.weather.TempLayer;
import org.LinkTechTips.weather.WProfile;
import org.LinkTechTips.weather.WindLayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;
import java.util.Random;

public class ClientInterface extends TcpInterface {
    private final static Logger LOGGER = LoggerFactory.getLogger(ClientInterface.class);

    private long prevWindDelta;

    public ClientInterface(int port, String code, String d) {
        super(port, code, d);
        prevWindDelta = Support.mtime();
    }

    public boolean run() {
        boolean busy = super.run();
        if ((Support.mtime() - prevWindDelta) > GlobalConstants.WIND_DELTA_TIMEOUT) {
            prevWindDelta = Support.mtime();
            sendWindDelta();
        }

        return busy;
    }

    public void newUser(SocketChannel fd, String peer, int portnum, int g) {
        insertUser(new ClientUser(fd, this, peer, portnum, g));
    }

    public void sendAa(@NotNull Client who, AbstractUser ex) {

        String data = String.format("%s:SERVER:%s:%s::%d", who.getCallsign(), who.getRealName(),
                who.getCid(), who.getRating());//, who.getProtocol());
        sendPacket(null, null, ex, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_ADDATC, data);
    }

    public void sendAp(@NotNull Client who, AbstractUser ex) {
        String data = String.format("%s:SERVER:%s::%d:%s:%d", who.getCallsign(), who.getCid(), who.getRating(),
                who.getProtocol(), who.getSimType());
        sendPacket(null, null, ex, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_ADDPILOT, data);
    }

    public void sendDa(@NotNull Client who, AbstractUser ex) {

        String data = String.format("%s:%s", who.getCallsign(), who.getCid());
        sendPacket(null, null, ex, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_RMATC, data);
    }

    public void sendDp(@NotNull Client who, AbstractUser ex) {

        String data = String.format("%s:%s", who.getCallsign(), who.getCid());
        sendPacket(null, null, ex, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_RMPILOT, data);
    }

    public void sendWeather(@NotNull Client who, @NotNull WProfile p) {
        int x;
        StringBuilder buf = new StringBuilder();
        String part;
        p.fix(who.getLat(), who.getLon());
        buf = new StringBuilder(String.format("%s:%s", "server", who.getCallsign()));
        for (x = 0; x < 4; x++) {
            TempLayer l = p.getTemps().get(x);
            part = String.format(":%d:%d", l.getCeiling(), l.getTemp());
            buf.append(part);
        }
        part = String.format(":%d", p.getBarometer());
        buf.append(part);
        sendPacket(who, null, null, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_TEMPDATA, buf.toString());

        buf = new StringBuilder(String.format("%s:%s", "server", who.getCallsign()));
        for (x = 0; x < 4; x++) {
            WindLayer l = p.getWinds().get(x);
            part = String.format(":%d:%d:%d:%d:%d:%d", l.getCeiling(), l.getFloor(), l.getDirection(),
                    l.getSpeed(), l.getGusting(), l.getTurbulence());
            buf.append(part);
        }
        sendPacket(who, null, null, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_WINDDATA, buf.toString());

        buf = new StringBuilder(String.format("%s:%s", "server", who.getCallsign()));
        for (x = 0; x < 3; x++) {
            CloudLayer c = (x == 2 ? p.getTstorm() : p.getClouds().get(x));
            part = String.format(":%d:%d:%d:%d:%d", c.getCeiling(), c.getFloor(), c.getCoverage(),
                    c.getIcing(), c.getTurbulence());
            buf.append(part);
        }
        part = String.format(":%.2f", p.getVisibility());
        buf.append(part);
        sendPacket(who, null, null, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_CLOUDDATA, buf.toString());
    }

    public void sendMetar(@NotNull Client who, String data) {
        String buf = String.format("server:%s:METAR:%s", who.getCallsign(), data);
        sendPacket(who, null, null, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_REPACARS, buf);
    }

    public void sendNoWx(Client who, String station) {
        for (AbstractUser temp : users) {
            ClientUser ctemp = (ClientUser) temp;
            if (ctemp.getThisClient() == who) {
                ctemp.showError(ProtocolConstants.ERR_NOWEATHER, station);
                break;
            }
        }
    }

    public int getBroad(String s) {
        int broad = ClientConstants.CLIENT_ALL;
        if ("*P".equals(s)) broad = ClientConstants.CLIENT_PILOT;
        else if ("*A".equals(s)) broad = ClientConstants.CLIENT_ATC;
        return broad;
    }

    public void sendGeneric(@NotNull String to, Client dest, AbstractUser ex,
                            @Nullable Client source, String from, String s, int cmd) {
        int range = -1;
        String buf = String.format("%s:%s:%s", from, to, s);
        if (to.charAt(0) == '@' && source != null)
            range = source.getRange();
        sendPacket(dest, source, ex, getBroad(to), range, cmd, buf);
    }

    public void sendPilotPos(@NotNull Client who, AbstractUser ex) {
        String data = String.format("%s:%s:%d:%d:%.5f:%.5f:%d:%d:%d:%d", who.getIdentFlag(),
                who.getCallsign(), who.getTransponder(), who.getRating(), who.getLat(), who.getLon(),
                who.getAltitude(), who.getGroundSpeed(), who.getPbh(), who.getFlags());
//dolog(L_INFO,"PBH unsigned value is %u",who.getpbh);
//dolog(L_INFO,"SendPilotPos is sending: %s",data);
        sendPacket(null, who, ex, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_PILOTPOS, data);
    }

    public void sendAtcPos(@NotNull Client who, AbstractUser ex) {
        String data = String.format("%s:%d:%d:%d:%d:%.5f:%.5f:%d", who.getCallsign(),
                who.getFrequency(), who.getFacilityType(), who.getVisualRange(), who.getRating(),
                who.getLat(), who.getLon(), who.getAltitude());
        sendPacket(null, who, ex, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_ATCPOS, data);
    }

    public void sendPlan(@Nullable Client dest, @NotNull Client who, int range) {
        String cs = (dest != null ? dest.getCallsign() : "*A");
        Flightplan plan = who.getPlan();
        String buf = String.format("%s:%s:%c:%s:%d:%s:%d:%d:%s:%s:%d:%d:%d:%d:%s:%s:%s",
                who.getCallsign(), cs, plan.getType(), plan.getAircraft(),
                plan.getTasCruise(), plan.getDepAirport(), plan.getDepTime(), plan.getActDepTime(),
                plan.getAlt(), plan.getDestAirport(), plan.getHrsEnroute(), plan.getMinEnroute(),
                plan.getHrsFuel(), plan.getMinFuel(), plan.getAltAirport(), plan.getRemarks(),
                plan.getRoute());
        sendPacket(dest, null, null, ClientConstants.CLIENT_ATC, range, ProtocolConstants.CL_PLAN, buf);
    }

    public void handleKill(@NotNull Client who, String reason) {
        if (who.getLocation() != Server.myServer) return;
        for (AbstractUser temp : users) {
            ClientUser ctemp = (ClientUser) temp;
            if (ctemp.getThisClient() == who) {
                String buf = String.format("SERVER:%s:%s", who.getCallsign(), reason);
                sendPacket(who, null, null, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_KILL, buf);
                temp.kill(NetworkConstants.KILL_KILL);
            }
        }
    }

    public void sendWindDelta() {
        Random random = new Random(Support.mtime());
        String buf;
        int speed = random.nextInt() % 11 - 5;
        int direction = random.nextInt() % 21 - 10;
        buf = String.format("SERVER:*:%d:%d", speed, direction);
        sendPacket(null, null, null, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_WDELTA, buf);
    }

    public int calcRange(@NotNull Client from, @NotNull Client to, int type, int range) {
        int x, y;
        switch (type) {
            case ProtocolConstants.CL_PILOTPOS:
            case ProtocolConstants.CL_ATCPOS:
                if (to.getType() == ClientConstants.CLIENT_ATC) return to.getVisualRange();
                x = to.getRange();
                y = from.getRange();
                if (from.getType() == ClientConstants.CLIENT_PILOT) return x + y;
                return Math.max(x, y);
            case ProtocolConstants.CL_MESSAGE:
                x = to.getRange();
                y = from.getRange();
                if (from.getType() == ClientConstants.CLIENT_PILOT && to.getType() == ClientConstants.CLIENT_PILOT)
                    return x + y;
                return Math.max(x, y);
            default:
                return range;
        }
    }

    /* Send a packet to a client.
       If <dest> is specified, only the client <dest> will receive the message.
       <broad> indicates if only pilot, only atc, or both will receive the
       message
    */
    public void sendPacket(@Nullable Client dest, @Nullable Client source, AbstractUser exclude,
                           int broad, int range, int cmd, String data) {
        if (dest != null) {
            if (dest.getLocation() != Server.myServer) return;
        }
        for (AbstractUser temp : users)
            if (temp.getKillFlag() == 0) {
                Client cl = ((ClientUser) temp).getThisClient();
                if (cl == null) continue;
                if (exclude == temp) continue;
                if (dest != null && cl != dest) continue;
                if ((cl.getType() & broad) == 0) continue;
                if (source != null && (range != -1 || cmd == ProtocolConstants.CL_PILOTPOS || cmd == ProtocolConstants.CL_ATCPOS)) {
                    int checkRange = calcRange(source, cl, cmd, range);
                    double distance = cl.distance(source);
                    if (distance == -1 || distance > checkRange) continue;
                }
                temp.uslprintf("%s%s\r\n", cmd == ProtocolConstants.CL_ATCPOS || cmd == ProtocolConstants.CL_PILOTPOS ? 1 : 0,
                        ClientConstants.CL_CMD_NAMES[cmd], data);
            }
    }

}
