/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.model;

import org.LinkTechTips.Main;
import org.LinkTechTips.user.AbstractUser;
import org.LinkTechTips.support.Support;
import org.LinkTechTips.user.ServerUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Server {
    public static Server myServer;

    public static final List<Server> servers = new ArrayList<>();

    private final static Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private int pCount;

    private int hops;

    private int check;

    private long lag;

    private int flags;

    private int packetDrops;

    private long alive;

    private String name;

    private String email;

    private String ident;

    private String hostName;

    private String version;

    private String location;

    private AbstractUser path;

    public Server(String ident, String name, String email, String hostName, String version, int flags, String location) {
        this.ident = ident;
        this.name = name;
        this.email = email;
        this.hostName = hostName;
        this.version = version;
        this.flags = flags;
        this.location = location;
        packetDrops = 0;
        hops = -1;
        pCount = -1;
        lag = -1;
        alive = Support.mtime();
    }

    public void configure(String name, String email, String hostName, String version, String location) {
        this.name = name;
        this.email = email;
        this.hostName = hostName;
        this.version = version;
        this.location = location;
    }

    public void close() {
        LOGGER.info(String.format("[BetterFSD/ServerInterface]: Dropping server %s(%s)", ident, name));
        for (Client client : Client.clients) {
            if (client.getLocation() == this) {
                client.close();
            }
        }

        List<AbstractUser> users = Main.serverInterface.getUsers();
        for (AbstractUser user : users) {
            if (((ServerUser) user).getThisServer() == this) {
                ((ServerUser) user).setThisServer(null);
            }
        }
    }

    public static @Nullable Server getServer(String ident) {
        for (Server server : servers) {
            if (server.getIdent().equals(ident)) {
                return server;
            }
        }
        return null;
    }

    public void setPath(AbstractUser who, int hops) {
        path = who;
        this.hops = hops;
        if (path == null) {
            pCount = -1;
        }
    }

    public void setAlive() {
        alive = Support.mtime();
    }

    public void receivePong(@NotNull String data) {
        Scanner scanner = new Scanner(data);
        if (scanner.hasNextInt()) {
            scanner.nextInt();
            if (scanner.hasNextLong()) {
                long t = scanner.nextLong();
                lag = Support.mtime() - t;
            }
        }
    }

    public void clearServerChecks() {
        servers.forEach(s -> s.setCheck(0));
    }

    public int getpCount() {
        return pCount;
    }

    public void setpCount(int pCount) {
        this.pCount = pCount;
    }

    public int getHops() {
        return hops;
    }

    public void setHops(int hops) {
        this.hops = hops;
    }

    public int getCheck() {
        return check;
    }

    public void setCheck(int check) {
        this.check = check;
    }

    public long getLag() {
        return lag;
    }

    public void setLag(long lag) {
        this.lag = lag;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getPacketDrops() {
        return packetDrops;
    }

    public void setPacketDrops(int packetDrops) {
        this.packetDrops = packetDrops;
    }

    public long getAlive() {
        return alive;
    }

    public void setAlive(long alive) {
        this.alive = alive;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdent() {
        return ident;
    }

    public void setIdent(String ident) {
        this.ident = ident;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public AbstractUser getPath() {
        return path;
    }

    public void setPath(AbstractUser path) {
        this.path = path;
    }
}
