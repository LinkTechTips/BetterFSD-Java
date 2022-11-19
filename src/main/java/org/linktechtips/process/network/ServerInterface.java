/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.process.network;

import org.linktechtips.Main;
import org.linktechtips.manager.Manage;
import org.linktechtips.model.Certificate;
import org.linktechtips.model.Client;
import org.linktechtips.model.Flightplan;
import org.linktechtips.model.Server;
import org.linktechtips.support.Support;
import org.linktechtips.user.AbstractUser;
import org.linktechtips.user.ServerUser;
import org.linktechtips.weather.WProfile;
import org.linktechtips.constants.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.channels.SocketChannel;

public class ServerInterface extends TcpInterface {
    private int packetCount, varMcDrops, varIntErr;

    private int varMcHandled, varUcHandled, varUcOverRun, varFailed, varShape, varBounce;

    private String serverIdent;

    private long lastSync;

    public ServerInterface(int port, String code, String d) {
        super(port, code, d);
        packetCount = 0;
        varMcHandled = Manage.manager.addVar("protocol.multicast.handled", ManageVarType.ATT_INT);
        varMcDrops = Manage.manager.addVar("protocol.multicast.dropped", ManageVarType.ATT_INT);
        varUcHandled = Manage.manager.addVar("protocol.unicast.handled", ManageVarType.ATT_INT);
        varUcOverRun = Manage.manager.addVar("protocol.unicast.overruns", ManageVarType.ATT_INT);
        varFailed = Manage.manager.addVar("protocol.errors.invalidcommand", ManageVarType.ATT_INT);
        varBounce = Manage.manager.addVar("protocol.errors.bounce", ManageVarType.ATT_INT);
        varShape = Manage.manager.addVar("protocol.errors.shape", ManageVarType.ATT_INT);
        varIntErr = Manage.manager.addVar("protocol.errors.integer", ManageVarType.ATT_INT);
        serverIdent = Server.myServer.getIdent();
    }

    @Override
    public boolean run() {
        long now = Support.mtime();
        boolean busy = super.run();
        if (now - lastSync > GlobalConstants.SYNC_TIMEOUT) {
            sendSync();
        }
        for (AbstractUser temp : users) {
            ServerUser stemp = (ServerUser) temp;
            if (stemp.getClientOk() == 0 && now - stemp.getStartupTime() > GlobalConstants.SERVER_MAX_TOOK) {
                stemp.kill(NetworkConstants.KILL_INIT_TIMEOUT);
            }
        }
        return busy;
    }

    @Override
    public void newUser(SocketChannel client, String peerName, int port, int g) {
        insertUser(new ServerUser(client, this, peerName, port, g));
    }

    public void incPacketCount() {
        packetCount++;
        /* Now check if the integer looped */
        if (packetCount < 0 || packetCount > 2_000_000_000) {
            sendReset();
        }
    }

    /* Send a SYNC packet */
    public void sendSync() {
        sendPacket(null, null, ProtocolConstants.CMD_SYNC, "*", serverIdent, packetCount, 0, 0, "");
        lastSync = Support.mtime();
        incPacketCount();
    }

    /* Send a NOTIFY packet to a destination */
    public void sendServerNotify(@NotNull String dest, @NotNull Server subject, AbstractUser toWho) {
        String buf = String.format("%d:%s:%s:%s:%s:%s:%d:%s", subject == Server.myServer ? 0 : 1, subject.getIdent(),
                subject.getName(), subject.getEmail(), subject.getHostName(), subject.getVersion(),
                subject.getFlags(), subject.getLocation());
        sendPacket(null, toWho, ProtocolConstants.CMD_NOTIFY, dest, serverIdent, packetCount, 0, 1, buf);
        incPacketCount();
    }

    /* Send a REQMETAR packet */
    public void sendReqMetar(String client, String metar, int fd, int parsed, @NotNull Server dest) {
        String buf = String.format("%s:%s:%d:%d", client, metar, parsed, fd);
        sendPacket(null, null, ProtocolConstants.CMD_REQ_METAR, dest.getIdent(), serverIdent,
                packetCount, 0, 0, buf);
        incPacketCount();
    }

