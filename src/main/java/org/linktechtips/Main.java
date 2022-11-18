
/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.linktechtips.constants.*;
import org.linktechtips.httpapi.httpApiManage;
import org.linktechtips.manager.Manage;
import org.linktechtips.model.Certificate;
import org.linktechtips.model.Client;
import org.linktechtips.model.Flightplan;
import org.linktechtips.model.Server;
import org.linktechtips.plugins.IPluginService;
import org.linktechtips.plugins.PluginLoader;
import org.linktechtips.process.PMan;
import org.linktechtips.process.config.ConfigEntry;
import org.linktechtips.process.config.ConfigGroup;
import org.linktechtips.process.config.ConfigManager;
import org.linktechtips.process.metar.MetarManage;
import org.linktechtips.process.network.ClientInterface;
import org.linktechtips.process.network.ServerInterface;
import org.linktechtips.process.network.SystemInterface;
import org.linktechtips.support.Support;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

import static org.slf4j.LoggerFactory.getLogger;


public class Main {
    private final static Logger LOGGER = getLogger(Main.class);
    public static ConfigManager configManager;
    public static ServerInterface serverInterface;
    public static SystemInterface systemInterface;
    public static ClientInterface clientInterface;
    private int clientPort;
    private int serverPort;
    private int systemPort;
    private final @NotNull PMan pManager;
    private String certFile;
    private String whazzupFile;
    private String whazzupjsonFile;
    private String mysqlmode;
    private String sqlurl;
    private String sqluser;
    private String sqlpassword;
    private long timer;
    private long prevNotify;
    private long prevLagCheck;
    private long certFileStat;
    private int fileOpen;
    private long prevCertCheck;
    private long prevWhazzup;

    public Main(String configFile) {
        LOGGER.info("[BetterFSD]: Booting Server");
        pManager = new PMan();
        /* Start the information manager */
        Manage.manager = new Manage();
        configManager = new ConfigManager(configFile);
        pManager.registerProcess(configManager);
        LOGGER.info("[BetterFSD]: Create Information Manager OK");
        /* Create the HTTPAPI manager */
        new httpApiManage();
        /* Create the METAR manager */
        MetarManage.metarManager = new MetarManage();
        pManager.registerProcess(MetarManage.metarManager);
        LOGGER.info("[METAR]: Create METAR Manager OK");
        /* Read the system configuration */
        configure();
        LOGGER.info("[Config]: Read System Configuration OK");
        /* Create the management variables */
        createManageVars();
        LOGGER.info("[Management]: Management Variables OK");
        /* Create the server and the client interfaces */
        createInterfaces();
        LOGGER.info("[Interfaces]: Create Interfaces OK");
        /* Connect to the other server */
        makeConnections();
        LOGGER.info("[Plugins]: Start loading plugins");
        try {
            List<IPluginService> services = PluginLoader.loadPlugins();
            for (IPluginService service : services) {
                LOGGER.info(String.format("[Plugins]: %s %s loaded successfully", service.PluginName(),
                        service.PlugunVersion()));
                service.PluginService();
            }
            LOGGER.info(String.format("[Plugins]: %s plugins loaded successfully", services.size()));
        } catch (MalformedURLException e) {
            LOGGER.error("[Plugins]: Failed to load plugins");
            e.printStackTrace();
        }
        LOGGER.info("BetterFSD Java Edition GO!");
        LOGGER.info("BY 4185 QQ:317432725");
        prevNotify = prevLagCheck = timer = Support.mtime();
        prevWhazzup = Support.mtime();
        fileOpen = 0;
    }

    public void close() {
    }

