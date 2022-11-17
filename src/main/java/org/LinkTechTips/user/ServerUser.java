/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.user;

import org.LinkTechTips.Main;
import org.LinkTechTips.manager.Manage;
import org.LinkTechTips.model.Certificate;
import org.LinkTechTips.model.Client;
import org.LinkTechTips.model.Server;
import org.LinkTechTips.process.config.ConfigEntry;
import org.LinkTechTips.process.config.ConfigGroup;
import org.LinkTechTips.process.metar.MetarManage;
import org.LinkTechTips.process.network.ServerInterface;
import org.LinkTechTips.support.Support;
import org.LinkTechTips.weather.WProfile;
import org.LinkTechTips.weather.Weather;
import org.LinkTechTips.constants.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;
import java.util.*;

public class ServerUser extends AbstractUser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerUser.class);
    public static final int MAX_SPACE = 1000000;

    private final ServerInterface parent;

    private @Nullable Server thisServer;

    private long startupTime;

    private int clientOk;

    public ServerUser(SocketChannel d, ServerInterface p, String pn, int portNum, int g) {
        super(d, p, pn, portNum, g);

        parent = p;
        startupTime = Support.mtime();
        clientOk = 0;
        thisServer = null;
        feed();
    }

    public void close() {
        /* This server link is killed, send out a LINKDOWN message to the
      others */
        StringBuilder buf = new StringBuilder();
        for (Server temp : Server.servers) {
            if (temp.getPath() == this) {
                temp.setPath(null, -1);
                if (buf.length() > 0) {
                    buf.append(":");
                }
                buf.append(temp.getIdent());
            }
        }
        if (buf.length() > 0) {
            parent.sendLinkDown(buf.toString());
            LOGGER.info("[User/Serveruser]: link down");
        }
        Main.serverInterface.sendSync();
    }

    public void parse(@NotNull String s) {
        if (s.charAt(0) == '#') return;
        setActive();
        if (s.charAt(0) != '\\') {
            doParse(s);
        } else {
            switch (s.charAt(1)) {
                case 'q' -> kill(NetworkConstants.KILL_COMMAND);
                case 'l' -> list(s);
                default -> uprintf("Syntax error, type \\h for help\n");
            }
        }
    }

    private void list(@NotNull String s) {
        String str = s.substring(2).stripLeading();
        if (StringUtils.isEmpty(str)) {
            str = null;
        }
        int nVars = Manage.manager.getNVars();
        for (int x = 0; x < nVars; x++) {
            String buf = Manage.manager.sprintValue(x);
            if (StringUtils.isNotBlank(buf)) {
                String varName = Objects.requireNonNull(Manage.manager.getVar(x)).getName();
                if (str == null || varName.contains(s)) {
                    uprintf("%s=%s\r\n", varName, buf);
                }
            }
        }
    }

    /* Feed all data from this server to a direct neighbor */
    private void feed() {
        for (Server tempServer : Server.servers) {
            Main.serverInterface.sendServerNotify("*", tempServer, this);
        }
        for (Client tempClient : Client.clients) {
            Main.serverInterface.sendAddClient("*", tempClient, this, null, 1);
            if (tempClient.getPlan() != null) {
                Main.serverInterface.sendPlan("*", tempClient, this);
            }
        }
        for (Certificate tempCert : Certificate.certs) {
            Main.serverInterface.sendCert("*", ProtocolConstants.CERT_ADD, tempCert, this);
        }
        for (WProfile tempWp : Weather.wProfiles) {
            Main.serverInterface.sendAddWp(this, tempWp);
        }
    }

    /*
     * When given the destination of a packet, needlocaldelivery() checks if this
     * packet needs local delivery.
     */
    private int needLocalDelivery(@NotNull String dest) {
        switch (dest.charAt(0)) {
            /* wildcards are always delivered locally. Note : '*P' and '*A' are
                also broadcast addresses */
            case '*':
            /* We don't know yet, who is on what frequency, so deliver frequency
                addressed packets */
            case '@':
                return 1;
            case '%':
                /* This is a pilot packet, check if the client is connected to
                this server */
                for (Client tempClient : Client.clients) {
                    if (tempClient.getCallsign().equalsIgnoreCase(dest.substring(1)) && tempClient.getLocation() == Server.myServer) {
                        return 1;
                    }
                }
                break;
            default:
                /* We have a server name here, check if the name equals our server
                name */
                if (dest.equalsIgnoreCase(Server.myServer.getIdent())) {
                    return 1;
                }
                break;
        }
        return 0;
    }

    /*
     * Takes care of a packet, that comes in from a server connection.
     */
    private void doParse(@NotNull String s) {
        int index = 0, count, packetCount = 0, hops = 0, bi;
        List<String> array = new ArrayList<>();
        String from, to;
        Server origin;

        if (s.charAt(0) == '#') {
            return;
        }

        count = Support.breakArgs(s, array, 100);

        if (count < 6) {
            /* This packet doesn't have the basic fields, show an error and
            drop it  */
            Manage.manager.incVar(parent.getVarShape());
            uprintf("# Missing basic packet fields, syntax error\r\n");
            return;
        }

        /* Some statistics */
        boolean packetErr = false;
        Scanner scanner = new Scanner(array.get(3).substring(1));
        if (!scanner.hasNextInt()) {
            packetErr = true;
        } else {
            packetCount = scanner.nextInt();
        }

        scanner = new Scanner(array.get(4));
        if (!scanner.hasNextInt()) {
            packetErr = true;
        } else {
            hops = scanner.nextInt();
        }

        from = array.get(2);
        to = array.get(1);
        bi = (array.get(3).charAt(0) == 'B') ? 1 : 0;

        if (packetErr) {
            Manage.manager.incVar(parent.getVarIntErr());
            return;
        }

        if (hops > GlobalConstants.MAX_HOPS) {
            /* This packet seems to bounce, drop it */
            Manage.manager.incVar(parent.getVarBounce());
            return;
        }

        /* Find the origin of the packet */
        origin = Server.getServer(from);
        if (origin != null) {
            /* This server seems to be alive */
            origin.setAlive();

            /* Discard if it originated from our server */
            if (origin == Server.myServer) {
                return;
            }

            /* Check if this packet travelled a shorter route */
            if (hops < origin.getHops() || origin.getHops() == -1) {
                origin.setPath(this, hops);
            }

            /* Discard if the packet was broadcast and the packetcount doesn't
                 match the packetcount (we already handled this packet earlier)
                 The broadcast check (bi) is nessecary to prevent newer broadcast
                 packets from passing stalled unicast packets with a quicker route.
              */
            if (packetCount <= origin.getpCount() || packetCount > (origin.getpCount() + MAX_SPACE)) {
                if (packetCount == origin.getpCount() || (bi == 1 && origin.getPacketDrops() < 100) ||
                        (bi == 1 && packetCount > (origin.getpCount() + MAX_SPACE))) {
                    Manage.manager.incVar(parent.getVarMcDrops());
                    origin.setPacketDrops(origin.getPacketDrops() + 1);
                    return;
                }
                Manage.manager.incVar(parent.getVarUcOverRun());
            }
            if (origin.getPacketDrops() >= 100) {
                origin.setpCount(packetCount);
            }
            if (packetCount > origin.getpCount()) {
                /* Update the packetcount for this server */
                origin.setpCount(packetCount);
            }
            if (bi == 1) {
                /* Set the path to the server */
                origin.setPath(this, hops);
                origin.setPacketDrops(0);
            }
        }

        /* Now find the command number for this packet */
        while (index < ProtocolConstants.CMD_NAMES.length) {
            if (ProtocolConstants.CMD_NAMES[index].equalsIgnoreCase(array.get(0))) {
                /* This user is correct */
                clientOk = 1;

                /* If we don't know the server, we only allow the NOTIFY packet from
                it */
                if (origin == null && index != ProtocolConstants.CMD_NOTIFY) {
                    return;
                }

                /* If the packet doesn't need local delivery, forward and return */
                if (needLocalDelivery(to) == 0) {
                    /* call sendpacket() to relay the packet to the other servers */
                    if (!to.equalsIgnoreCase(Server.myServer.getIdent())) {
                        parent.sendPacket(this, null, index, to, from, packetCount,
                                hops, bi, Support.catCommand(array.subList(5, array.size() - 1), count - 5, new StringBuilder()));
                    }
                    return;
                }

                /* Execute the command */
                int needForward = runCmd(index, array.subList(1, array.size() - 1).toArray(new String[0]), count - 1);
                /* if the packet needs forwarding, call sendpacket() to relay the
                packet to the other servers */
                if (!to.equalsIgnoreCase(Server.myServer.getIdent()) && needForward == 1) {
                    parent.sendPacket(this, null, index, to, from, packetCount,
                            hops, bi, Support.catCommand(array.subList(5, array.size() - 1), count - 5, new StringBuilder()));
                }
                Manage.manager.incVar(bi == 1 ? parent.getVarMcHandled() : parent.getVarUcHandled());
                return;
            }
            index++;
        }
        uprintf("# Unknown command, Syntax error\r\n");
        Manage.manager.incVar(parent.getVarFailed());
    }

    private int runCmd(int num, String @NotNull [] data, int count) {
        switch (num) {
            case ProtocolConstants.CMD_NOTIFY:
                execNotify(data, count);
                break;
            case ProtocolConstants.CMD_PING:
                execPing(data, count);
                break;
            case ProtocolConstants.CMD_LINK_DOWN:
                execLinkDown(data, count);
                break;
            case ProtocolConstants.CMD_PONG:
                execPong(data, count);
                break;
            case ProtocolConstants.CMD_SYNC:
                break; /* SYNC packets are for routing only */
            case ProtocolConstants.CMD_ADD_CLIENT:
                return execAddClient(data, count);
            case ProtocolConstants.CMD_RM_CLIENT:
                execRmClient(data, count);
                break;
            case ProtocolConstants.CMD_PD:
                execPd(data, count);
                break;
            case ProtocolConstants.CMD_AD:
                execAd(data, count);
                break;
            case ProtocolConstants.CMD_CERT:
                return execCert(data, count);
            case ProtocolConstants.CMD_MULTICAST:
            case ProtocolConstants.CMD_METAR:
                execMetar(data, count);
                break;
            case ProtocolConstants.CMD_PLAN:
                return execPlan(data, count);
            case ProtocolConstants.CMD_REQ_METAR:
                execReqMetar(data, count);
                break;
            case ProtocolConstants.CMD_WEATHER:
                execWeather(data, count);
                break;
            case ProtocolConstants.CMD_NO_WX:
                execNoWx(data, count);
                break;
            case ProtocolConstants.CMD_ADD_WPROFILE:
                execAddWp(data, count);
                break;
            case ProtocolConstants.CMD_DEL_WPROFILE:
                execDelWp(data, count);
                break;
            case ProtocolConstants.CMD_KILL:
                execKill(data, count);
                break;
            case ProtocolConstants.CMD_RESET:
                execReset(data, count);
                break;
        }

        return 1;
    }

    /* Handle a NOTIFY packet */
    private void execNotify(String[] array, int count) {
        Server s;
        int packetCount = 0, hops = 0, feedFlag = 0;

        if (count < 11) {
            return;
        }

        packetCount = NumberUtils.toInt(array[2]);
        hops = NumberUtils.toInt(array[3]);
        feedFlag = NumberUtils.toInt(array[4]);
        s = Server.getServer(array[5]);

        if (s == Server.myServer) {
            return;
        }

        if (s == null) {
            s = new Server(array[5], array[6], array[7], array[8], array[9],
                    NumberUtils.toInt(array[10]), (count == 12 ? array[11] : ""));
        } else {
            s.configure(array[6], array[7], array[8], array[9],
                    (count == 12 ? array[11] : ""));
        }
        /* If this is a notify from our peer, set 'thisserver' */
        if (feedFlag == 0 && hops == 1) thisServer = s;

        /* If this is a server feed, or we already have routing info, we don't
      need to set the routing info */
        if (feedFlag == 1 || (s.getHops() > -1 && hops >= s.getHops())) return;
        s.setPath(this, hops);
    }

    /* Handle a PING packet */
    private void execPing(String @NotNull [] array, int count) {
        String[] subarray = ArrayUtils.subarray(array, 4, array.length);
        parent.sendPong(array[1], Support.catCommand(Arrays.asList(subarray), count - 4, new StringBuilder()));
    }

    /* Handle a PONG packet */
    private void execPong(String[] array, int count) {
        if (count < 5) return;
        Scanner scanner = new Scanner(array[4]);
        int fd = scanner.nextInt();
        Server source = Server.getServer(array[1]);
        if (source == null) {
            return;
        }
        source.receivePong(array[4]);
        if (fd != -1) {
            Main.systemInterface.receivePong(array[1], array[4], array[2], array[3]);
        }
    }

    /* Handle a LINKDOWN packet */
    private void execLinkDown(String[] array, int count) {
        for (int x = 4; x < count; x++) {
            /* Get the next server ident from the packet and look it up */
            Server temp = Server.getServer(array[x]);
            if (temp == null) {
                continue;
            }

            /* If the shortest path to the server is pointing at the direction
             which the packet came from, we can no longer route packets
             to the server in that direction: reset the path to NULL; packets for
             the server will then be broadcast, until a new shortest path is
             found */
            if (temp.getPath() == this) {
                temp.setPath(null, -1);
            }
        }
        Main.serverInterface.sendSync();
    }

    /* Handle an ADD CLIENT packet */
    private int execAddClient(String[] array, int count) {
        if (count < 12) {
            return 1;
        }

        Server location = Server.getServer(array[5]);
        /* If we can't find the server, or the indicated server is our server,
            we don't need the new client. */
        if (location == null || location == Server.myServer) {
            return 1;
        }

        int type = NumberUtils.toInt(array[7]);
        Client c = Client.getClient(array[6]);
        if (c != null) {
            return 0;
        }

        c = new Client(array[4], location, array[6], type, NumberUtils.toInt(array[8]), array[9],
                array[10], NumberUtils.toInt(array[11]));
        if (type == ClientConstants.CLIENT_ATC) {
            Main.clientInterface.sendAa(c, null);
        } else if (type == ClientConstants.CLIENT_PILOT) {
            Main.clientInterface.sendAp(c, null);
        }

        return 1;
    }

    /* Handle an RM CLIENT packet */
    private void execRmClient(String[] array, int count) {
        if (count < 5) {
            return;
        }
        Client c = Client.getClient(array[4]);
        if (c != null) {
            int type = c.getType();
            if (c.getLocation() == Server.myServer) {
                return;
            }
            if (type == ClientConstants.CLIENT_ATC) {
                Main.clientInterface.sendDa(c, null);
            } else if (type == ClientConstants.CLIENT_PILOT) {
                Main.clientInterface.sendDp(c, null);
            }
            c.close();
        }
    }

    private void execAd(String @NotNull [] array, int count) {
        if (count < 12) return;
        Client who = Client.getClient(array[4]);
        if (who == null) return;
        who.updateAtc(ArrayUtils.subarray(array, 5, array.length));
        Main.clientInterface.sendAtcPos(who, null);
    }

    private void execPd(String @NotNull [] array, int count) {
        if (count < 14) return;
        Client who = Client.getClient(array[5]);
        if (who == null) return;
        who.updatePilot(ArrayUtils.subarray(array, 4, array.length));
        Main.clientInterface.sendPilotPos(who, null);
    }

    private int execCert(String[] array, int count) {
        if (count < 10) return 0;
        ConfigGroup group = Main.configManager.getGroup("hosts");
        ConfigEntry entry = null;
        if (group != null) {
            entry = group.getEntry("certificates");
        }
        int ok = entry != null ? entry.inList(array[9]) : 1;
        int level = NumberUtils.toInt(array[7]);
        Certificate c = Certificate.getCert(array[5]);
        long now = NumberUtils.toLong(array[8]) * 1000;
        switch (NumberUtils.toInt(array[4])) {
            case ProtocolConstants.CERT_ADD:
                if (c == null) {
                    if (ok == 1) {
                        c = new Certificate(array[5], array[6], level, now, array[9]);
                    }
                } else if (now > c.getCreation()) {
                    if (ok == 1) {
                        c.configure(null, NumberUtils.toInt(array[7]), now, array[9]);
                    }
                } else {
                    return 0;
                }
                break;
            case ProtocolConstants.CERT_DELETE:
                if (c != null) {
                    if (ok == 1) {
                        c.close();
                    } else {
                        return 0;
                    }
                }
                break;
            case ProtocolConstants.CERT_MODIFY:
                if (c != null && now > c.getCreation()) {
                    if (ok == 1) {
                        c.configure(array[6], NumberUtils.toInt(array[7]), now, array[9]);
                    } else {
                        return 0;
                    }
                }
                break;
        }
        return 1;
    }

    private void execMulticast(String @NotNull [] array, int count) {
        if (count < 7) return;
        Client source = Client.getClient(array[5]);
        int cmd = NumberUtils.toInt(array[4]);
        if (cmd > ProtocolConstants.CL_MAX) {
            return;
        }
        String dest;
        Client c = null;
        switch (array[0].charAt(0)) {
            case '*', '@' -> dest = array[0];
            default -> {
                c = Client.getClient(array[0] + 1);
                if (c == null) return;
                dest = array[0] + 1;
            }
        }
        String[] subarray = ArrayUtils.subarray(array, 6, array.length);
        Main.clientInterface.sendGeneric(dest, c, null, source, array[5], Support.catCommand(Arrays.asList(subarray), count - 6, new StringBuilder()), cmd);
    }

    private int execPlan(String @NotNull [] array, int count) {
        if (count < 21) return 0;
        int revision = NumberUtils.toInt(array[5]);
        Client who = Client.getClient(array[4]);
        if (who == null) return 1;
        if (who.getPlan() != null && who.getPlan().getRevision() >= revision) return 0;
        who.handleFp(ArrayUtils.subarray(array, 6, array.length));
        who.getPlan().setRevision(revision);
        Main.clientInterface.sendPlan(null, who, 400);
        return 1;
    }

    private void execReqMetar(String[] array, int count) {
        if (count < 8) return;
        MetarManage.metarManager.requestMetar(array[4], array[5], NumberUtils.toInt(array[6]), NumberUtils.toInt(array[7]));
    }

    private void execWeather(String @NotNull [] array, int count) {
        if (count < 56) return;
        WProfile prof = new WProfile(array[4], 0, null);
        int fd = NumberUtils.toInt(array[5]);
        prof.loadArray(ArrayUtils.subarray(array, 6, array.length), count - 6);
        if (fd == -1) {
            Client c = Client.getClient(array[0].substring(1));
            if (c == null) {
                return;
            }
             Main.clientInterface.sendWeather(c, prof);
        } else {
             Main.systemInterface.receiveWeather(fd, prof);
        }
    }

    private void execMetar(String[] array, int count) {
        if (count<7) return;
        int fd = NumberUtils.toInt(array[5]);
        if (fd!=-1) {
            Main.systemInterface.receiveMetar(fd, array[4], array[6]);
        } else {
            Client c=Client.getClient(array[0]+1);
            if (c == null) return;
            Main.clientInterface.sendMetar(c, array[6]);
        }
    }

    private void execNoWx(String[] array, int count) {
        if (count < 6) return;
        int fd = NumberUtils.toInt(array[5]);
        if (fd == -1) {
            Client c = Client.getClient(array[0].substring(1));
            if (c == null) return;
            Main.clientInterface.sendNoWx(c, array[4]);
        } else {
            Main.systemInterface.receiveNoWx(fd, array[4]);
        }
    }

    private void execAddWp(String @NotNull [] array, int count) {
        /* Non METAR hosts, should not store weather profiles */
        if (count < 57 || MetarManage.metarManager.getSource() == MetarSource.SOURCE_NETWORK) return;
        ConfigGroup group = Main.configManager.getGroup("hosts");
        ConfigEntry entry = null;
        if (group != null) {
            entry = group.getEntry("weather");
        }
        int ok = entry != null ? entry.inList(array[6]) : 1;
        if (ok == 0) {
            return;
        }

        Scanner scanner = new Scanner(array[5]);
        long version = scanner.nextLong();
        WProfile prof = Weather.getWProfile(array[4]);
        if (prof != null && prof.getVersion() >= version) {
            return;
        }
        if (prof != null) {
            prof.close();
        }
        prof = new WProfile(array[4], version, array[6]);
        prof.loadArray(ArrayUtils.subarray(array, 7, array.length), count - 7);
        prof.genRawCode();
    }

    private void execDelWp(String[] array, int count) {
        if (count < 5 || MetarManage.metarManager.getSource() == MetarSource.SOURCE_NETWORK) return;
        ConfigGroup group = Main.configManager.getGroup("hosts");
        ConfigEntry entry = null;
        if (group != null) {
            entry = group.getEntry("weather");
        }
        int ok = entry != null ? entry.inList(array[1]) : 1;
        if (ok == 0) {
            return;
        }
        WProfile prof = Weather.getWProfile(array[4]);
        if (prof == null || prof.getActive() == 0) {
            return;
        }
        prof.close();
    }

    private void execKill(String[] array, int count) {
        if (count < 6) {
            return;
        }

        Client c = Client.getClient(array[4]);
        if (c == null) {
            return;
        }
        Main.clientInterface.handleKill(c, array[5]);
    }

    private void execReset(String @NotNull [] array, int count) {
        Server s = Server.getServer(array[1]);
        if (s == null) {
            return;
        }
        s.setpCount(0);
    }

    public @Nullable Server getThisServer() {
        return thisServer;
    }

    public void setThisServer(@Nullable Server thisServer) {
        this.thisServer = thisServer;
    }

    public long getStartupTime() {
        return startupTime;
    }

    public void setStartupTime(long startupTime) {
        this.startupTime = startupTime;
    }

    public int getClientOk() {
        return clientOk;
    }

    public void setClientOk(int clientOk) {
        this.clientOk = clientOk;
    }
}