    /* Send a LINKDOWN packet to a destination */
    public void sendLinkDown(String data) {
        sendPacket(null, null, ProtocolConstants.CMD_LINK_DOWN, "*", serverIdent, packetCount,
                0, 1, data);
        incPacketCount();
    }

    /* Send a PONG packet to a destination */
    public void sendPong(@NotNull String dest, String data) {
        sendPacket(null, null, ProtocolConstants.CMD_PONG, dest, serverIdent, packetCount, 0, 0, data);
        incPacketCount();
    }

    /* Broadcast a PING packet to a destination*/
    public void sendPing(@NotNull String dest, String data) {
        sendPacket(null, null, ProtocolConstants.CMD_PING, dest, serverIdent, packetCount, 0, 0, data);
        incPacketCount();
    }

    /* Send an ADDCLIENT packet to a destination */
    public void sendAddClient(@NotNull String dest, @NotNull Client who, AbstractUser direction, AbstractUser source, int feed) {
        String buf = String.format("%s:%s:%s:%d:%d:%s:%s:%d", who.getCid(), who.getLocation().getIdent(),
                who.getCallsign(), who.getType(), who.getRating(), who.getProtocol(), who.getRealName(), who.getSimType());
        sendPacket(null, direction, ProtocolConstants.CMD_ADD_CLIENT, dest, serverIdent, packetCount, 0, 0, buf);
        incPacketCount();
        /* This command is important for clients as well, so if it's not a feed,
        clients should get it as well */
        if (feed == 0) {
            if (who.getType() == ClientConstants.CLIENT_ATC) {
                Main.clientInterface.sendAa(who, source);
            } else if (who.getType() == ClientConstants.CLIENT_PILOT) {
                Main.clientInterface.sendAp(who, source);
            }
        }
    }

    /* Send an RMCLIENT packet to a destination */
    public void sendRmClient(AbstractUser direction, @NotNull String dest, @NotNull Client who, AbstractUser ex) {
        String buf = who.getCallsign();
        sendPacket(null, direction, ProtocolConstants.CMD_RM_CLIENT, dest, serverIdent, packetCount, 0, 0, buf);
        incPacketCount();
        /* This command is important for clients as well, so if it's send to the
        broadcast address, clients should get it as well */
        if (dest.equals("*")) {
            if (who.getType() == ClientConstants.CLIENT_ATC) {
                Main.clientInterface.sendDa(who, ex);
            } else {
                Main.clientInterface.sendDp(who, ex);
            }
        }
    }

    public void sendPilotData(@NotNull Client who, AbstractUser ex) {
        String data = String.format("%s:%s:%d:%d:%.5f:%.5f:%d:%d:%d:%d", who.getIdentFlag(),
                who.getCallsign(), who.getTransponder(), who.getRating(), who.getLat(), who.getLon(),
                who.getAltitude(), who.getGroundSpeed(), who.getPbh(), who.getFlags());
        sendPacket(null, null, ProtocolConstants.CMD_PD, "*", serverIdent, packetCount, 0, 0, data);
        Main.clientInterface.sendPilotPos(who, ex);
        incPacketCount();
    }

    public void sendAtcData(@NotNull Client who, AbstractUser ex) {
        String data = String.format("%s:%d:%d:%d:%d:%.5f:%.5f:%d", who.getCallsign(),
                who.getFrequency(), who.getFacilityType(), who.getVisualRange(), who.getRating(), who.getLat(), who.getLon(),
                who.getAltitude());
        sendPacket(null, null, ProtocolConstants.CMD_AD, "*", serverIdent, packetCount, 0, 0, data);
        Main.clientInterface.sendAtcPos(who, ex);
        incPacketCount();
    }

    public void sendCert(@NotNull String dest, int cmd, @NotNull Certificate who, AbstractUser direction) {
        String data = String.format("%d:%s:%s:%d:%d:%s", cmd, who.getCid(), who.getPassword(), who.getLevel(),
                who.getCreation(), who.getOrigin());
        sendPacket(null, direction, ProtocolConstants.CMD_CERT, dest, serverIdent, packetCount, 0, 0, data);
        incPacketCount();
    }