    /* Here we do timeout checks. This function is triggered every second to
   reduce the load on the server */
    public void doChecks() {
        long now = Support.mtime();
        if ((now - prevNotify) > GlobalConstants.NOTIFY_CHECK) {
            ConfigGroup sgroup = configManager.getGroup("system");
            if (sgroup != null && sgroup.isChanged())
                configMyServer();
            serverInterface.sendServerNotify("*", Server.myServer, null);
            prevNotify = now;
        }
        if ((now - prevLagCheck) > GlobalConstants.LAG_CHECK) {
            String data = String.format("-1 %d", Support.mtime());
            serverInterface.sendPing("*", data);
            prevLagCheck = now;
        }
        if ((now - prevCertCheck) > GlobalConstants.CERT_FILE_CHECK) {
            ConfigEntry entry;
            ConfigGroup sysgroup = configManager.getGroup("system");
            if (sysgroup != null) {
                if ((entry = sysgroup.getEntry("certificates")) != null) {
                    certFile = entry.getData().toUpperCase();
                    Path file = Paths.get(certFile);
                    if (Files.exists(file)) {
                        try {
                            BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                            long fileLastModified = attr.lastModifiedTime().toMillis();
                            prevCertCheck = now;
                            if (certFileStat != fileLastModified) {
                                certFileStat = fileLastModified;
                                readCert();
                            }
                        } catch (IOException e) {
                            LOGGER.warn("[BetterFSD]: Read cert file info failed.", e);
                        }
                    }
                }
            }
        }
// WhazzUp Start
        if ((now - prevWhazzup) >= GlobalConstants.WHAZZUP_CHECK) {
            ConfigEntry entry;
            ConfigGroup sysgroup = configManager.getGroup("system");
            if (sysgroup != null) {
                if ((entry = sysgroup.getEntry("whazzup")) != null) {
                    whazzupFile = entry.getData();
                    String whazzupTemp = String.format("%s%s", whazzupFile, ".tmp");
                    prevWhazzup = now;
                    if (fileOpen == 0) {
                        try (FileOutputStream wzfile = new FileOutputStream(whazzupTemp)) {
                            fileOpen = 1;
                            wzfile.write(String.format("%s%s\n", "![DateStamp]", Support.sprintGmtDate(now)).getBytes());
                            wzfile.write(String.format("%s\n", "!GENERAL").getBytes());
                            wzfile.write(String.format("%s = %d\n", "VERSION", 1).getBytes());
                            wzfile.write(String.format("%s = %d\n", "RELOAD", 1).getBytes());
                            wzfile.write(String.format("%s = %s\n", "UPDATE", Support.sprintGmt(now)).getBytes());
                            wzfile.write(String.format("%s = %d\n", "CONNECTED CLIENTS", Client.clients.size()).getBytes());
                            wzfile.write(String.format("%s = %d\n", "CONNECTED SERVERS", Server.servers.size()).getBytes());
                            wzfile.write(String.format("%s\n", "!CLIENTS").getBytes());
                            String dataSeg1, dataSeg2, dataSeg3, dataSeg4, dataSeg5, dataSeg6, dataSeg7;
                            for (Client tempClient : Client.clients) {
                                dataSeg1 = String.format("%s:%s:%s:%s", tempClient.getCallsign(), tempClient.getCid(),
                                        tempClient.getRealName(), tempClient.getType() == ClientConstants.CLIENT_ATC ? "ATC" : "PILOT");
                                if (tempClient.getFrequency() != 0 && tempClient.getFrequency() < 100_000) {
                                    dataSeg2 = String.format("1%02d.%03d", tempClient.getFrequency() / 1000, tempClient.getFrequency() % 1000);
                                } else {
                                    dataSeg2 = "";
                                }

                                Flightplan tempFlightplan = tempClient.getPlan();
                                if (tempClient.getLat() != 0 && tempClient.getAltitude() < 100_000 && tempClient.getLon() != 0) {
                                    dataSeg3 = String.format("%f:%f:%d:%d", tempClient.getLat(), tempClient.getLon(), tempClient.getAltitude(), tempClient.getGroundSpeed());
                                } else {
                                    dataSeg3 = ":::";
                                }

                                if (tempFlightplan != null) {
                                    dataSeg4 = String.format("%s:%d:%s:%s:%s", tempFlightplan.getAircraft(),
                                            tempFlightplan.getTasCruise(), tempFlightplan.getDepAirport(),
                                            tempFlightplan.getAlt(), tempFlightplan.getDestAirport());
                                } else {
                                    dataSeg4 = "::::";
                                }
                                dataSeg5 = String.format("%s:%s:%d:%d:%d:%d", tempClient.getLocation().getIdent(), tempClient.getProtocol(), tempClient.getRating(), tempClient.getTransponder(), tempClient.getFacilityType(), tempClient.getVisualRange());

                                if (tempFlightplan != null) {
                                    dataSeg6 = String.format("%d:%c:%d:%d:%d:%d:%d:%d:%s:%s:%s", tempFlightplan.getRevision(),
                                            tempFlightplan.getType(), tempFlightplan.getDepTime(), tempFlightplan.getActDepTime(),
                                            tempFlightplan.getHrsEnroute(), tempFlightplan.getMinEnroute(), tempFlightplan.getHrsFuel(),
                                            tempFlightplan.getMinFuel(), tempFlightplan.getAltAirport(), tempFlightplan.getRemarks(), tempFlightplan.getRoute());
                                } else {
                                    dataSeg6 = "::::::::::";
                                }

                                dataSeg7 = String.format("::::::%s:%s", Support.sprintGmt(tempClient.getStartTime()), tempClient.getHeading());
                                wzfile.write(String.format("%s:%s:%s:%s:%s:%s:%s\n", dataSeg1, dataSeg2, dataSeg3, dataSeg4, dataSeg5, dataSeg6, dataSeg7).getBytes());
                            }
                            wzfile.write("!SERVERS\n".getBytes());
                            for (Server tempServer : Server.servers) {
                                if ("n/a".equals(tempServer.getHostName())) {
                                    String dataLine = String.format("%s:%s:%s:%s:%d", tempServer.getIdent(), tempServer.getHostName(), tempServer.getLocation(), tempServer.getName(), (tempServer.getFlags() & ServerConstants.SERVER_SILENT) != 0 ? 0 : 1);
                                    wzfile.write(String.format("%s\n", dataLine).getBytes());
                                }
                            }
                        } catch (IOException e) {
                            LOGGER.warn("[BetterFSD]: Open whazzup file failed.", e);
                        }
                        File realFile = new File(whazzupFile);
                        realFile.delete();
                        File tempFile = new File(whazzupTemp);
                        tempFile.renameTo(realFile);
                    }
                    fileOpen = 0;
                }
                if ((entry = sysgroup.getEntry("whazzupjson")) != null) {
                    whazzupjsonFile = entry.getData();
                    String whazzupjsonTemp = String.format("%s%s", whazzupjsonFile, ".tmp");
                    prevWhazzup = now;
                    if (fileOpen == 0) {
                        try (FileOutputStream wzfile = new FileOutputStream(whazzupjsonTemp)) {
                            fileOpen = 1;
                            wzfile.write(String.format("{\"version\":%d,\"reload\":%d,\"lastupdate\":\"%s\",\"clients\":%d,\"servers\":%d,\"clients\":[", 1, 1, Support.sprintGmt(now),
                                    Client.clients.size(), Server.servers.size()).getBytes());
                            String dataSeg1, dataSeg2 = null, dataSeg3 = null, dataSeg4, dataSeg5, dataSeg6;
                            for (Client tempClient : Client.clients) {
                                dataSeg1 = String.format("{\"type\":\"%s\",\"callsign\":\"%s\",\"realname\":\"%s\",\"cid\":\"%s\",\"rating\":%d,\"visualrange\":%d,\"ident\":\"%s\",\"protocol\":%s",
                                        tempClient.getType() == ClientConstants.CLIENT_ATC ? "ATC" : "PILOT", tempClient.getCallsign(), tempClient.getRealName(), tempClient.getCid(), tempClient.getRating(),
                                        tempClient.getVisualRange(), tempClient.getIdentFlag(), tempClient.getProtocol());
                                if (tempClient.getFrequency() != 0 && tempClient.getFrequency() < 100_000) {
                                    dataSeg2 = String.format(",\"frequency\":1%02d.%03d", tempClient.getFrequency() / 1000, tempClient.getFrequency() % 1000);
                                }

                                Flightplan tempFlightplan = tempClient.getPlan();
                                if (tempClient.getLat() != 0 && tempClient.getAltitude() < 100_000 && tempClient.getLon() != 0) {
                                    dataSeg3 = String.format(",\"latlng\":[%f,%f],\"altitude\":%d,\"groundspeed\":%d", tempClient.getLat(), tempClient.getLon(), tempClient.getAltitude(), tempClient.getGroundSpeed());
                                }

                                if (tempFlightplan != null) {
                                    dataSeg4 = String.format(",\"plan\":{\"aircraft\":\"%s\",\"tascruise\":%d,\"depairport\":\"%s\",\"alt\":\"%s\",\"destairport\":\"%s\",\"revision\":%d,\"type\":\"%c\",\"deptime\":%d,\"actdeptime\":%d," +
                                                    "\"hrsenroute\":%d,\"minenroute\":%d,\"hrsfuel\":%d,\"minfuel\":%d,\"altairport\":\"%s\",\"remarks\":\"%s\",\"route\":\"%s\"}", tempFlightplan.getAircraft(), tempFlightplan.getTasCruise(),
                                            tempFlightplan.getDepAirport(), tempFlightplan.getAlt(), tempFlightplan.getDestAirport(), tempFlightplan.getRevision(), tempFlightplan.getType(), tempFlightplan.getDepTime(), tempFlightplan.getActDepTime(),
                                            tempFlightplan.getHrsEnroute(), tempFlightplan.getMinEnroute(), tempFlightplan.getHrsFuel(), tempFlightplan.getMinFuel(), tempFlightplan.getAltAirport(), tempFlightplan.getRemarks(),
                                            tempFlightplan.getRoute());
                                } else {
                                    dataSeg4 = ",\"plan\":{}";
                                }
                                dataSeg5 = String.format(",\"heading\":%s,\"transponder\":%d,\"facilitytype\":%d", tempClient.getHeading(), tempClient.getTransponder(), tempClient.getFacilityType());

                                dataSeg6 = String.format(",\"starttime\":%s},", Support.sprintGmt(tempClient.getStartTime()));
                                wzfile.write(String.format("%s%s%s%s%s%s", dataSeg1, dataSeg2, dataSeg3, dataSeg4, dataSeg5, dataSeg6).getBytes());
                            }
                            wzfile.write("null],\"servers\":[".getBytes());
                            for (Server tempServer : Server.servers) {
                                if ("n/a".equals(tempServer.getHostName())) {
                                    String dataLine = String.format("{\"ident\":\"%s\",\"hostname\":\"%s\",\"location\":\"%s\",\"name\":\"%s\",\"slient\":%d}", tempServer.getIdent(), tempServer.getHostName(), tempServer.getLocation(),
                                            tempServer.getName(), (tempServer.getFlags() & ServerConstants.SERVER_SILENT) != 0 ? 0 : 1);
                                    wzfile.write(String.format("%s", dataLine).getBytes());
                                }
                            }
                            wzfile.write("]}".getBytes());
                        } catch (IOException e) {
                            LOGGER.warn("[BetterFSD]: Open whazzup file failed.", e);
                        }
                        File realFile = new File(whazzupjsonFile);
                        realFile.delete();
                        File tempFile = new File(whazzupjsonTemp);
                        tempFile.renameTo(realFile);
                    }
                    fileOpen = 0;
                }
            }
        }
// WhazzUp End
        for (Server tempServer : Server.servers) {
            if (now - tempServer.getAlive() > GlobalConstants.SERVER_TIMEOUT && tempServer != Server.myServer) {
                tempServer.close();
            }
        }

    /* Check for client timeouts. We should not drop clients if we are in
       silent mode; If we are in silent mode, we won't receive updates, so
       every client would time out. When we are a silent server, the limit
       will be SILENTCLIENTTIMEOUT, which is about 10 hours  */

        int limit = (Server.myServer.getFlags() & ServerConstants.SERVER_SILENT) != 0 ? GlobalConstants.SILENT_CLIENT_TIMEOUT : GlobalConstants.CLIENT_TIMEOUT;
        for (Client client : Client.clients) {
            if (client.getLocation() != Server.myServer) {
                if ((now - client.getAlive()) > limit) {
                    client.close();
                }
            }
        }

    }

