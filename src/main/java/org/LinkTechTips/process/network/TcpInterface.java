/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.process.network;

import org.LinkTechTips.constants.GlobalConstants;
import org.LinkTechTips.constants.ManageVarType;
import org.LinkTechTips.constants.NetworkConstants;
import org.LinkTechTips.manager.Manage;
import org.LinkTechTips.process.Process;
import org.LinkTechTips.support.Support;
import org.LinkTechTips.user.AbstractUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

import static java.net.StandardSocketOptions.TCP_NODELAY;

public class TcpInterface extends Process {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpInterface.class);

    protected String description;

    protected ServerSocketChannel sock;

    private Selector selector;

    protected SelectionKey selectionKey;

    protected int varCurrent;

    protected int varTotal;

    protected int varPeak;

    protected int[] varClosed;

    protected int feedStrategy;

    protected int floodLimit;

    protected int outBufLimit;

    protected long prevChecks;

    protected @Nullable String prompt;

    protected List<AbstractUser> users;

    protected List<Guard> guards;

    protected List<Allow> allows;

    public TcpInterface(int port, String code, String desc) {
        super();
        boolean on = true;
        description = desc;
        allows = new ArrayList<>();
        varClosed = new int[9];
        floodLimit = -1;
        outBufLimit = -1;
        makeVars(code);
        try {
            selector = Selector.open();
            sock = ServerSocketChannel.open();
        } catch (IOException e) {
            LOGGER.error("[Network/TcpInterface]: Could not open server socket channel.", e);
            System.exit(1);
        }
        try {
            sock.setOption(TCP_NODELAY, Boolean.TRUE);
        } catch (IOException e) {
            LOGGER.error(String.format("[Network/TcpInterface]: Could not set TCP_NODELAY on port %d", port), e);
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("[Network/TcpInterface]: TCP_NODELAY is not supported on current platform");
        }
        try {
            sock.setOption(StandardSocketOptions.SO_REUSEADDR, on);
        } catch (IOException e) {
            LOGGER.error(String.format("[Network/TcpInterface]: setsockopt error on port %d", port), e);
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("[Network/TcpInterface]: SO_REUSEADDR is not supported on current platform");
        }

        try {
            sock.configureBlocking(false);
            sock.bind(new InetSocketAddress(port));
            sock.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            LOGGER.error(String.format("[Network/TcpInterface]: Bind error on port %d", port), e);
            System.exit(1);
        }

        users = new ArrayList<>();
        guards = new ArrayList<>();
        prompt = null;
        feedStrategy = 0;
        prevChecks = 0;
        LOGGER.info(String.format("[Network/TcpInterface]: Booting port %d (%s)", port, description));
    }

    public void close() {
        try {
            sock.close();
        } catch (IOException e) {
            LOGGER.warn("[Network/TcpInterface]: Close tcpInterface error.");
        }
    }

    public void makeVars(String code) {
        String varName;
        varName = String.format("interface.%s.current", code);
        varCurrent = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.total", code);
        varTotal = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.peak", code);
        varPeak = Manage.manager.addVar(varName, ManageVarType.ATT_INT);

        varName = String.format("interface.%s.command", code);
        varClosed[1] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.flood", code);
        varClosed[2] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.initialtimeout", code);
        varClosed[3] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.stalled", code);
        varClosed[4] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.closed", code);
        varClosed[5] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.writeerr", code);
        varClosed[6] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.killed", code);
        varClosed[7] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
        varName = String.format("interface.%s.protocol", code);
        varClosed[8] = Manage.manager.addVar(varName, ManageVarType.ATT_INT);
    }

    public void addGuard(@NotNull AbstractUser who) {
        Guard guard = new Guard();
        guard.setHost(who.getPeer());
        guard.setPort(who.getPort());
        guard.setPrevTry(Support.mtime());
    }

    public void allow(String name) {
        try {
            InetAddress address = InetAddress.getByName(name);
            Allow allow = new Allow();
            allow.setIp(address.getHostAddress());
            allows.add(allow);
        } catch (UnknownHostException e) {
            LOGGER.error(String.format("[Network/TcpInterface]: Server %s unknown in allowfrom", name));
        }
    }

    public void newUser(@NotNull SelectionKey key) {
        String peer;
        InetSocketAddress remoteAddress;
        int ok = 0;
        SocketChannel clientChannel;
        try {
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            clientChannel = serverChannel.accept();
            if (clientChannel == null) {
                return;
            }
            clientChannel.configureBlocking(false);
            remoteAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
            for (Allow allow : allows) {
                if (allow.getIp().equals(remoteAddress.getAddress().getHostAddress())) {
                    ok = 1;
                    break;
                }
            }
            peer = Support.findHostname(remoteAddress.getAddress().getHostAddress());
            if (ok == 0 && !allows.isEmpty()) {
                String message = "#You are not allowed on this port.\r\n";
                clientChannel.write(ByteBuffer.wrap(message.getBytes()));
                LOGGER.info(String.format("[Network/TcpInterface]: Connection rejected from %s", peer));
                clientChannel.close();
                return;
            }
        } catch (IOException e) {
            LOGGER.error("[Network/TcpInterface]: Accept connection failed.", e);
            return;
        }

        LOGGER.info(String.format("[Network/TcpInterface]: Connection accepted from %s on %s", peer, description));

        try {
            clientChannel.setOption(TCP_NODELAY, true);
        } catch (IOException e) {
            LOGGER.error("[Network/TcpInterface]: Could not set off TCP_NODELAY");
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("[Network/TcpInterface]: TCP_NODELAY is not supported on current platform");
        }

        newUser(clientChannel, peer, remoteAddress.getPort(), 0);
    }

    public void newUser(SocketChannel client, String peerName, int port, int g) {

    }

    public void insertUser(@NotNull AbstractUser user) {
        try {
            user.getSocketChannel().register(selector, SelectionKey.OP_READ, user);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }
        users.add(user);
        if (prompt != null) {
            user.setPrompt(prompt);
            user.printPrompt();
        }
        Manage.manager.incVar(varTotal);
        Manage.manager.incVar(varCurrent);
        int peak = Objects.requireNonNull(Manage.manager.getVar(varPeak)).getValue().getNumber();
        int now = Objects.requireNonNull(Manage.manager.getVar(varCurrent)).getValue().getNumber();
        if (now > peak) {
            Manage.manager.setVar(varPeak, now);
        }
    }

    @Override
    public boolean run() {
        int busy = 0;
        long now = Support.mtime();
        long timeOut = 1000L;
        try {
            selector.select(timeOut);
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            for (Iterator<SelectionKey> iterator = selectionKeys.iterator(); iterator.hasNext(); ) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (!key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    newUser(key);
                }

                Object att = key.attachment();
                if (att == null) {
                    continue;
                }
                AbstractUser temp = (AbstractUser) att;
                if (temp.getKillFlag() != 0) {
                    users.remove(temp);
                    temp.close();
                    if (varCurrent != -1) {
                        Manage.manager.decVar(varCurrent);
                    }
                } else {
                    if (key.isWritable()) {
                        temp.output();
                    }
                    if (key.isReadable()) {
                        temp.input();
                    }
                    if (temp.run() > 0) {
                        busy = 1;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Network/TcpInterface]: run select failed", e);
        }

        for (Iterator<AbstractUser> iterator = users.iterator(); iterator.hasNext(); ) {
            AbstractUser temp = iterator.next();
            if (temp.getKillFlag() != 0) {
                iterator.remove();
                temp.close();
                if (varCurrent != -1) {
                    Manage.manager.decVar(varCurrent);
                }
            }
        }

        if (prevChecks != now) {
            doChecks();
        }
        return busy > 0;
    }

    private void doChecks() {
        long now = Support.mtime();
        prevChecks = now;
        for (AbstractUser temp : users) {
            if (temp.getKillFlag() == 0) {
                if (temp.getTimeOut() != 0) {
                    temp.kill(NetworkConstants.KILL_DATA_TIMEOUT);
                }
                if ((feedStrategy & ((now - temp.getPrevFeedCheck() > GlobalConstants.USER_FEED_CHECK) ? 1 : 0)) != 0) {
                    temp.calcFeed();
                }
                if (now - temp.getLastPing() >= GlobalConstants.USER_PING_TIMEOUT) {
                    temp.sendPing();
                    temp.setLastPing(now);
                } else if (now - temp.getLastActive() >= GlobalConstants.USER_TIMEOUT) {
                    temp.uprintf("# Timeout\r\n");
                    temp.setTimeOut(1);
                }
            }
        }
        for (Iterator<Guard> iterator = guards.iterator(); iterator.hasNext(); ) {
            Guard guard = iterator.next();
            long time = Support.mtime();
            if (time - guard.getPrevTry() > GlobalConstants.GUARD_RETRY) {
                guard.setPrevTry(time);
                if (addUser(guard.getHost(), guard.getPort(), null) != 0) {
                    iterator.remove();
                }
            }
        }
    }

    public int addUser(String name, int port, @Nullable AbstractUser terminal) {
        SocketChannel sock;
        for (AbstractUser user : users) {
            if (user.getPort() == port && user.getPeer().equals(name)) {
                if (terminal != null) {
                    terminal.uprintf("Already connected to %s\r\n", name);
                }
                return -1;
            }
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(name);
        } catch (UnknownHostException e) {
            if (terminal != null) {
                terminal.uprintf("Unknown hostname: %s\r\n", name);
            }
            return 0;
        }

        try {
            sock = SocketChannel.open();
        } catch (IOException e) {
            LOGGER.error("[Network/TcpInterface]: Could not open socket channel", e);
            return 0;
        }

        if (terminal != null) {
            terminal.uprintf("[Network/TcpInterface]: Connecting to %s port %d.\r\n", name, port);
        }

        int count = 0;
        Exception error = null;
        while (true) {
            if (++count == 3) {
                break;
            }
            try {
                sock.connect(new InetSocketAddress(address, port));
                sock.configureBlocking(false);
            } catch (ClosedByInterruptException e) {
                if (count < 3) {
                    continue;
                }
                error = e;
                break;
            } catch (Exception e) {
                error = e;
                break;
            }
        }
        if (error != null) {
            if (terminal != null) {
                terminal.uprintf("Could not connect to server: %s\r\n", error.getMessage());
            }
            try {
                sock.close();
            } catch (IOException e) {
                LOGGER.error("[Network/TcpInterface]: Close socket error.", e);
            }
        }
        if (terminal != null) {
            terminal.uprintf("Connection established.\r\n");
        }
        try {
            sock.setOption(TCP_NODELAY, true);
        } catch (IOException e) {
            LOGGER.error("[Network/TcpInterface]: Could not set off TCP_NODELAY");
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("[Network/TcpInterface]: TCP_NODELAY is not supported on current platform");
        }
        newUser(sock, name, port, 1);
        return 1;
    }

    public void delGuard() {
        guards.clear();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ServerSocketChannel getSock() {
        return sock;
    }

    public void setSock(ServerSocketChannel sock) {
        this.sock = sock;
    }

    public int getVarCurrent() {
        return varCurrent;
    }

    public void setVarCurrent(int varCurrent) {
        this.varCurrent = varCurrent;
    }

    public int getVarTotal() {
        return varTotal;
    }

    public void setVarTotal(int varTotal) {
        this.varTotal = varTotal;
    }

    public int getVarPeak() {
        return varPeak;
    }

    public void setVarPeak(int varPeak) {
        this.varPeak = varPeak;
    }

    public int[] getVarClosed() {
        return varClosed;
    }

    public void setVarClosed(int[] varClosed) {
        this.varClosed = varClosed;
    }

    public int getFeedStrategy() {
        return feedStrategy;
    }

    public void setFeedStrategy(int feedStrategy) {
        this.feedStrategy = feedStrategy;
    }

    public int getFloodLimit() {
        return floodLimit;
    }

    public void setFloodLimit(int floodLimit) {
        this.floodLimit = floodLimit;
    }

    public int getOutBufLimit() {
        return outBufLimit;
    }

    public void setOutBufLimit(int outBufLimit) {
        this.outBufLimit = outBufLimit;
    }

    public long getPrevChecks() {
        return prevChecks;
    }

    public void setPrevChecks(long prevChecks) {
        this.prevChecks = prevChecks;
    }

    public @Nullable String getPrompt() {
        return prompt;
    }

    public void setPrompt(@Nullable String prompt) {
        this.prompt = prompt;
    }

    public List<AbstractUser> getUsers() {
        return users;
    }

    public void setUsers(List<AbstractUser> users) {
        this.users = users;
    }

    public List<Guard> getGuards() {
        return guards;
    }

    public void setGuards(List<Guard> guards) {
        this.guards = guards;
    }

    public List<Allow> getAllows() {
        return allows;
    }

    public void setAllows(List<Allow> allows) {
        this.allows = allows;
    }
}
