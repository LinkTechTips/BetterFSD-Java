
/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.support;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Support {

    public static @NotNull String catCommand(@NotNull List<String> array, int n, @NotNull StringBuilder buf) {
        if (array.size() == 0) {
            return "";
        }
        for (int x = 0; x < n; x++) {
            if (x > 0) {
                buf.append(":");
            }
            buf.append(array.get(x));
        }
        return buf.toString();
    }

    public static @NotNull String catArgs(@NotNull List<String> array, int n, @NotNull StringBuilder buf) {
        for (int x = 0; x < n; x++) {
            if (x > 0) {
                buf.append(" ");
            }
            buf.append(array.get(x));
        }
        return buf.toString();
    }

    /* Takes <bytes> bytes from the packet s and puts it into buf */
    public static @NotNull String snapPacket(@NotNull String s, String buf, int bytes) {
        return buf + s.substring(0, bytes);
    }

    public static int breakPacket(@NotNull String s, @NotNull List<String> arr, int max) {
        String[] split = s.split(":", -1);
        for (String str : split) {
            arr.add(StringUtils.defaultString(str, ""));
            if (arr.size() == max) {
                return arr.size();
            }
        }

        return arr.size();
    }

    public static int breakArgs(@NotNull String s, @NotNull List<String> arr, int max) {
        String[] split = s.split(" ");
        for (String str : split) {
            str = StringUtils.strip(str);
            if (StringUtils.isNotBlank(str)) {
                arr.add(str);
            }
            if (arr.size() == max) {
                return arr.size();
            }
        }

        return arr.size();
    }

    public static String findHostname(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.getHostName();
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    public static double dist(double lat1, double lon1, double lat2, double lon2) {
        double dist, dlon = lon2 - lon1;
        lat1 *= Math.PI / 180.0;
        lat2 *= Math.PI / 180.0;
        dlon *= Math.PI / 180.0;
        dist = (Math.sin(lat1) * Math.sin(lat2)) + (Math.cos(lat1) * Math.cos(lat2) * Math.cos(dlon));
        if (dist > 1.0) dist = 1.0;
        dist = Math.acos(dist) * 60 * 180 / Math.PI;
        return dist;
    }

    public static String printPart(double part, char p1, char p2) {
        char c = part < 0.0 ? p2 : p1;
        part = Math.abs(part);
        double degrees = Math.floor(part), min, sec;
        part -= degrees;
        part *= 60;
        min = Math.floor(part);
        part -= min;
        part *= 60;
        sec = part;

        return String.format("%c %02d %02d' %02d\"", c, (int) degrees, (int) min, (int) sec);
    }

    public static String printLoc(double lat, double lon) {
        String north = printPart(lat, 'N', 'S');
        String east = printPart(lon, 'E', 'W');
        return String.format("%s %s", north, east);
    }

    public static void startTimer() {

    }

    public static long mtime() {
        return Instant.now(Clock.systemDefaultZone()).toEpochMilli();
    }

    public static long mgmtime() {
        return Instant.now(Clock.systemUTC()).toEpochMilli();
    }

    public static @NotNull String sprintTime(long now) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public static @NotNull String sprintDate(long now) {
        if (now == 0) {
            return "<none>";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    public static @NotNull String sprintGmtDate(long now) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public static @NotNull String sprintGmt(long now) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
