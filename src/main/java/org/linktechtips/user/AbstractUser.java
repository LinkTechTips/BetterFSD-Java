/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.user;

import org.linktechtips.constants.GlobalConstants;
import org.linktechtips.constants.NetworkConstants;
import org.linktechtips.manager.Manage;
import org.linktechtips.process.network.TcpInterface;
import org.linktechtips.support.Support;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

public class AbstractUser {
    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractUser.class);

    private static int fdCount = 0;

    protected final int fd;

    protected final SocketChannel socketChannel;

    protected int killFlag, inSize, outSize, feed, feedCount, guardFlag;

    protected int outBufSoftLimit;

    protected long lastActive, lastPing, prevFeedCheck;

    protected int timeOut, blocked;

    protected ByteBuffer inBuf, outBuf;

    protected String peer;

    protected int port;

    protected String prompt;

    protected final TcpInterface baseParent;

    public AbstractUser(SocketChannel d, TcpInterface p, String peerName, int portNum, int g) {
        killFlag = 0;
        socketChannel = d;
        fd = ++fdCount;
        inBuf = ByteBuffer.allocate(1);
        outBuf = ByteBuffer.allocate(1);
        inSize = outSize = 1;
        blocked = 0;
        feedCount = 0;
        feed = -1;
        baseParent = p;
        timeOut = 0;
        peer = peerName;
        port = portNum;
        prevFeedCheck = 0;
        guardFlag = g;
        outBufSoftLimit = -1;
        setActive();
    }

    public void close() {
        LOGGER.info(String.format("[User/AbstractUser]: client from %s removed from %s:%s", peer, baseParent.getDescription(), NetworkConstants.KILL_REASONS[killFlag]));
        Manage.manager.incVar(baseParent.getVarClosed()[killFlag]);
        if (killFlag != NetworkConstants.KILL_CLOSED && killFlag != NetworkConstants.KILL_WRITE_ERR) {
            output();
        }
        if (killFlag != NetworkConstants.KILL_COMMAND && guardFlag > 0) {
            baseParent.addGuard(this);
        }
        try {
            socketChannel.close();
        } catch (IOException e) {
            LOGGER.warn(String.format("[User/AbstractUser]: Close client %s failed.", peer), e);
        }
    }

    public void setActive() {
        lastActive = lastPing = Support.mtime();
    }

    public void setMasks(@NotNull Set<SelectableChannel> rmask, @NotNull Set<SelectableChannel> wmask) {
        rmask.add(socketChannel);
        if (outBuf.position() > 0) {
            wmask.add(socketChannel);
        }
    }

    public void output() {
        int bytes;
        try {
            bytes = socketChannel.write(outBuf);
            byte[] data = new byte[bytes];
            System.arraycopy(outBuf.array(), 0, data, 0, bytes);
            LOGGER.info("[User/AbstractUser]: Send: " + new String(data));
            outBuf.clear();
        } catch (IOException e) {
            kill(NetworkConstants.KILL_WRITE_ERR);
            return;
        }

        if ((baseParent.getFeedStrategy() & NetworkConstants.FEED_OUT) > 0) {
            feedCount += bytes;
        }
    }

    public void input() {
        int bytes;
        ByteBuffer buf;
        try {
            buf = ByteBuffer.allocate(1024);
            bytes = socketChannel.read(buf);
            if (bytes == -1) {
                kill(NetworkConstants.KILL_CLOSED);
                return;
            }
        } catch (IOException e) {
            kill(NetworkConstants.KILL_CLOSED);
            return;
        }

        if ((baseParent.getFeedStrategy() & NetworkConstants.FEED_IN) > 0) {
            feedCount += bytes;
        }

        int inBufBytes = inBuf.position();

        if (inSize > 4096 && inBufBytes == 0) {
            inSize = 2048;
            inBuf.clear();
        }

        buf.flip();

        if (bytes + inBufBytes + 1 > inSize) {
            inSize = bytes + inBufBytes + 1000;
            ByteBuffer newInBuf = ByteBuffer.allocate(inSize);
            if (inBufBytes > 0) {
                inBuf.flip();
                newInBuf.put(inBuf);
                inBuf.compact();
            }
            newInBuf.put(buf);
            inBuf = newInBuf;
        } else {
            inBuf.put(buf);
        }
        buf.compact();
    }

    private int nextLine(@NotNull StringBuilder dest) {
        if (!inBuf.hasArray()) {
            return -1;
        }
        byte[] data = new byte[inBuf.position()];
        System.arraycopy(inBuf.array(), 0, data, 0, inBuf.position());
        String src = new String(data);

        int len = src.indexOf("\r\n");
        if (len == -1) {
            len = src.indexOf("\n");
        }
        if (len == -1) {
            return -1;
        }

        if (len < GlobalConstants.MAX_LINE_LENGTH) {
            dest.append(src, 0, len);
        }

        ByteBuffer newInBuf = ByteBuffer.allocate(inSize);
        if (src.length() > len + 2) {
            newInBuf.put(src.substring(len + 2).getBytes());
        }
        inBuf = newInBuf;

        if (len >= GlobalConstants.MAX_LINE_LENGTH) {
            return -1;
        }
        return len;
    }

    public void calcFeed() {
        long now = Support.mtime();
        long elapsed = now - prevFeedCheck;
        double fact1 = elapsed / 300.0, fact2 = 1.0 - fact1;
        int newFeed = (int) (feedCount / elapsed);
        if (feed == -1) {
            feed = newFeed;
        } else {
            feed = (int) (newFeed * fact1 + feed * fact2);
        }
        feedCount = 0;
        prevFeedCheck = now;
        int bandWidth = feed;
        if (baseParent.getFeedStrategy() == NetworkConstants.FEED_BOTH) {
            bandWidth /= 2;
        }
        if (bandWidth > 50) {
            outBufSoftLimit = bandWidth * 30;
        }
    }

    private void send(@NotNull String buf) {
        byte[] bufBytes = buf.getBytes();

        int outBufBytes = outBuf.position();
        int bytes = bufBytes.length;

        if (outBufBytes > 0) {
            if (outBufBytes + bytes + 1 > outSize) {
                outSize = outBufBytes + bytes + 1000;
                ByteBuffer newOutBuf = ByteBuffer.allocate(outSize);
                outBuf.flip();
                newOutBuf.put(outBuf);
                outBuf.compact();
                newOutBuf.put(bufBytes);
                outBuf = newOutBuf;
            } else {
                outBuf.put(bufBytes);
            }
        } else {
            int sendBytes;
            try {
                ByteBuffer tmpBuf = ByteBuffer.wrap(bufBytes);
                sendBytes = socketChannel.write(tmpBuf);
            } catch (IOException e) {
                kill(NetworkConstants.KILL_WRITE_ERR);
                return;
            }

            if ((baseParent.getFeedStrategy() & NetworkConstants.FEED_OUT) > 0) {
                feedCount += sendBytes;
            }
        }

    }

    public void uprintf(@NotNull String format, Object... args) {
        if (killFlag != 0) {
            return;
        }

        String buf = String.format(format, args);
        send(buf);
    }

    public void uslprintf(@NotNull String format, int limit, Object... args) {
        if (killFlag != 0) {
            return;
        }

        String buf = String.format(format, args);
        if (limit > 0 && outBufSoftLimit != -1 && (buf.length() + outBuf.position()) > outBufSoftLimit) {
            return;
        }
        send(buf);
    }

    public int run() {
        int count = 0, stat, ok = 0;
        if (blocked == 1) {
            return 0;
        }
        StringBuilder bufBuilder = new StringBuilder();
        while ((stat = nextLine(bufBuilder)) != -1 && (++count < 60)) {
            if (baseParent.getFloodLimit() != -1 && feed > baseParent.getFloodLimit()) {
                kill(NetworkConstants.KILL_FLOOD);
            }
            parse(bufBuilder.toString());
            ok = 1;
            if (blocked == 1) {
                break;
            }
            bufBuilder.delete(0, bufBuilder.length());
        }

        if (ok == 1 && stat == -1) {
            printPrompt();
        }

        return stat == -1 ? 0 : 1;
    }

    public void parse(String s) {

    }

    public void block() {
        blocked = 1;
    }

    public void unblock() {
        blocked = 0;
        printPrompt();
    }

    public void printPrompt() {
        if (prompt == null || killFlag > 0 || blocked > 0) {
            return;
        }

        uprintf("%s", prompt);
    }

    public void kill(int reason) {
        killFlag = reason;
    }

    public void sendPing() {

    }

    public int getFd() {
        return fd;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public int getKillFlag() {
        return killFlag;
    }

    public void setKillFlag(int killFlag) {
        this.killFlag = killFlag;
    }

    public int getInSize() {
        return inSize;
    }

    public void setInSize(int inSize) {
        this.inSize = inSize;
    }

    public int getOutSize() {
        return outSize;
    }

    public void setOutSize(int outSize) {
        this.outSize = outSize;
    }

    public int getFeed() {
        return feed;
    }

    public void setFeed(int feed) {
        this.feed = feed;
    }

    public int getFeedCount() {
        return feedCount;
    }

    public void setFeedCount(int feedCount) {
        this.feedCount = feedCount;
    }

    public int getGuardFlag() {
        return guardFlag;
    }

    public void setGuardFlag(int guardFlag) {
        this.guardFlag = guardFlag;
    }

    public int getOutBufSoftLimit() {
        return outBufSoftLimit;
    }

    public void setOutBufSoftLimit(int outBufSoftLimit) {
        this.outBufSoftLimit = outBufSoftLimit;
    }

    public long getLastActive() {
        return lastActive;
    }

    public void setLastActive(long lastActive) {
        this.lastActive = lastActive;
    }

    public long getLastPing() {
        return lastPing;
    }

    public void setLastPing(long lastPing) {
        this.lastPing = lastPing;
    }

    public long getPrevFeedCheck() {
        return prevFeedCheck;
    }

    public void setPrevFeedCheck(long prevFeedCheck) {
        this.prevFeedCheck = prevFeedCheck;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }

    public int getBlocked() {
        return blocked;
    }

    public void setBlocked(int blocked) {
        this.blocked = blocked;
    }

    public String getPeer() {
        return peer;
    }

    public void setPeer(String peer) {
        this.peer = peer;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