    public void sendReset() {
        sendPacket(null, null, ProtocolConstants.CMD_RESET, "*", serverIdent, packetCount, 0, 0, "");
        packetCount = 0;
    }

    public int sendMulticast(@Nullable Client source, @NotNull String dest, String s, int cmd, int multiOk, AbstractUser ex) {
        Client destination = null;
        String servDest;
        String sourceStr = source != null ? source.getCallsign() : "server";
        if (source != null && dest.equalsIgnoreCase("server")) {
            if (cmd == ProtocolConstants.CL_PING) {
                Main.clientInterface.sendGeneric(source.getCallsign(), source, null,
                        null, "server", s, ProtocolConstants.CL_PONG);
            }
            return 1;
        }
        switch (dest.charAt(0)) {
            case '@', '*' -> {
                if (multiOk == 0) {
                    return 0;
                }
                servDest = dest;
            }
            default -> {
                servDest = String.format("%%%s", dest);
                destination = Client.getClient(dest);
                if (destination == null) {
                    return 0;
                }
            }
        }
        String data = String.format("%d:%s:%s", cmd, sourceStr, s);
        sendPacket(null, null, ProtocolConstants.CMD_MULTICAST, servDest, serverIdent, packetCount, 0, 0, data);
        incPacketCount();
        Main.clientInterface.sendGeneric(dest, destination, ex, source, sourceStr, s, cmd);
        return 1;
    }

    public void sendPlan(@NotNull String dest, @NotNull Client who, AbstractUser direction) {
        Flightplan plan = who.getPlan();
        if (plan == null) {
            return;
        }
        String buf = String.format("%s:%d:%c:%s:%d:%s:%d:%d:%s:%s:%d:%d:%d:%d:%s:%s:%s",
                who.getCallsign(), plan.getRevision(), plan.getType(), plan.getAircraft(),
                plan.getTasCruise(), plan.getDepAirport(), plan.getDepTime(),
                plan.getActDepTime(), plan.getAlt(), plan.getDestAirport(), plan.getHrsEnroute(),
                plan.getMinEnroute(), plan.getHrsFuel(), plan.getMinFuel(), plan.getAltAirport(),
                plan.getRemarks(), plan.getRoute());
        sendPacket(null, direction, ProtocolConstants.CMD_PLAN, dest, serverIdent, packetCount, 0, 0, buf);
        incPacketCount();
        Main.clientInterface.sendPlan(null, who, 400);
    }

    public void sendWeather(@NotNull String dest, int fd, @NotNull WProfile w) {
        if (dest.equalsIgnoreCase(serverIdent)) {
            Main.systemInterface.receiveWeather(fd, w);
            return;
        }

        if (dest.charAt(0) == '%') {
            Client c = Client.getClient(dest.substring(1));
            if (c == null) {
                return;
            }
            if (c.getLocation() == Server.myServer) {
                Main.clientInterface.sendWeather(c, w);
                return;
            }
        }

        String weather = w.print();
        String data = String.format("%s:%d:%s", w.getName(), fd, weather);
        sendPacket(null, null, ProtocolConstants.CMD_WEATHER, dest, serverIdent, packetCount, 0, 0, data);
        incPacketCount();
    }

    public void sendMetar(@NotNull String dest, int fd, String station, String data) {
        if (dest.equalsIgnoreCase(serverIdent)) {
             Main.systemInterface.receiveMetar(fd, station, data);
            return;
        }
        if (dest.charAt(0) == '%') {
            Client c = Client.getClient(dest.substring(1));
            if (c == null) {
                return;
            }
            if (c.getLocation() == Server.myServer) {
                Main.clientInterface.sendMetar(c, data);
                return;
            }
        }
        String buf = String.format("%s:%d:%s", station, fd, data);
        sendPacket(null, null, ProtocolConstants.CMD_METAR, dest, serverIdent, packetCount, 0, 0, buf);
        incPacketCount();
    }

