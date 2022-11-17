/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.user;

import org.linktechtips.Main;
import org.linktechtips.manager.Manage;
import org.linktechtips.model.Certificate;
import org.linktechtips.model.Client;
import org.linktechtips.model.Server;
import org.linktechtips.process.config.ConfigEntry;
import org.linktechtips.process.config.ConfigGroup;
import org.linktechtips.process.metar.MetarManage;
import org.linktechtips.process.network.Guard;
import org.linktechtips.process.network.SystemInterface;
import org.linktechtips.support.Support;
import org.linktechtips.weather.WProfile;
import org.linktechtips.weather.Weather;
import org.linktechtips.constants.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SystemUser extends AbstractUser {

    private int authorized;

    private double lat, lon;

    public SystemUser(SocketChannel fd, SystemInterface p, String pn, int portNum, int g) {
        super(fd, p, pn, portNum, g);
        // TODO logs[L_MAX]
        uprintf(String.format("# %s system interface ready.\n", GlobalConstants.PRODUCT));
        //uprintf("# Logs: Emerg: %d, Alert: %d, Crit: %d, Err: %d, Warn: %d, Info:"\
        //     " %d, Debug: %d\r\n", logs[0], logs[1], logs[2], logs[3], logs[4], logs[5], logs[6]);
        uprintf("# Type 'help' for help.\r\n");

        ConfigGroup group = Main.configManager.getGroup("system");
        ConfigEntry entry = null;
        if (group != null) {
            entry = group.getEntry("password");
        }
        String pwd = entry != null ? entry.getData() : null;
        authorized = StringUtils.isBlank(pwd) ? 1 : 0;
    }

    public void parse(@NotNull String s) {
        setActive();
         doParse(s);
    }

    private void information() {
        uprintf("Number of servers in the network: %d\r\n", Server.servers.size());
        uprintf("Number of clients in the network: %d\r\n", Client.clients.size());
        uprintf("Number of certificates in the network: %d\r\n", Certificate.certs.size());
    }

    private void usage(int cmd, String string) {
        uprintf("Usage: %s %s\r\n", SystemConstants.SYS_CMDS[cmd], string);
    }

    private void list(@NotNull String s) {
        String str = s.stripLeading();
        if (StringUtils.isEmpty(str)) {
            str = null;
        }
        int nVars = Manage.manager.getNVars();
        for (int x = 0; x < nVars; x++) {
            String buf = Manage.manager.sprintValue(x);
            if (buf != null) {
                String varName = Objects.requireNonNull(Manage.manager.getVar(x)).getName();
                if (str == null || varName.contains(str)) {
                    uprintf("%s=%s\r\n", varName, buf);
                }
            }
        }
    }

    private void doParse(@NotNull String s) {
        List<String> array = new ArrayList<>();
        int count = Support.breakArgs(s, array, 100);
        if (count == 0) {
            return;
        }
        for (int i = 0; i < SystemConstants.SYS_CMDS.length; i++) {
            if (array.get(0).equalsIgnoreCase(SystemConstants.SYS_CMDS[i])) {
                if (SystemConstants.NEED_AUTHORIZATION[i] == 1 && authorized == 0) {
                    uprintf("Not authorized, use 'pwd'.\r\n");
                } else {
                    runCmd(i, array.subList(1, array.size() - 1).toArray(new String[0]), count - 1);
                }
                return;
            }
        }
        uprintf("Syntax error.\r\n");
    }

    private void runCmd(int num, String @NotNull [] array, int count) {
        switch (num) {
            case SystemConstants.SYS_SERVERS -> execServers(array, count, 1);
            case SystemConstants.SYS_SERVERS2 -> execServers(array, count, 2);
            case SystemConstants.SYS_INFORMATION -> information();
            case SystemConstants.SYS_PING -> execPing(array, count);
            case SystemConstants.SYS_CONNECT -> execConnect(array, count);
            case SystemConstants.SYS_ROUTE -> execRoute(array, count);
            case SystemConstants.SYS_DISCONNECT -> execDisconnect(array, count);
            case SystemConstants.SYS_HELP -> execHelp(array, count);
            case SystemConstants.SYS_QUIT -> kill(NetworkConstants.KILL_COMMAND);
            case SystemConstants.SYS_STAT -> list((count == 0 ? "" : array[0]));
            case SystemConstants.SYS_SAY -> execSay(array, count);
            case SystemConstants.SYS_CLIENTS -> execClients(array, count);
            case SystemConstants.SYS_CERT -> execCert(array, count);
            case SystemConstants.SYS_DISTANCE -> execDistance(array, count);
            case SystemConstants.SYS_TIME -> execTime(array, count);
            case SystemConstants.SYS_WEATHER -> execWeather(array, count);
            case SystemConstants.SYS_RANGE -> execRange(array, count);
            case SystemConstants.SYS_LOG -> execLog(array, count);
            case SystemConstants.SYS_PWD -> execPwd(array, count);
            case SystemConstants.SYS_WALL -> execWall(array, count);
            case SystemConstants.SYS_DELGUARD -> execDelGuard(array, count);
            case SystemConstants.SYS_METAR -> execMetar(array, count);
            case SystemConstants.SYS_WP -> execWp(array, count);
            case SystemConstants.SYS_KILL -> execKill(array, count);
            case SystemConstants.SYS_DUMP -> execDump(array, count);
            case SystemConstants.SYS_POS -> execPos(array, count);
            case SystemConstants.SYS_REFMETAR -> execRefMetar(array, count);
        }
    }

    private void execHelp(String[] array, int count) {
        int copyMode = 0, topicMode = 0, globalMode = 0;
        String s = count > 0 ? array[0] : null;
        String topic;
        if (s != null) {
            topic = s;
        } else {
            topic = "global";
            globalMode = 1;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(FsdPath.PATH_FSD_HELP), StandardCharsets.UTF_8);
        } catch (IOException e) {
            uprintf("Could not open help file 'help.txt'\r\n");
            return;
        }

        if ("topics".equals(topic)) {
            topicMode = 1;
        }

        for (String line : lines) {
            if (line.startsWith("%topic", 6)) {
                if (copyMode == 1) {
                    break;
                }

                Pattern pattern = Pattern.compile("%topic (\\w+)");
                Matcher matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                String top = matcher.group(1);
                String p = "";
                if (top.contains(":")) {
                    String[] split = top.split(":");
                    top = split[0];
                    p = split[1];
                }

                if (topicMode == 1) {
                    uprintf("%s:%s", top, p);
                    continue;
                }

                if (top.equalsIgnoreCase(topic)) {
                    String header = "FSD ONLINE DOCUMENTATION";
                    uprintf("%s %55s", header, p);
                    StringBuilder tmp = new StringBuilder();
                    while (tmp.length() < 79) {
                        tmp.append("=");
                    }
                    line = tmp.toString();
                    uprintf("%s\r\n", line);
                    copyMode = 1;
                }
            } else if (copyMode == 1) {
                uprintf("%s", line);
            }
        }

        if (globalMode == 1) {
            String l = "";
            for (int i = 0; i < SystemConstants.SYS_CMDS.length; i++) {
                if (StringUtils.isNotEmpty(l)) {
                    l += " ";
                }
                l += SystemConstants.SYS_CMDS[i];
                if (l.length() > 65) {
                    uprintf("%s\r\n", l.toUpperCase());
                    l = "";
                }
            }
            if (StringUtils.isNotEmpty(l)) {
                uprintf("%s\r\n", l.toUpperCase());
            }
        }
    }

    private void execConnect(String[] array, int count) {
        if (count < 1) {
            List<AbstractUser> users = Main.serverInterface.getUsers();
            if (users.size() == 0)
                uprintf("No active server connections.\r\n");
            else {
                uprintf("fd  Limit  Out-Q  In-Q  Feed Peer\r\n");
                uprintf("---------------------------------\r\n");
                for (AbstractUser user : users) {
                    ServerUser su = (ServerUser) user;
                    uprintf("%02d %5d   %5d %5d %5d %s:%d (%s)\r\n", su.getSocketChannel(), su.getOutBufSoftLimit(),
                            su.outBuf.position(), su.inBuf.position(), su.feed, su.peer, su.port,
                            su.getThisServer() != null ? su.getThisServer().getIdent() : "<Unknown>");
                }
            }
            List<Guard> guards = Main.serverInterface.getGuards();
            if (guards.size() == 0)
                uprintf("No pending connections.\r\n");
            else {
                uprintf("Pending connections:\r\n");
                for (Guard guard : guards) {
                    uprintf("  %s:%d\r\n", guard.getHost(), guard.getPort());
                }
            }
            return;
        }
        int port = 3011;
        if (count > 1) port = NumberUtils.toInt(array[1]);
        if (Main.serverInterface.addUser(array[0], port, this) == 1) {
            Main.serverInterface.sendServerNotify("*", Server.myServer, null);
        }
    }

    private void execDisconnect(String[] array, int count) {
        if (count == 0) {
            usage(SystemConstants.SYS_DISCONNECT, "<fd>");
            return;
        }
        int fd = NumberUtils.toInt(array[0]);
        List<AbstractUser> users = Main.serverInterface.getUsers();
        for (AbstractUser temp : users) {
            if (temp.getFd() == fd) {
                temp.kill(NetworkConstants.KILL_COMMAND);
                uprintf("Disconnected\r\n");
                return;
            }
        }
        uprintf("No such connection was found.\r\n");
    }

    private void execPing(String[] array, int count) {
        if (count == 0) {
            usage(SystemConstants.SYS_PING, "<ident>");
            return;
        }

        long now = Support.mtime();
        String data = String.format("%d %d", fd, now);
        Main.serverInterface.sendPing(array[0], data);
    }

    private void execServers(String[] array, int count, int type) {
        Server match = null, temp = null;
        if (count == 1) {
            for (Server server : Server.servers) {
                temp = server;
                if (temp.getName().contains(array[0]) || temp.getIdent().contains(array[0])) {
                    if (match != null) {
                        break;
                    }
                    match = temp;
                }
            }
        }
        if (type == 1) {
            uprintf("ID         Host                      Fl Hops Lag  Name\r\n");
            uprintf("-------------------------------------------------------------\r\n");
            for (Server server : Server.servers)
                temp = server;
            if (count == 0 || Objects.requireNonNull(temp).getName().contains(array[0]) ||
                    temp.getIdent().contains(array[0]))
                uprintf("%-10s %-25s %c%c   %02d  %02d  %s\r\n", Objects.requireNonNull(temp).getIdent(),
                        temp.getHostName(), (temp.getFlags() & ServerConstants.SERVER_METAR) != 0 ? 'M' : '-',
                        (temp.getFlags() & ServerConstants.SERVER_SILENT) != 0 ? 'S' : '-',
                        temp.getHops(), temp.getLag(), temp.getName());
            uprintf("\r\n");
        } else {
            uprintf("ID         Version     Mail                 Location\r\n");
            uprintf("-------------------------------------------------------------\r\n");
            for (Server server : Server.servers)
                if (count == 0 || Objects.requireNonNull(temp).getName().contains(array[0]) ||
                        temp.getIdent().contains(array[0]))
                    uprintf("%-10s %-11s %-20s %s\r\n", Objects.requireNonNull(temp).getIdent(),
                            temp.getVersion(), temp.getEmail(), temp.getLocation());
            uprintf("\r\n");
        }
    }

    private void execRoute(String[] array, int count) {
        uprintf("ID         Next hop\r\n");
        uprintf("-------------------\r\n");
        String dest;
        for (Server temp : Server.servers) {
            if (count == 0 || temp.getName().contains(array[0]) ||
                    temp.getIdent().contains(array[0])) {
                if (temp == Server.myServer) {
                    dest = "*local*";
                } else if (temp.getPath() == null) {
                    dest = "*broadcast*";
                } else {
                    dest = String.format("%s:%d", temp.getPath().getPeer(), temp.getPath().getPort());
                }
                uprintf("%-10s %s\r\n", temp.getIdent(), dest);
            }
        }
        uprintf("\r\n");
    }

    private void execSay(String @NotNull [] array, int count) {
        if (count < 2) {
            usage(SystemConstants.SYS_SAY, "<user> <text>");
            return;
        }
        StringBuilder buf = new StringBuilder();
        String[] subarray = ArrayUtils.subarray(array, 1, array.length);
        Support.catArgs(Arrays.asList(subarray), count - 1, buf);
        if (Main.serverInterface.sendMulticast(null, array[0], buf.toString(), ProtocolConstants.CL_MESSAGE, 1, null) == 0) {
            uprintf("client '%s' unknown.\r\n", array[0]);
        }
    }

    private void execClients(String[] array, int count) {
        Client temp = null;
        if (count > 0) {
            Client match = null;
            for (Client client : Client.clients) {
                temp = client;
                if (temp.getCallsign().contains(array[0]) || temp.getCid().contains(array[0])) {
                    if (match != null) {
                        break;
                    }
                    match = temp;
                }
            }
            if (temp == null) {
                uprintf("No match.\r\n");
                return;
            }
        }
        uprintf("Call-sign    CID     Online   Idle     Type  Server     Frequency\r\n");
        uprintf("-----------------------------------------------------------------\r\n");
        for (Client client : Client.clients) {
            temp = client;
            if (count == 0 || temp.getCallsign().contains(array[0]) || temp.getCid().contains(array[0])) {
                uprintf("%-10s   %-7s %s %s %-5s %-10s", temp.getCallsign(), temp.getCid(),
                        Support.sprintTime(Support.mtime() - temp.getStartTime()),
                        Support.sprintTime(Support.mtime() - temp.getAlive()),
                        temp.getType() == ClientConstants.CLIENT_ATC ? "ATC" : "PILOT", temp.getLocation().getIdent(),
                        temp.getFrequency());
                if (temp.getFrequency() != 0 && temp.getFrequency() < 100000)
                    uprintf(" 1%02d.%03d", temp.getFrequency() / 1000, temp.getFrequency() % 1000);
                uprintf("%s\r\n", "");
            }
        }
    }

    private void execCert(String[] array, int count) {
        if (count < 2) {
            uprintf("CID        Max level      Origin     Creation (UTC)       Last used (local)\r\n");
            uprintf("---------------------------------------------------------------------------\r\n");
            for (Certificate c : Certificate.certs) {
                if (count == 0 || c.getCid().contains(array[0])) {
                    uprintf("%-10s %-13s  %-10s %s  %s\r\n", c.getCid(), CertificateConstants.CERT_LEVEL[c.getLevel()],
                            c.getOrigin(), Support.sprintDate(c.getCreation()), Support.sprintDate(c.getPrevVisit()));
                    return;
                }
            }
        }
        int mode;
        if ("add".equalsIgnoreCase(array[0])) {
            mode = ProtocolConstants.CERT_ADD;
        } else if ("delete".equalsIgnoreCase(array[0])) {
            mode = ProtocolConstants.CERT_DELETE;
        } else if ("modify".equalsIgnoreCase(array[0])) {
            mode = ProtocolConstants.CERT_MODIFY;
        } else {
            uprintf("Invalid mode, should be 'add', 'delete' or 'modify'.\r\n");
            return;
        }

        int level, ok = 0;
        Certificate c = Certificate.getCert(array[1]);
        switch (mode) {
            case ProtocolConstants.CERT_ADD -> {
                if (count < 4) {
                    usage(SystemConstants.SYS_CERT, "add <cid> <pwd> <level>");
                    return;
                }
                if (c != null) {
                    uprintf("Certificate already exists.\r\n");
                    return;
                }
                for (level = 0; level <= GlobalConstants.LEV_MAX; level++)
                    if (array[3].equalsIgnoreCase(CertificateConstants.CERT_LEVEL[level])) {
                        ok = 1;
                        break;
                    }
                if (ok == 0) {
                    uprintf("Unknown level, use 'help cert' for help.\r\n");
                    return;
                }
                c = new Certificate(array[1], array[2], level, Support.mgmtime(), Server.myServer.getIdent());
                Main.serverInterface.sendCert("*", mode, c, null);
            }
            case ProtocolConstants.CERT_DELETE -> {
                if (c == null) {
                    uprintf("Certificate does not exist.\r\n");
                    return;
                }
                Main.serverInterface.sendCert("*", mode, c, null);
                c.close();
            }
            case ProtocolConstants.CERT_MODIFY -> {
                if (c == null) {
                    uprintf("Certificate does not exist.\r\n");
                    return;
                }
                if (count < 3) {
                    usage(SystemConstants.SYS_CERT, "modify <cid> <level>");
                    return;
                }
                for (level = 0; level <= GlobalConstants.LEV_MAX; level++)
                    if (array[2].equalsIgnoreCase(CertificateConstants.CERT_LEVEL[level])) {
                        ok = 1;
                        break;
                    }
                if (ok == 0) {
                    uprintf("Unknown level, use 'help cert' for help.\r\n");
                    return;
                }
                c.configure(null, level, Support.mgmtime(), Server.myServer.getIdent());
                Main.serverInterface.sendCert("*", mode, c, null);
            }
        }
        uprintf("Your change has been executed, but the certificate file\r\n");
        uprintf("has not been updated. Please update this file if your change\r\n");
        uprintf("should be permanent.\r\n");
    }

    private void execDistance(String[] array, int count) {
        if (count < 2) {
            usage(SystemConstants.SYS_DISTANCE, "<client1> <client2>");
            return;
        }
        Client c1 = Client.getClient(array[0]);
        if (c1 == null) {
            uprintf("client '%s' not found.\r\n", array[0]);
            return;
        }
        Client c2 = Client.getClient(array[1]);
        if (c2 == null) {
            uprintf("client '%s' not found.\r\n", array[1]);
            return;
        }
        if (c1.getPositionOk() == 0) {
            uprintf("client '%s' did not send a position report.\r\n", array[0]);
            return;
        }
        if (c2.getPositionOk() == 0) {
            uprintf("client '%s' did not send a position report.\r\n", array[1]);
            return;
        }
        uprintf("%f NM.\r\n", c1.distance(c2));
    }

    private void execTime(String[] array, int count) {
        long now = Support.mtime();
        uprintf("%s local", DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(now)));
        long gmtNow = Support.mgmtime();
        uprintf("%s UTC", DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(gmtNow).atZone(ZoneId.of("UTC"))));
    }

    private void execWeather(String[] array, int count) {
        if (count < 1) {
            usage(SystemConstants.SYS_WEATHER, "<profile>");
            return;
        }
        Server s = MetarManage.metarManager.requestMetar(Server.myServer.getIdent(), array[0], 1, fd);
        if (s != null) {
            uprintf("Weather request sent to METAR host %s.\n\r", s.getIdent());
        } else {
            uprintf("No METAR host in network.\r\n");
        }
    }

    void execRange(String[] array, int count) {
        if (count < 1) {
            usage(SystemConstants.SYS_RANGE, "<callsign>");
            return;
        }
        Client temp = Client.getClient(array[0]);
        if (temp == null) {
            uprintf("No such callsign.\n\r");
            return;
        }
        uprintf("Range : %d NM.\r\n", temp.getRange());
    }

    void execLog(String[] array, int count) {
        // TODO
//        if (count<2)
//        {
//            usage(SystemConstants.SYS_LOG,"(show|delete) <maxlevel> [amount]\r\n  Where level "
//                    "is 0=Emergency, 1=Alert, 2=Critical, 3=Error, 4=Warning, 5=Info\r\n"
//                    "  6=Debug");
//            return;
//        }
//        int mode, maxlevel=NumberUtils.toInt(array[1]), amount;
//        if (count>2) amount=NumberUtils.toInt(array[2]); else amount=MAXLOG;
//        if (amount>MAXLOG) amount=MAXLOG;
//        if (maxlevel>=L_MAX) maxlevel=L_MAX-1;
//        if (!STRCASECMP(array[0],"show")) mode=1; else
//        if (!STRCASECMP(array[0],"delete")) mode=2; else
//        {
//            uprintf("Invalid operation mode, use 'show' or 'delete'.\r\n");
//            return;
//        }
//        int msgp=logp;
//        while (amount)
//        {
//            if (--msgp<0) msgp+=MAXLOG;
//            if (msgp==logp) break;
//            if (loghistory[msgp].msg==NULL) break;
//            if (loghistory[msgp].level>maxlevel) continue;
//            if (mode==1) uprintf("%s\r\n", loghistory[msgp].msg); else
//            {
//                free(loghistory[msgp].msg);
//                loghistory[msgp].msg=NULL;
//            }
//            amount--;
//        }
    }

    void execPwd(String[] array, int count) {
        if (count < 1) {
            usage(SystemConstants.SYS_PWD, "<password>");
            return;
        }
        ConfigGroup sgroup = Main.configManager.getGroup("system");
        ConfigEntry sentry = null;
        if (sgroup != null) {
            sentry = sgroup.getEntry("password");
        }
        String buf = sentry != null ? sentry.getData() : null;
        if (buf != null && buf.equals(array[0]))
            authorized = 1;
        if (authorized == 0)
            uprintf("Password incorrect.\r\n");
        else uprintf("Password correct.\r\n");
    }

    void execWall(String[] array, int count) {
        if (count < 1) {
            usage(SystemConstants.SYS_PWD, "<text>");
            return;
        }
        String buf = Support.catArgs(Arrays.asList(array), count, new StringBuilder());
        for (AbstractUser temp : Main.clientInterface.getUsers()) {
            ClientUser ctemp = (ClientUser) temp;
            Client c = ctemp.getThisClient();
            if (c == null) {
                continue;
            }
            Main.clientInterface.sendGeneric(c.getCallsign(), c, null, null, "server", buf, ProtocolConstants.CL_MESSAGE);
        }
    }

    void execDelGuard(String[] array, int count) {
        Main.serverInterface.delGuard();
        uprintf("Guard connections deleted.\r\n");
    }

    void execMetar(String[] array, int count) {
        if (count < 1) {
            usage(SystemConstants.SYS_METAR, "<profile>");
            return;
        }
        Server s = MetarManage.metarManager.requestMetar(Server.myServer.getIdent(), array[0], 0, fd);
        if (s != null) {
            uprintf("Metar request sent to METAR host %s.\n\r", s.getIdent());
        } else {
            uprintf("No METAR host in network.\r\n");
        }
    }

    void execWp(String @NotNull [] array, int count) {
        String mode = array[0];
        String help = "<show|create|delete|activate|set> <name> ...";
        if (count == 0) {
            usage(SystemConstants.SYS_WP, help);
            return;
        }
        if (mode.equals("show")) {
            if (count == 1) {
                uprintf("Active  Name  Origin     Creation\r\n");
                uprintf("---------------------------------\r\n");
                for (WProfile wp : Weather.wProfiles)
                    uprintf("%-6s  %-4s  %-10s %s\r\n", wp.getActive() == 1 ? "Yes" : "No", wp.getName(),
                            wp.getOrigin() != null ? wp.getOrigin() : "<local>", Support.sprintDate(wp.getCreation()));
            } else {
                WProfile wp = Weather.getWProfile(array[1]);
                if (wp == null) {
                    uprintf("Could not find weather profile '%s'\r\n", array[1]);
                    return;
                } else {
                    printWeather(wp);
                }
            }
            return;
        }
        if (count < 2) {
            usage(SystemConstants.SYS_WP, help);
            return;
        }
        array[1] = array[1].toUpperCase();
        WProfile wp = Weather.getWProfile(array[1]);
        if (mode.equalsIgnoreCase("create")) {
            if (wp != null) {
                uprintf("Weather profile already exists!\r\n");
                return;
            }
            Server s = MetarManage.metarManager.requestMetar(Server.myServer.getIdent(), array[1], 1, -2);
            if (s != null) {
                uprintf("A METAR request has been sent out to server %s to get the initial data.\r\n", s.getIdent());
                uprintf("The weather profile will created when the data arrives.\r\n");
            } else {
                new WProfile(array[1], Support.mgmtime(), Server.myServer.getIdent());
                uprintf("No METAR host in network.\r\n");
                uprintf("The weather profile has been created without default data.\r\n");
            }
            return;
        }
        if (wp == null) {
            uprintf("Could not find weather profile '%s'\r\n", array[1]);
            return;
        }
        if (mode.equalsIgnoreCase("delete")) {
            if (wp.getActive() == 1)
                Main.serverInterface.sendDelWp(wp);
            wp.close();
            uprintf("Weather profile deleted.\r\n");
        } else if (mode.equalsIgnoreCase("activate")) {
            wp.activate();
            wp.genRawCode();
            Main.serverInterface.sendAddWp(null, wp);
            uprintf("Weather profile now in effect!\r\n");
        } else if (mode.equalsIgnoreCase("set")) {
            String p = array[2], v = array[3];
            Scanner scanner = new Scanner(v);
            if (!scanner.hasNextInt()) {
                uprintf("Invalid value '%s'\r\n", v);
            } else {
                int val = scanner.nextInt();
                doSet(wp, p, val);
            }
        } else {
            uprintf("Invalid wp mode.\r\n");
        }
    }

    void execKill(String @NotNull [] array, int count) {
        if (count < 2) {
            usage(SystemConstants.SYS_KILL, "<callsign> <reason>");
            return;
        }
        Client c = Client.getClient(array[0]);
        if (c == null) {
            uprintf("Could not find client '%s'\r\n", array[0]);
            return;
        }
        String[] subarray = ArrayUtils.subarray(array, 1, array.length);
        String buf = Support.catArgs(Arrays.asList(subarray), count - 1, new StringBuilder());
        Main.serverInterface.sendKill(c, buf);
        uprintf("Killed\r\n");
    }

    void execPos(String[] array, int count) {
        if (count < 2) {
            usage(SystemConstants.SYS_POS, "<lat> <lon");
            return;
        }
        lat = NumberUtils.toDouble(array[0]);
        lon = NumberUtils.toDouble(array[1]);
        uprintf("Position set\r\n");
    }

    void execDump(String[] array, int count) {
        if (count < 2) {
            usage(SystemConstants.SYS_DUMP, "<fd> <file>");
            return;
        }
        int num = NumberUtils.toInt(array[0]);
        for (AbstractUser temp : Main.serverInterface.getUsers())
            if (temp.fd == num) {
                try (FileOutputStream output = new FileOutputStream(array[1])) {
                    output.write(temp.outBuf.array());
                } catch (IOException e) {
                    uprintf("%s: %s", array[1], e.getMessage());
                    return;
                }
                uprintf("Done\r\n");
                return;
            }
        uprintf("Can't find fd\r\n");
    }

    void execRefMetar(String[] array, int count) {
        if (MetarManage.metarManager.getSource() == MetarSource.SOURCE_NETWORK) {
            uprintf("Servers doesn't use a local METAR file.\r\n");
            return;
        }
        if (MetarManage.metarManager.isDownloading()) {
            uprintf("Already downloading latest METAR information.\r\n");
            return;
        }
        uprintf("Starting download of metar data.\r\n");
        MetarManage.metarManager.startDownload();
    }

    public void printWeather(@NotNull WProfile wp) {
        uprintf("\r\nWeather profile %s:\r\n\r\n", wp.getName());
        int x;
        StringBuilder line;
        String buf;

        line = new StringBuilder(String.format("%-20s%-10s%-10s", "Cloud layer", "1", "2"));
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Floor"));
        for (x = 0; x < 2; x++) {
            buf = String.format("%-10d", wp.getClouds().get(x).getFloor());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Ceiling"));
        for (x = 0; x < 2; x++) {
            buf = String.format("%-10d", wp.getClouds().get(x).getCeiling());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Coverage"));
        for (x = 0; x < 2; x++) {
            buf = String.format("%-10d", wp.getClouds().get(x).getCoverage());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Turbulence"));
        for (x = 0; x < 2; x++) {
            buf = String.format("%-10d", wp.getClouds().get(x).getTurbulence());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Icing"));
        for (x = 0; x < 2; x++) {
            buf = String.format("%-10s", wp.getClouds().get(x).getIcing() == 1 ? "Yes" : "No");
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());


        line = new StringBuilder(String.format("%-20s%-10s%-10s%-10s%-10s", "Wind layer", "1", "2", "3", "4"));
        uprintf("\r\n%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Floor"));
        for (x = 0; x < 4; x++) {
            buf = String.format("%-10d", wp.getWinds().get(x).getFloor());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Ceiling"));
        for (x = 0; x < 4; x++) {
            buf = String.format("%-10d", wp.getWinds().get(x).getCeiling());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Direction"));
        for (x = 0; x < 4; x++) {
            buf = String.format("%-10d", wp.getWinds().get(x).getDirection());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Speed"));
        for (x = 0; x < 4; x++) {
            buf = String.format("%-10d", wp.getWinds().get(x).getSpeed());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Gusting"));
        for (x = 0; x < 4; x++) {
            buf = String.format("%-10s", wp.getWinds().get(x).getGusting() > 0 ? "Yes" : "No");
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());

        line = new StringBuilder(String.format("%-20s%-10s%-10s%-10s%-10s", "Temp layer", "1", "2", "3", "4"));
        uprintf("\r\n%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Ceiling"));
        for (x = 0; x < 4; x++) {
            buf = String.format("%-10d", wp.getTemps().get(x).getCeiling());
            line.append(buf);
        }
        uprintf("%s\r\n", line.toString());
        line = new StringBuilder(String.format("%-20s", "  Temperature"));
        for (x = 0; x < 4; x++) {
            buf = String.format("%-10d", wp.getTemps().get(x).getTemp());
            line.append(buf);
        }
        uprintf("%s\r\n\r\n", line.toString());

        uprintf("Visibility  : %.2f SM\r\n", wp.getVisibility());
        uprintf("Barometer   : %d Hg\r\n", wp.getBarometer());
        printPrompt();
    }

    public void printMetar(String wp, String data) {
        uprintf("\r\nWeather profile %s:\r\n\r\n", wp);
        uprintf("%s\r\n", data);
        printPrompt();
    }

    void doSet(@NotNull WProfile wp, @NotNull String p, int val) {
        int x, ok;
        String[] cat1 = {
                "winds", "clouds", "temperature", "pressure", "visibility"
        };
        String where = null, more = null;
        int find = p.indexOf('.');
        if (find != -1) {
            where = p.substring(find + 1);
        }
        ok = 0;
        for (x = 0; x < 5; x++)
            if (p.equalsIgnoreCase(cat1[x])) {
                ok = 1;
                break;
            }
        if (ok == 0) {
            uprintf("Invalid category '%s'.\r\n", p);
            return;
        }
        switch (x) {
            case 3 -> {
                wp.setBarometer(val);
                return;
            }
            case 4 -> {
                wp.setVisibility(val);
                return;
            }
        }
        if (StringUtils.isEmpty(where)) {
            uprintf("Invalid parameter\r\n");
            return;
        }
        String[] split = where.split("\\.");
        if (split.length > 1) {
            more = split[1];
        }
        Scanner scanner = new Scanner(where);
        if (!scanner.hasNextInt()) {
            uprintf("Invalid index '%s'\r\n", where);
            return;
        }
        int index = scanner.nextInt();
        index--;
        if (index < 0 || (x == 1 && index > 2) || index > 4) {
            uprintf("Invalid index %d\r\n", index);
            return;
        }
        if (StringUtils.isEmpty(more)) {
            uprintf("Invalid parameter\r\n");
            return;
        }
        where = more;
        String[] strings = new String[10];
        switch (x) {
            case 0 -> {
                strings[0] = "floor";
                strings[1] = "ceiling";
                strings[2] = "direction";
                strings[3] = "speed";
                strings[4] = "gusting";
                strings[5] = null;
            }
            case 1 -> {
                strings[0] = "floor";
                strings[1] = "ceiling";
                strings[2] = "coverage";
                strings[3] = "turbulence";
                strings[4] = "icing";
                strings[5] = null;
            }
            case 2 -> {
                strings[0] = "ceiling";
                strings[1] = "temperature";
                strings[2] = null;
            }
        }
        int y;
        ok = 0;
        for (y = 0; strings[y] != null; y++)
            if (where.equalsIgnoreCase(strings[y])) {
                ok = 1;
                break;
            }
        if (ok == 0) {
            uprintf("Invalid specifier '%s'\r\n", where);
            return;
        }
        switch (x) {
            case 0:
                switch (y) {
                    case 0 -> wp.getWinds().get(index).setFloor(val);
                    case 1 -> wp.getWinds().get(index).setCeiling(val);
                    case 2 -> wp.getWinds().get(index).setDirection(val);
                    case 3 -> wp.getWinds().get(index).setSpeed(val);
                    case 4 -> wp.getWinds().get(index).setGusting(val > 0 ? 1 : 0);
                }
                break;
            case 1:
                switch (y) {
                    case 0 -> wp.getClouds().get(index).setFloor(val);
                    case 1 -> wp.getClouds().get(index).setCeiling(val);
                    case 2 -> wp.getClouds().get(index).setCoverage(val);
                    case 3 -> wp.getClouds().get(index).setTurbulence(val);
                    case 4 -> wp.getClouds().get(index).setIcing(val > 0 ? 1 : 0);
                }
                break;
            case 2:
                switch (y) {
                    case 0 -> wp.getTemps().get(index).setCeiling(val);
                    case 1 -> wp.getTemps().get(index).setTemp(val);
                }
                break;
        }
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
}