    public void run() {
        pManager.run();
        if (timer != Support.mtime()) {
            timer = Support.mtime();
            doChecks();
        }
    }

    private void configMyServer() {
        int mode = 0;
        String serverIdent = null, serverName = null;
        String serverMail = null, serverHostName = null;
        String serverLocation = null;
        ConfigEntry entry;
        ConfigGroup system = configManager.getGroup("system");
        if (system != null) {
            system.setChanged(false);
            if ((entry = system.getEntry("ident")) != null) {
                serverIdent = entry.getData();
            }
            if ((entry = system.getEntry("name")) != null) {
                serverName = entry.getData();
            }
            if ((entry = system.getEntry("email")) != null) {
                serverMail = entry.getData();
            }
            if ((entry = system.getEntry("hostname")) != null) {
                serverHostName = entry.getData();
            }
            if ((entry = system.getEntry("location")) != null) {
                serverLocation = entry.getData();
            }
            if ((entry = system.getEntry("mode")) != null) {
                if ("silent".equalsIgnoreCase(entry.getData())) {
                    mode = ServerConstants.SERVER_SILENT;
                }
            }
        }
        if (serverIdent == null) {
            serverIdent = String.format("%d", ProcessHandle.current().pid());
            LOGGER.error("[BetterFSD]: No serverident specified");
        }
        if (serverMail == null) {
            serverMail = "";
            LOGGER.error("[BetterFSD]: No server mail address specified");
        }
        if (serverHostName == null) {
            try {
                serverHostName = InetAddress.getLocalHost().getHostName();
                LOGGER.error("[BetterFSD]: No server host name specified");
            } catch (UnknownHostException e) {
                LOGGER.error("[BetterFSD]: Get localhost name failed.");
            }
        }
        if (serverName == null) {
            LOGGER.error("[BetterFSD]: No servername specified");
            serverName = serverHostName;
        }
        if (serverLocation == null) {
            LOGGER.error("[BetterFSD]: No serverlocation specified");
            serverLocation = "";
        }
        int flags = mode;
        if (MetarManage.metarManager.getSource() == MetarSource.SOURCE_NETWORK) {
            flags |= ServerConstants.SERVER_METAR;
        }
        if (Server.myServer != null) {
            Server.myServer.configure(serverName, serverMail, serverHostName, GlobalConstants.VERSION, serverLocation);
        } else {
            Server.myServer = new Server(serverIdent, serverName, serverMail, serverHostName, GlobalConstants.VERSION, flags, serverLocation);
        }
    }

