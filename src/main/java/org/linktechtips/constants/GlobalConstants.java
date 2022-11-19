
/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.constants;

public class GlobalConstants {
    public static final String PRODUCT = "BetterFSD Java Edition Version 1.1.0 By 4185 QQ:3174327625";
    public static final String VERSION = "V1.1.0";
    public static final int NEED_REVISION = 9;

    /**
     * WARNING!!!: The USER_TIMEOUT (idle time of a SOCKET before it's dropped)
     * should not be higher than the SERVER_TIMEOUT (idle time of a
     * server)
     */
    public static final int USER_TIMEOUT = 500_000;
    public static final int SERVER_TIMEOUT = 800_000;
    public static final int CLIENT_TIMEOUT = 800_000;
    public static final int SILENT_CLIENT_TIMEOUT = 36000_000;
    public static final int WIND_DELTA_TIMEOUT = 70_000;

    public static final int USER_PING_TIMEOUT = 200_000;
    public static final int USER_FEED_CHECK = 3000;
    public static final int LAG_CHECK = 60_000;
    public static final int NOTIFY_CHECK = 300_000;
    public static final int SYNC_TIMEOUT = 120_000;
    public static final int SERVER_MAX_TOOK = 240;
    public static final int MAX_HOPS = 10;
    public static final int GUARD_RETRY = 120_000;
    public static final int CALLSIGN_BYTES = 12;
    public static final int MAX_LINE_LENGTH = 1026;
    public static final int MAX_METAR_DOWNLOAD_TIME = 1600_000;
    public static final int CERT_FILE_CHECK = 30_000;

    public static final int WHAZZUP_CHECK = 1_000;
    public static final int CONNECT_DELAY = 20_000;

    public static final int LEV_SUSPENDED = 0;
    public static final int LEV_OBSPILOT = 1;
    public static final int LEV_STUDENT1 = 2;
    public static final int LEV_STUDENT2 = 3;
    public static final int LEV_STUDENT3 = 4;
    public static final int LEV_CONTROLLER1 = 5;
    public static final int LEV_CONTROLLER2 = 6;
    public static final int LEV_CONTROLLER3 = 7;
    public static final int LEV_INSTRUCTOR1 = 8;
    public static final int LEV_INSTRUCTOR2 = 9;
    public static final int LEV_INSTRUCTOR3 = 10;
    public static final int LEV_SUPERVISOR = 11;
    public static final int LEV_ADMINISTRATOR = 12;
    public static final int LEV_MAX = 12;
}
