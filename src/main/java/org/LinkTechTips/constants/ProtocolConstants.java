/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.constants;

public class ProtocolConstants {

    public static final int CMD_NOTIFY = 0;
    public static final int CMD_REQ_METAR = 1;
    public static final int CMD_PING = 2;
    public static final int CMD_PONG = 3;
    public static final int CMD_SYNC = 4;
    public static final int CMD_LINK_DOWN = 5;
    public static final int CMD_NO_WX = 6;
    public static final int CMD_ADD_CLIENT = 7;
    public static final int CMD_RM_CLIENT = 8;
    public static final int CMD_PLAN = 9;
    public static final int CMD_PD = 10;
    public static final int CMD_AD = 11;
    public static final int CMD_CERT = 12;
    public static final int CMD_MULTICAST = 13;
    public static final int CMD_WEATHER = 14;
    public static final int CMD_METAR = 15;
    public static final int CMD_ADD_WPROFILE = 16;
    public static final int CMD_DEL_WPROFILE = 17;
    public static final int CMD_KILL = 18;
    public static final int CMD_RESET = 19;

    public static final int CL_ADDATC = 0;
    public static final int CL_RMATC = 1;
    public static final int CL_ADDPILOT = 2;
    public static final int CL_RMPILOT = 3;
    public static final int CL_REQHANDOFF = 4;
    public static final int CL_MESSAGE = 5;
    public static final int CL_REQWEATHER = 6;
    public static final int CL_PILOTPOS = 7;
    public static final int CL_ATCPOS = 8;
    public static final int CL_PING = 9;
    public static final int CL_PONG = 10;
    public static final int CL_ACHANDOFF = 11;
    public static final int CL_PLAN = 12;
    public static final int CL_SB = 13;
    public static final int CL_PC = 14;
    public static final int CL_WEATHER = 15;
    public static final int CL_CLOUDDATA = 16;
    public static final int CL_WINDDATA = 17;
    public static final int CL_TEMPDATA = 18;
    public static final int CL_REQCOM = 19;
    public static final int CL_REPCOM = 20;
    public static final int CL_REQACARS = 21;
    public static final int CL_REPACARS = 22;
    public static final int CL_ERROR = 23;
    public static final int CL_CQ = 24;
    public static final int CL_CR = 25;
    public static final int CL_KILL = 26;
    public static final int CL_WDELTA = 27;

    public static final int CL_MAX = 27;

    public static final int CERT_ADD = 0;
    public static final int CERT_DELETE = 1;
    public static final int CERT_MODIFY = 2;

    public static final int ERR_OK = 0;      /* No error */
    public static final int ERR_CSINUSE = 1;      /* Callsign in use */
    public static final int ERR_CSINVALID = 2;      /* Callsign invalid */
    public static final int ERR_REGISTERED = 3;      /* Already registered */
    public static final int ERR_SYNTAX = 4;      /* Syntax error */
    public static final int ERR_SRCINVALID = 5;      /* Invalid source in packet */
    public static final int ERR_CIDINVALID = 6;      /* Invalid CID/password */
    public static final int ERR_NOSUCHCS = 7;      /* No such callsign */
    public static final int ERR_NOFP = 8;      /* No flightplan */
    public static final int ERR_NOWEATHER = 9;      /* No such weather profile */
    public static final int ERR_REVISION = 10;      /* Invalid protocol revision */
    public static final int ERR_LEVEL = 11;      /* Requested level too high */
    public static final int ERR_SERVFULL = 12;      /* No more clients */
    public static final int ERR_CSSUSPEND = 13;      /* CID/PID suspended */
    public static final int ERR_PILOTASOBS = 14;     /* cannot log in as observer */


    public static final String[] CMD_NAMES = {
            "NOTIFY",
            "REQMETAR",
            "PING",
            "PONG",
            "SYNC",
            "LINKDOWN",
            "NOWX",
            "ADDCLIENT",
            "RMCLIENT",
            "PLAN",
            "PD",    /* Pilot data */
            "AD",    /* ATC data */
            "ADDCERT",
            "MC",
            "WX",
            "METAR",
            "AWPROF",
            "DWPROF",
            "KILL",
            "RESET"
    };

    public static final int[] SILENT_OK = {
            1,  /* notify */
            1,  /* reqmetar */
            1,  /* ping */
            1,  /* pong */
            1,  /* sync */
            1,  /* linkdown */
            1,  /* nowx */
            1,  /* addclient */
            1,  /* rmclient */
            1,  /* plan */
            0,  /* pd */
            0,  /* ad */
            1,  /* addcert */
            0,  /* mc */
            1,  /* wx */
            1,  /* metar */
            1,  /* add w profile */
            1,  /* del w profile */
            1,  /* kill client */
            1  /* reset */
    };
}
