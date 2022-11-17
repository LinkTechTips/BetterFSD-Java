/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.process.network;

import org.linktechtips.model.Server;
import org.linktechtips.user.AbstractUser;
import org.linktechtips.user.SystemUser;
import org.linktechtips.support.Support;
import org.linktechtips.weather.WProfile;
import org.jetbrains.annotations.NotNull;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SystemInterface extends TcpInterface {
    public SystemInterface(int port, String code, String d) {
        super(port, code, d);

    }

    public boolean run() {
        return super.run();
    }

    public void newUser(SocketChannel fd, String peer, int portnum, int g) {
        insertUser(new SystemUser(fd, this, peer, portnum, g));
    }

    public void receivePong(String from, @NotNull String data, String pc, String hops) {
        int fd;
        long now;
        Scanner scanner = new Scanner(data);
        if (!scanner.hasNextInt()) {
            return;
        }
        fd = scanner.nextInt();
        if (!scanner.hasNextLong()) {
            return;
        }
        now = scanner.nextLong();
        if (fd == -1) {
            return;
        }
        for (AbstractUser temp : users) {
            if (temp.getFd() == fd) {
                temp.uprintf("\r\nPONG received from %s: %d seconds (%s,%s)\r\n",
                        from, Support.mtime() - now, pc, hops);
                temp.printPrompt();
                return;
            }
        }
    }

    public void receiveWeather(int fd, @NotNull WProfile w) {
        if (fd == -2) {
            String buffer = w.print();
            List<String> array = new ArrayList<>();
            int count = Support.breakPacket(buffer, array, 100);
            WProfile wp = new WProfile(w.getName(), Support.mgmtime(), Server.myServer.getIdent());
            wp.loadArray(array.toArray(new String[0]), count);
            return;
        }
        for (AbstractUser temp : users) {
            if (temp.getFd() == fd) {
                SystemUser st = (SystemUser) temp;
                w.fix(st.getLat(), st.getLon());
                st.printWeather(w);
                break;
            }
        }
    }

    public void receiveMetar(int fd, String wp, String w) {
        for (AbstractUser temp : users) {
            if (temp.getFd() == fd) {
                SystemUser st = (SystemUser) temp;
                st.printMetar(wp, w);
                break;
            }
        }
    }

    public void receiveNoWx(int fd, String st) {
        if (fd == -2) {
            WProfile wp = new WProfile(st, Support.mgmtime(), Server.myServer.getIdent());
        }
        for (AbstractUser temp : users) {
            if (temp.getFd() == fd) {
                temp.uprintf("\r\nNo weather available for %s.\r\n", st);
                temp.printPrompt();
                break;
            }
        }
    }
}