    public void sendNoWx(@NotNull String dest, int fd, String station) {
        if (dest.equalsIgnoreCase(serverIdent)) {
            Main.systemInterface.receiveNoWx(fd, station);
            return;
        }
        if (dest.charAt(0) == '%') {
            Client c = Client.getClient(dest.substring(1));
            if (c == null) {
                return;
            }
            if (c.getLocation() == Server.myServer) {
                Main.clientInterface.sendNoWx(c, station);
                return;
            }
        }
        String buf = String.format("%s:%d", station, fd);
        sendPacket(null, null, ProtocolConstants.CMD_NO_WX, dest, serverIdent, packetCount, 0, 0, buf);
        incPacketCount();
    }

    public void sendAddWp(AbstractUser direction, @NotNull WProfile wp) {
        String weather = wp.print();
        String buf = String.format("%s:%d:%s:%s", wp.getName(), wp.getVersion(), wp.getOrigin(), weather);
        sendPacket(null, direction, ProtocolConstants.CMD_ADD_WPROFILE, "*", serverIdent, packetCount, 0, 0, buf);
        incPacketCount();
    }

    public void sendDelWp(@NotNull WProfile wp) {
        sendPacket(null, null, ProtocolConstants.CMD_DEL_WPROFILE, "*", serverIdent, packetCount, 0, 0, wp.getName());
        incPacketCount();
    }

    public void sendKill(@NotNull Client who, String reason) {
        if (who.getLocation() == Server.myServer) {
            Main.clientInterface.handleKill(who, reason);
            return;
        }
        String data = String.format("%s:%s", who.getCallsign(), reason);
        sendPacket(null, null, ProtocolConstants.CMD_KILL, who.getLocation().getIdent(), serverIdent,
                packetCount, 0, 1, data);
        incPacketCount();
    }

    /* Send the packet on its way. This function will also do the routing */
    public void sendPacket(AbstractUser exclude, @Nullable AbstractUser direction, int cmdNum,
                           @NotNull String to, String from, int pc, int hc, int bi, String data) {
        StringBuilder buf = new StringBuilder();
        /* variable to determine wheter or not to do the softlimit check on the
      server connection output buffer. currently only do the check on
      client position updates */
        int slCheck = (cmdNum == ProtocolConstants.CMD_PD || cmdNum == ProtocolConstants.CMD_AD) ? 1 : 0;

        /* Increase the hopcount */
        hc++;

        /* Assemble the packet */
        assemble(buf, cmdNum, to, from, bi, pc, hc, data);


        /* Now look at the destionation field, to determine the route for this
      packet */

        switch (to.charAt(0)) {
            case '@': /* Fallthrough to broadcast */

      /* This is a broadcast packet.
         Note: '*P' and '*A' are broadcast destinations too! */
            case '*':
                /* Reassemble the packet to indicate a broadcast */
                if (bi == 0) {
                    assemble(buf, cmdNum, to, from, 1, pc, hc, data);
                }
                for (AbstractUser tempUser : users) {
                    if (isServerOk((ServerUser) tempUser, exclude, cmdNum)) {
                        tempUser.uslprintf("%s\r\n", slCheck, buf);
                    }
                }
                break;
        /* This is a packet for a pilot. Lookup the pilot, and send in the
         direction of the appropriate server */
            case '%':
                for (Client tempClient : Client.clients) {
                    if (tempClient.getCallsign().equals(to.substring(1))) {
                        /* We got the pilot, and his connected server, now if we know the
                       correct path, send it; otherwise we'll have to broadcast the
                       packet */
                        Server tempServer = tempClient.getLocation();
                        /* It the pilot is connected to this server, don't send out
                        the packet to other servers */
                        if (tempServer == Server.myServer) {
                            break;
                        }
                        if (tempServer.getPath() != null) {
                            tempServer.getPath().uslprintf("%s\r\n", slCheck, buf);
                        } else {
                            /* Reassemble the packet to indicate a broadcast */
                            if (bi == 0) {
                                assemble(buf, cmdNum, to, from, 1, pc, hc, data);
                            }
                            for (AbstractUser tempUser : users) {
                                if (isServerOk((ServerUser) tempUser, exclude, cmdNum)) {
                                    tempUser.uslprintf("%s\r\n", slCheck, buf);
                                }
                            }
                        }
                        break;
                    }
                }
                break;

            /* This packet is on its way to a single server */
            default:
                for (Server tempServer : Server.servers) {
                    if (tempServer.getIdent().equals(to)) {
                        if (tempServer.getPath() != null) {
                            tempServer.getPath().uslprintf("%s\r\n", slCheck, buf);
                        } else {
                            /* Reassemble the packet to indicate a broadcast */
                            if (bi == 0) {
                                assemble(buf, cmdNum, to, from, 1, pc, hc, data);
                            }
                            for (AbstractUser tempUser : users) {
                                if (isServerOk((ServerUser) tempUser, exclude, cmdNum)) {
                                    tempUser.uslprintf("%s\r\n", slCheck, buf);
                                }
                            }
                        }
                    }
                    break;
                }
                break;
        }
    }