    private void configure() {
        clientPort = 6809;
        serverPort = 3011;
        systemPort = 3012;
        ConfigEntry entry;
        ConfigGroup system = configManager.getGroup("system");
        if (system != null) {
            if ((entry = system.getEntry("clientport")) != null) {
                clientPort = entry.getInt();
            }
            if ((entry = system.getEntry("serverport")) != null) {
                serverPort = entry.getInt();
            }
            if ((entry = system.getEntry("systemport")) != null) {
                systemPort = entry.getInt();
            }
            if ((entry = system.getEntry("mysqlmode")) != null) {
                mysqlmode = entry.getData();
                if (Objects.equals(mysqlmode, "false")) {
                    if ((entry = system.getEntry("certificates")) != null) {
                        certFile = entry.getData();
                    }
                } else if (Objects.equals(mysqlmode, "true")) {
                    if ((entry = system.getEntry("sqlurl")) != null) {
                        sqlurl = entry.getData();
                    }
                    if ((entry = system.getEntry("sqluser")) != null) {
                        sqluser = entry.getData();
                    }
                    if ((entry = system.getEntry("sqlpassword")) != null) {
                        sqlpassword = entry.getData();
                    }
                }
            }
            if ((entry = system.getEntry("whazzup")) != null) {
                whazzupFile = entry.getData();
            }
            if ((entry = system.getEntry("whazzupjson")) != null) {
                whazzupjsonFile = entry.getData();
            }
    }

    configMyServer();

    readCert();

}

