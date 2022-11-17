/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.constants;

public class NetworkConstants {
    public static final int KILL_NONE = 0;

    public static final int KILL_COMMAND = 1;

    public static final int KILL_FLOOD = 2;

    public static final int KILL_INIT_TIMEOUT = 3;

    public static final int KILL_DATA_TIMEOUT = 4;

    public static final int KILL_CLOSED = 5;

    public static final int KILL_WRITE_ERR = 6;

    public static final int KILL_KILL = 7;

    public static final int KILL_PROTOCOL = 8;

    public static final int FEED_IN = 1;

    public static final int FEED_OUT = 2;

    public static final int FEED_BOTH = 3;

    public static final String[] KILL_REASONS = {
            "",
            "closed on command",
            "flooding",
            "initial timeout",
            "socket stalled",
            "connection closed",
            "write error",
            "killed on command",
            "protocol revision error"
    };
}