    /* This routine is called whenever a client is dropped. We have to check
   here if there's a silent server connected to us. In that case, we'll
   have to send him a RMCLIENT */
    public void clientDropped(@NotNull Client who) {
        for (AbstractUser temp : users) {
            ServerUser s = (ServerUser) temp;
            if (s.getThisServer() != null && (s.getThisServer().getFlags() & ServerConstants.SERVER_SILENT) != 0) {
                sendRmClient(s, s.getThisServer().getName(), who, null);
            }
        }
    }

    private boolean isServerOk(@NotNull ServerUser x, AbstractUser exclude, int cmdNum) {
        return (x != exclude) && (ProtocolConstants.SILENT_OK[cmdNum] == 1 || x.getThisServer() == null ||
                (x.getThisServer().getFlags() & ServerConstants.SERVER_SILENT) == 0);
    }

    public void assemble(@NotNull StringBuilder buf, int cmdNum, String to, String from, int bi, int pc, int hc, String data) {
        buf.append(String.format("%s:%s:%s:%c%d:%d:%s", ProtocolConstants.CMD_NAMES[cmdNum], to, from,
                bi > 0 ? 'B' : 'U', pc, hc, data));
    }

    public int getPacketCount() {
        return packetCount;
    }

    public void setPacketCount(int packetCount) {
        this.packetCount = packetCount;
    }

    public int getVarMcDrops() {
        return varMcDrops;
    }

    public void setVarMcDrops(int varMcDrops) {
        this.varMcDrops = varMcDrops;
    }

    public int getVarIntErr() {
        return varIntErr;
    }

    public void setVarIntErr(int varIntErr) {
        this.varIntErr = varIntErr;
    }

    public int getVarMcHandled() {
        return varMcHandled;
    }

    public void setVarMcHandled(int varMcHandled) {
        this.varMcHandled = varMcHandled;
    }

    public int getVarUcHandled() {
        return varUcHandled;
    }

    public void setVarUcHandled(int varUcHandled) {
        this.varUcHandled = varUcHandled;
    }

    public int getVarUcOverRun() {
        return varUcOverRun;
    }

    public void setVarUcOverRun(int varUcOverRun) {
        this.varUcOverRun = varUcOverRun;
    }

    public int getVarFailed() {
        return varFailed;
    }

    public void setVarFailed(int varFailed) {
        this.varFailed = varFailed;
    }

    public int getVarShape() {
        return varShape;
    }

    public void setVarShape(int varShape) {
        this.varShape = varShape;
    }

    public int getVarBounce() {
        return varBounce;
    }

    public void setVarBounce(int varBounce) {
        this.varBounce = varBounce;
    }

    public String getServerIdent() {
        return serverIdent;
    }

    public void setServerIdent(String serverIdent) {
        this.serverIdent = serverIdent;
    }

    public long getLastSync() {
        return lastSync;
    }

    public void setLastSync(long lastSync) {
        this.lastSync = lastSync;
    }
}