    void handleCidLine(@NotNull String line) {
        Certificate tempcert;
        int mode, level;
        String cid, pwd;
        List<String> array = new ArrayList<>();
        if (StringUtils.isEmpty(line)) {
            return;
        }
        if (line.charAt(0) == ';' || line.charAt(0) == '#') return;
        if (Support.breakArgs(line, array, 4) < 3) return;
        cid = array.get(0);
        level = NumberUtils.toInt(array.get(2));
        pwd = array.get(1);
        tempcert = Certificate.getCert(cid);
        if (tempcert == null) {
            tempcert = new Certificate(cid, pwd, level, Support.mgmtime(), Server.myServer.getIdent());
            mode = ProtocolConstants.CERT_ADD;
        } else {
            tempcert.setLiveCheck(1);
            if (tempcert.getPassword().equalsIgnoreCase(pwd) && level == tempcert.getLevel())
                return;
            tempcert.configure(pwd, level, Support.mgmtime(), Server.myServer.getIdent());
            mode = ProtocolConstants.CERT_MODIFY;
        }
        if (serverInterface != null) serverInterface.sendCert("*", mode, tempcert, null);
    }

    void readCert() {
        if (Objects.equals(mysqlmode, "false")) {
            if (StringUtils.isBlank(certFile)) return;

            List<String> lines;
            File file = new File(certFile);
            try {
                lines = Files.readAllLines(Paths.get(certFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error(String.format("[BetterFSD]: Could not open certificate file '%s'",
                        file.getAbsolutePath()), e);
                return;
            }

            for (Certificate temp : Certificate.certs) {
                temp.setLiveCheck(0);
            }
            LOGGER.info(String.format("[BetterFSD]: Reading certificates from '%s'", certFile));
            for (String line : lines) {
                handleCidLine(line);
            }
            for (Certificate temp : Certificate.certs) {
                if (temp.getLiveCheck() == 0) {
                    serverInterface.sendCert("*", ProtocolConstants.CERT_DELETE, temp, null);
                    temp.close();
                }
            }
        } else if (Objects.equals(mysqlmode, "true")) {
            //TODO
        }
    }

    private void createManageVars() {
        int varNum = Manage.manager.addVar("system.boottime", ManageVarType.ATT_DATE);
        Manage.manager.setVar(varNum, Support.mtime());
        varNum = Manage.manager.addVar("version.system", ManageVarType.ATT_VARCHAR);
        Manage.manager.setVar(varNum, GlobalConstants.VERSION);
    }

    private void createInterfaces() {
        String prompt = String.format("%s>", Server.myServer.getIdent());
        clientInterface = new ClientInterface(clientPort, "client", "client interface");
        serverInterface = new ServerInterface(serverPort, "server", "server interface");
        systemInterface = new SystemInterface(systemPort, "system", "system management interface");
        systemInterface.setPrompt(prompt);

        serverInterface.setFeedStrategy(NetworkConstants.FEED_BOTH);

        /* Clients may send a maximum of 100000 bytes/second */
        clientInterface.setFloodLimit(100000);
        clientInterface.setFeedStrategy(NetworkConstants.FEED_IN);

        /* Clients may have a buffer of 100000 bytes */
        clientInterface.setOutBufLimit(100000);

        pManager.registerProcess(clientInterface);
        pManager.registerProcess(serverInterface);
        pManager.registerProcess(systemInterface);
    }

    private void makeConnections() {
        ConfigGroup cgroup = configManager.getGroup("connections");
        if (cgroup == null) return;
        ConfigEntry centry = cgroup.getEntry("connectto");
        if (centry != null) {
            /* Connect to the configured servers */
            int x, nParts = centry.getNParts();
            for (x = 0; x < nParts; x++) {
                int portNum = 3011;
                String data = centry.getPart(x);
                assert data != null;
                int index = data.indexOf(":");
                if (index != -1) {
                    String sportNum = data.substring(index + 1);
                    Scanner scanner = new Scanner(sportNum);
                    if (scanner.hasNextInt()) {
                        portNum = scanner.nextInt();
                    }
                    data = data.substring(0, index);
                }
                if (serverInterface.addUser(data, portNum, null) != 1) {
                    LOGGER.error(String.format("[BetterFSD]: Connection to %s port %d failed!", data, portNum));
                } else {
                    LOGGER.error(String.format("[BetterFSD]: Connected to %s port %d", data, portNum));
                }
            }
        }


        centry = cgroup.getEntry("allowfrom");
        if (centry != null) {
            /* Allow the configured servers */
            int nParts = centry.getNParts();
            for (int x = 0; x < nParts; x++) {
                serverInterface.allow(centry.getPart(x));
            }
        } else {
            LOGGER.warn("[BetterFSD]: No 'allowfrom' found, allowing everybody on the server port");
        }

        serverInterface.sendReset();
    }
}