/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.constants;

public class SystemConstants {

    public static final int SYS_SERVERS = 0;
    public static final int SYS_INFORMATION = 1;
    public static final int SYS_PING = 2;
    public static final int SYS_CONNECT = 3;
    public static final int SYS_TIME = 4;
    public static final int SYS_ROUTE = 5;
    public static final int SYS_WEATHER = 6;
    public static final int SYS_DISCONNECT = 7;
    public static final int SYS_HELP = 8;
    public static final int SYS_QUIT = 9;
    public static final int SYS_STAT = 10;
    public static final int SYS_SAY = 11;
    public static final int SYS_CLIENTS = 12;
    public static final int SYS_CERT = 13;
    public static final int SYS_PWD = 14;
    public static final int SYS_DISTANCE = 15;
    public static final int SYS_RANGE = 16;
    public static final int SYS_LOG = 17;
    public static final int SYS_WALL = 18;
    public static final int SYS_DELGUARD = 19;
    public static final int SYS_METAR = 20;
    public static final int SYS_WP = 21;
    public static final int SYS_KILL = 22;
    public static final int SYS_POS = 23;
    public static final int SYS_DUMP = 24;
    public static final int SYS_SERVERS2 = 25;
    public static final int SYS_REFMETAR = 26;

    public static final String[] SYS_CMDS = {
            "servers",
            "info",
            "ping",
            "connect",
            "time",
            "route",
            "weather",
            "disconnect",
            "help",
            "quit",
            "stat",
            "say",
            "clients",
            "cert",
            "pwd",
            "distance",
            "range",
            "log",
            "wall",
            "delguard",
            "metar",
            "wp",
            "kill",
            "pos",
            "dump",
            "servers2",
            "refreshmetar"
    };

    public static final int[] NEED_AUTHORIZATION = {
            0,          /* servers */
            1,          /* info    */
            1,          /* ping    */
            1,          /*  connect */
            0,          /* time    */
            0,          /* route   */
            0,          /* weather  */
            1,          /* disconnect */
            0,          /* help    */
            0,          /* quit  */
            1,          /* stat */
            1,          /* say */
            1,          /*  clients */
            1,          /* cert */
            0,          /* pwd  */
            1,          /*  distance */
            1,          /*  range */
            1,          /* log */
            1,          /* wall */
            1,          /* delguard */
            0,          /* metar */
            1,          /* wp */
            1,          /* kill */
            0,          /* pos */
            1,          /* dump */
            0,          /* servers2 */
            1          /* refresh metar */
    };
}
