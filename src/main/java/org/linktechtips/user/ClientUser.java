/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.user;

import org.linktechtips.Main;
import org.linktechtips.manager.Manage;
import org.linktechtips.model.Certificate;
import org.linktechtips.model.Client;
import org.linktechtips.model.Server;
import org.linktechtips.process.config.ConfigEntry;
import org.linktechtips.process.config.ConfigGroup;
import org.linktechtips.process.metar.MetarManage;
import org.linktechtips.process.network.ClientInterface;
import org.linktechtips.process.network.TcpInterface;
import org.linktechtips.support.Support;
import org.linktechtips.constants.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.linktechtips.Main.configManager;

public class ClientUser extends AbstractUser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientUser.class);

    private @Nullable Client thisClient;

    public ClientUser(SocketChannel channel, @NotNull ClientInterface p, String pn, int portnum, int gg) {
        super(channel, p, pn, portnum, gg);
        thisClient = null;
        ConfigGroup gu = configManager.getGroup("system");
        ConfigEntry e = null;
        if (gu != null) {
            e = gu.getEntry("maxclients");
        }
        int total = Objects.requireNonNull(Manage.manager.getVar(p.getVarCurrent())).getValue().getNumber();
        if (e != null && NumberUtils.toInt(e.getData()) <= total) {
            showError(ProtocolConstants.ERR_SERVFULL, "");
            kill(NetworkConstants.KILL_COMMAND);
        }
    }

    @Override
    public void close() {
        super.close();
        if (thisClient != null) {
            int type = thisClient.getType();
            String callsign = thisClient.getCallsign();
            String cid = thisClient.getCid();
            LOGGER.info(String.format("[User/ClientUser]: %s as %s logged out", cid, callsign));
            Main.serverInterface.sendRmClient(null, "*", thisClient, this);
            thisClient.close();
        }
    }

    public void readMotd() {
        String line = GlobalConstants.PRODUCT;
        Main.clientInterface.sendGeneric(Objects.requireNonNull(thisClient).getCallsign(), thisClient, null,
                null, "server", line, ProtocolConstants.CL_MESSAGE);

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(FsdPath.PATH_FSD_MOTD), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }

        for (String msg : lines) {
            Main.clientInterface.sendGeneric(thisClient.getCallsign(), thisClient, null, null, "server", msg, ProtocolConstants.CL_MESSAGE);
        }
    }

    public void parse(@NotNull String s) {
        setActive();
        doParse(s);
    }

    /* Checks if the given callsign is OK. returns 0 on success or errorcode
       on failure */
    public int callsignOk(@NotNull String name) {
        if (name.length() < 2 || name.length() > GlobalConstants.CALLSIGN_BYTES) return ProtocolConstants.ERR_CSINVALID;
        if (StringUtils.indexOfAny(name, "!@#$%*:& \t") != -1) return ProtocolConstants.ERR_CSINVALID;
        for (Client client : Client.clients)
            if (client.getCallsign().equals(name)) return ProtocolConstants.ERR_CSINUSE;
        return ProtocolConstants.ERR_OK;
    }

    public int checkSource(@NotNull String from) {
        if (!from.equalsIgnoreCase(Objects.requireNonNull(thisClient).getCallsign())) {
            showError(ProtocolConstants.ERR_SRCINVALID, from);
            return 0;
        }
        return 1;
    }

    public int getComm(@NotNull String cmd) {
        for (int index = 0; index < ClientConstants.CL_CMD_NAMES.length; index++) {
            if (cmd.startsWith(ClientConstants.CL_CMD_NAMES[index])) {
                return index;
            }
        }
        return -1;
    }

    public ClientUser(SocketChannel d, TcpInterface p, String peerName, int portNum, int g) {
        super(d, p, peerName, portNum, g);
    }

    public void showError(int num, String env) {
        uprintf("$ERserver:%s:%03d:%s:%s\r\n", thisClient != null ? thisClient.getCallsign() :
                "unknown", num, env, ClientConstants.ERR_STR[num]);
    }

    public int checkLogin(String id, String pwd, int req) {
        if (StringUtils.isEmpty(id)) return -2;
        int[] max = new int[1];
        int ok = Certificate.maxLevel(id, pwd, max);
        if (ok == 0) {
            showError(ProtocolConstants.ERR_CIDINVALID, id);
            return -1;
        }
        return Math.min(req, max[0]);
    }
    private String pilotcanbeobs;
    private void configure() {
        ConfigEntry entry;
        ConfigGroup system = configManager.getGroup("system");
        if (system != null) {
            if ((entry = system.getEntry("pilotcanbeobs")) != null) {
                pilotcanbeobs = entry.getData();
            }
        }
    }
    public void execAa(String[] s, int count) {
        configure();
        if (thisClient != null) {
            showError(ProtocolConstants.ERR_REGISTERED, "");
            return;
        }
        if (count < 7) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        int err = callsignOk(s[0]);
        if (err != 0) {
            showError(err, "");
            kill(NetworkConstants.KILL_COMMAND);
            return;
        }
        if (NumberUtils.toInt(s[6]) != GlobalConstants.NEED_REVISION) {
            showError(ProtocolConstants.ERR_REVISION, "");
            kill(NetworkConstants.KILL_PROTOCOL);
            return;
        }
        int req = NumberUtils.toInt(s[5]);
        if (req < 0) req = 0;
        int level = checkLogin(s[3], s[4], req);
        if (Objects.equals(pilotcanbeobs, "false")) {
            if (level == 0) {
                showError(ProtocolConstants.ERR_CSSUSPEND, "");
                kill(NetworkConstants.KILL_COMMAND);
                return;
            } else if(level == 1){
                showError(ProtocolConstants.ERR_PILOTASOBS, "");
                kill(NetworkConstants.KILL_COMMAND);
                return;
            } else if (level == -1) {
                kill(NetworkConstants.KILL_COMMAND);
                return;
            } else if (level == -2) level = 1;
        } else {
            if (level == 0) {
                showError(ProtocolConstants.ERR_CSSUSPEND, "");
                kill(NetworkConstants.KILL_COMMAND);
                return;
            } else if (level == -1) {
                kill(NetworkConstants.KILL_COMMAND);
                return;
            } else if (level == -2) level = 1;
        }
        if (level < req) {
            showError(ProtocolConstants.ERR_LEVEL, s[5]);
            kill(NetworkConstants.KILL_COMMAND);
            return;
        }
        thisClient = new Client(s[3], Server.myServer, s[0], ClientConstants.CLIENT_ATC, level, s[6], s[2],
                -1);
        Main.serverInterface.sendAddClient("*", thisClient, null, this, 0);
        readMotd();
    }

    public void execAp(String[] s, int count) {
        if (thisClient != null) {
            showError(ProtocolConstants.ERR_REGISTERED, "");
            return;
        }
        if (count < 8) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        int err = callsignOk(s[0]);
        if (err != 0) {
            showError(err, "");
            kill(NetworkConstants.KILL_COMMAND);
            return;
        }
        if (NumberUtils.toInt(s[5]) != GlobalConstants.NEED_REVISION) {
            showError(ProtocolConstants.ERR_REVISION, "");
            kill(NetworkConstants.KILL_PROTOCOL);
            return;
        }
        int req = NumberUtils.toInt(s[4]);
        if (req < 0) req = 0;
        int level = checkLogin(s[2], s[3], req);
        if (level < 0) {
            kill(NetworkConstants.KILL_COMMAND);
            return;
        } else if (level == 0) {
            showError(ProtocolConstants.ERR_CSSUSPEND, "");
            kill(NetworkConstants.KILL_COMMAND);
            return;
        }
        if (level < req) {
            showError(ProtocolConstants.ERR_LEVEL, s[4]);
            kill(NetworkConstants.KILL_COMMAND);
            return;
        }
        thisClient = new Client(s[2], Server.myServer, s[0], ClientConstants.CLIENT_PILOT, level, s[4], s[7],
                NumberUtils.toInt(s[6]));
        Main.serverInterface.sendAddClient("*", thisClient, null, this, 0);
        readMotd();
    }

    public void execMulticast(String @NotNull [] s, int count, int cmd, int nargs, int multiok) {
        nargs += 2;
        if (count < nargs) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        String[] subarray = ArrayUtils.subarray(s, 2, s.length);
        String data = Support.catCommand(Arrays.asList(subarray), count - 2, new StringBuilder());
        String from = s[0], to = s[1];
        if (checkSource(from) == 0) return;
        Main.serverInterface.sendMulticast(thisClient, to, data, cmd, multiok, this);
    }

    public void exeCd(String[] s, int count) {
        if (count == 0) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if (checkSource(s[0]) == 0) return;
        kill(NetworkConstants.KILL_COMMAND);
    }

    public void execPilotPos(String @NotNull [] array, int count) {
        if (count < 10) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if (checkSource(array[1]) == 0) return;
        Objects.requireNonNull(thisClient).updatePilot(array);
        Main.serverInterface.sendPilotData(thisClient, this);
    }

    public void execAtcPos(String @NotNull [] array, int count) {
        if (count < 8) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if (checkSource(array[0]) == 0) return;
        Objects.requireNonNull(thisClient).updateAtc(ArrayUtils.subarray(array, 1, array.length));
        Main.serverInterface.sendAtcData(thisClient, this);
    }

    public void execFp(String @NotNull [] array, int count) {
        if (count < 17) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if (checkSource(array[0]) == 0) return;
        Objects.requireNonNull(thisClient).handleFp(ArrayUtils.subarray(array, 2, array.length));
        Main.serverInterface.sendPlan("*", thisClient, null);
    }

    public void execWeather(String[] array, int count) {
        if (count < 3) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if (checkSource(array[0]) == 0) return;
        String source = String.format("%%%s", Objects.requireNonNull(thisClient).getCallsign());
        MetarManage.metarManager.requestMetar(source, array[2], 1, -1);
    }

    public void execAcars(String[] array, int count) {
        if (count < 3) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if (checkSource(array[0]) == 0) return;
        if ("METAR".equalsIgnoreCase(array[2]) && count > 3) {
            String source = String.format("%%%s", Objects.requireNonNull(thisClient).getCallsign());
            MetarManage.metarManager.requestMetar(source, array[3], 0, -1);
        }
    }

    public void execCq(String @NotNull [] array, int count) {
        if (count < 3) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if ("server".equalsIgnoreCase(array[1])) {
            execMulticast(array, count, ProtocolConstants.CL_CQ, 1, 1);
            return;
        }
        if ("RN".equalsIgnoreCase(array[2])) {
            Client cl = Client.getClient(array[1]);
            if (cl != null) {
                String data = String.format("%s:%s:RN:%s:USER:%d", cl.getCallsign(), Objects.requireNonNull(thisClient).getCallsign(), cl.getRealName(), cl.getRating());
                Main.clientInterface.sendPacket(thisClient, cl, null, ClientConstants.CLIENT_ALL, -1, ProtocolConstants.CL_CR, data);
                return;
            }
        }
        if ("fp".equalsIgnoreCase(array[2])) {
            Client cl = Client.getClient(array[3]);
            if (cl == null) {
                showError(ProtocolConstants.ERR_NOSUCHCS, array[3]);
                return;
            }
            if (cl.getPlan() == null) {
                showError(ProtocolConstants.ERR_NOFP, "");
                return;
            }
            Main.clientInterface.sendPlan(thisClient, cl, -1);
        }
    }

    public void execKill(String[] array, int count) {
        if (count < 3) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        Client cl = Client.getClient(array[1]);
        if (cl != null) {
            showError(ProtocolConstants.ERR_NOSUCHCS, array[1]);
            return;
        }

        String junk;

        if (Objects.requireNonNull(thisClient).getRating() < 11) {
            junk = "You are not allowed to kill users!";
            Main.clientInterface.sendGeneric(thisClient.getCallsign(), thisClient, null,
                    null, "server", junk, ProtocolConstants.CL_MESSAGE);
            junk = String.format("%s attempted to remove %s, but was not allowed to", thisClient.getCallsign(), array[1]);
            LOGGER.error("[User/ClientUser]: " + junk);
        } else {
            junk = String.format("Attempting to kill %s", array[1]);
            Main.clientInterface.sendGeneric(thisClient.getCallsign(), thisClient, null,
                    null, "server", junk, ProtocolConstants.CL_MESSAGE);
            junk = String.format("%s Killed %s", thisClient.getCallsign(), array[1]);
            LOGGER.info("[User/ClientUser]: " + junk);
            Main.serverInterface.sendKill(cl, array[2]);
        }
    }

    void doParse(@NotNull String s) {
        String cmd = "";
        List<String> list = new ArrayList<>();
        cmd = Support.snapPacket(s, cmd, 3);
        int index = getComm(cmd), count;
        if (index == -1) {
            showError(ProtocolConstants.ERR_SYNTAX, "");
            return;
        }
        if (thisClient == null && index != ProtocolConstants.CL_ADDATC && index != ProtocolConstants.CL_ADDPILOT)
            return;

        /* Just a hack to put the pointer on the first arg here */
        String str = s.substring(ClientConstants.CL_CMD_NAMES[index].length());

        count = Support.breakPacket(str, list, 100);

        String[] array = list.toArray(new String[0]);
        switch (index) {
            case ProtocolConstants.CL_ADDATC -> execAa(array, count);
            case ProtocolConstants.CL_ADDPILOT -> execAp(array, count);
            case ProtocolConstants.CL_PLAN -> execFp(array, count);
            /* Handled like RMPILOT */
            case ProtocolConstants.CL_RMATC, ProtocolConstants.CL_RMPILOT -> exeCd(array, count);
            case ProtocolConstants.CL_PILOTPOS -> execPilotPos(array, count);
            case ProtocolConstants.CL_ATCPOS -> execAtcPos(array, count);
            case ProtocolConstants.CL_PONG, ProtocolConstants.CL_PING -> execMulticast(array, count, index, 0, 1);
            case ProtocolConstants.CL_MESSAGE -> execMulticast(array, count, index, 1, 1);
            case ProtocolConstants.CL_REQHANDOFF, ProtocolConstants.CL_ACHANDOFF, ProtocolConstants.CL_REPCOM ->
                    execMulticast(array, count, index, 1, 0);
            case ProtocolConstants.CL_SB, ProtocolConstants.CL_PC, ProtocolConstants.CL_REQCOM -> execMulticast(array, count, index, 0, 0);
            case ProtocolConstants.CL_WEATHER -> execWeather(array, count);
            case ProtocolConstants.CL_REQACARS -> execAcars(array, count);
            case ProtocolConstants.CL_CR -> execMulticast(array, count, index, 2, 0);
            case ProtocolConstants.CL_CQ -> execCq(array, count);
            case ProtocolConstants.CL_KILL -> execKill(array, count);
            default -> showError(ProtocolConstants.ERR_SYNTAX, "");
        }
    }

    public @Nullable Client getThisClient() {
        return thisClient;
    }
}
