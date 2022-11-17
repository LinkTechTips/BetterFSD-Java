/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.constants;

public class ClientConstants {

    public static final int CLIENT_PILOT = 1;

    public static final int CLIENT_ATC = 2;

    public static final int CLIENT_ALL = 3;

    public static final String[] CL_CMD_NAMES = {
            "#AA",
            "#DA",
            "#AP",
            "#DP",
            "$HO",
            "#TM",
            "#RW",
            "@",
            "%",
            "$PI",
            "$PO",
            "$HA",
            "$FP",
            "#SB",
            "#PC",
            "#WX",
            "#CD",
            "#WD",
            "#TD",
            "$C?",
            "$CI",
            "$AX",
            "$AR",
            "$ER",
            "$CQ",
            "$CR",
            "$!!",
            "#DL"
    };

    public static final String[] ERR_STR = {
            "No error",
            "Callsign in use",
            "Invalid callsign",
            "Already registerd",
            "Syntax error",
            "Invalid source callsign",
            "Invalid CID/password",
            "No such callsign",
            "No flightplan",
            "No such weather profile",
            "Invalid protocol revision",
            "Requested level too high",
            "Too many clients connected",
            "CID/PID was suspended",
            "Cannot log in as Observer"
    };
}
