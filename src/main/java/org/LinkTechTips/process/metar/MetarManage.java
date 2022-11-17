
/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.process.metar;

import org.LinkTechTips.Main;
import org.LinkTechTips.constants.FsdPath;
import org.LinkTechTips.constants.ManageVarType;
import org.LinkTechTips.constants.MetarSource;
import org.LinkTechTips.constants.ServerConstants;
import org.LinkTechTips.manager.Manage;
import org.LinkTechTips.model.Server;
import org.LinkTechTips.process.Process;
import org.LinkTechTips.process.config.ConfigEntry;
import org.LinkTechTips.process.config.ConfigGroup;
import org.LinkTechTips.support.Support;
import org.LinkTechTips.weather.WProfile;
import org.LinkTechTips.weather.Weather;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class MetarManage extends Process {

    private final static Logger LOGGER = LoggerFactory.getLogger(MetarManage.class);

    public static MetarManage metarManager;

    public static final int VAR_AMOUNT = 10;
    public static final int MAX_METAR_DOWNLOAD_TIME = 1600_000;

    private @Nullable BufferedWriter ioIn;

    private @Nullable BufferedReader ioOut;

    private @Nullable Socket sock;

    private @Nullable BufferedReader dataReadSock;

    private @Nullable BufferedWriter dataSock;

    private @Nullable Mmq rootQ;

    private long prevDownload;

    private long metarFileTime;

    private int nStations;

    private final @NotNull List<Station> stationList;

    private int metarSize;

    private boolean newFileReady;

    private final int varPrev;

    private final int varTotal;

    private final int varStations;

    private final int @NotNull [] variation;

    private String metarHost;

    private String metarDir;

    private String ftpEmail;

    private boolean passiveMode;

    private boolean downloading;

    private int source;

    public MetarManage() {
        passiveMode = true;
        source = MetarSource.SOURCE_NETWORK;
        rootQ = null;
        int prevHour = 1;
        nStations = 0;
        stationList = new ArrayList<>();
        variation = new int[VAR_AMOUNT];

        parseWeatherConfig();
        parseSystemConfig();

        if (source == MetarSource.SOURCE_DOWNLOAD) {
            if (metarHost == null) {
                metarHost = "weather.noaa.gov";
            }

            if (metarDir == null) {
                metarDir = "data/observations/metar/cycles/";
            }
        }

        int var = Manage.manager.addVar("metar.method", ManageVarType.ATT_VARCHAR);
        if (source == MetarSource.SOURCE_NETWORK) {
            Manage.manager.setVar(var, "network");
        } else if (source == MetarSource.SOURCE_FILE) {
            Manage.manager.setVar(var, "file");
        } else if (source == MetarSource.SOURCE_DOWNLOAD) {
            Manage.manager.setVar(var, "download");
        }

        varPrev = Manage.manager.addVar("metar.current", ManageVarType.ATT_DATE);
        varTotal = Manage.manager.addVar("metar.requests", ManageVarType.ATT_INT);
        varStations = Manage.manager.addVar("metar.stations", ManageVarType.ATT_INT);

        Manage.manager.setVar(varPrev, Support.mtime());

        if (source == MetarSource.SOURCE_FILE) {
            buildList();
        }
    }

    private void parseWeatherConfig() {
        ConfigGroup weatherGroup = Main.configManager.getGroup("weather");
        ConfigEntry sourceEntry = null, serverEntry = null, dirEntry = null, ftpModeEntry = null;
        if (weatherGroup != null) {
            sourceEntry = weatherGroup.getEntry("source");
            serverEntry = weatherGroup.getEntry("server");
            dirEntry = weatherGroup.getEntry("dir");
            ftpModeEntry = weatherGroup.getEntry("ftpmode");
        }

        if (sourceEntry != null) {
            String source = sourceEntry.getData();
            if ("network".equals(source)) {
                this.source = MetarSource.SOURCE_NETWORK;
            } else if ("file".equals(source)) {
                this.source = MetarSource.SOURCE_FILE;
            } else if ("download".equals(source)) {
                this.source = MetarSource.SOURCE_DOWNLOAD;
            } else {
                LOGGER.error(String.format("[METAR]: Unknown METAR source %s in config file", source));
            }
        }

        if (serverEntry != null) {
            metarHost = serverEntry.getData();
        }

        if (dirEntry != null) {
            metarDir = dirEntry.getData();
        }

        if (ftpModeEntry != null) {
            String mode = ftpModeEntry.getData();
            passiveMode = "passive".equals(mode);
        }
    }

    private void parseSystemConfig() {
        ConfigGroup systemGroup = Main.configManager.getGroup("system");
        ConfigEntry emailEntry = null;
        if (systemGroup != null) {
            emailEntry = systemGroup.getEntry("email");
        }

        if (emailEntry != null) {
            ftpEmail = emailEntry.getData();
        }
    }

    private void buildList() {
        Path file = Paths.get(FsdPath.METARFILE);
        if (Files.notExists(file)) {
            return;
        }
        try {
            BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
            metarFileTime = attr.lastModifiedTime().toMillis();
        } catch (IOException e) {
            return;
        }

        nStations = 0;
        stationList.clear();
        parseMetar();
    }

    private void parseMetar() {
        File file = new File(FsdPath.METARFILE);
        if (!file.isFile() || !file.exists()) {
            return;
        }
        try (InputStreamReader read = new InputStreamReader(new FileInputStream(file));
                BufferedReader bufferedReader = new BufferedReader(read)) {
            String line;
            List<String> arr = new ArrayList<>();
            for (long offset = 0; (line = bufferedReader.readLine()) != null; offset += line.length()) {
                if (line.length() < 30) {
                    continue;
                }

                int count = Support.breakArgs(line, arr, 3);
                if (count < 3) {
                    continue;
                }
                if (line.startsWith("     ")) {
                    continue;
                }
                String stationName = null;
                if (arr.get(0).length() == 4) {
                    stationName = arr.get(0);
                } else if (arr.get(1).length() == 4) {
                    stationName = arr.get(1);
                }
                if (stationName == null) {
                    continue;
                }
                Station station = new Station();
                station.setName(stationName);
                station.setLocation(offset);
                stationList.add(station);
                nStations++;
            }
            stationList.sort(Comparator.comparing(Station::getName));
            Manage.manager.setVar(varStations, nStations);
        } catch (FileNotFoundException e) {
            LOGGER.error("[METAR]: Config file not found: " + FsdPath.METARFILE);
        } catch (IOException e) {
            LOGGER.error("[METAR]: Something went wrong when parse metar file: ", e);
        }
    }

    private void setupNewFile() {
        newFileReady = false;
        File metarFile = new File(FsdPath.METARFILE);
        File metarNewFile = new File(FsdPath.METARFILENEW);
        if (metarSize < 10_0000) {
            metarNewFile.delete();
            LOGGER.warn(String.format("[METAR]: Size of new METAR file (%d) is too small, dropping.", metarSize));
        }

        if (!metarNewFile.renameTo(metarFile)) {
            LOGGER.warn(String.format("[METAR]: Can't move %s to %s", FsdPath.METARFILENEW, FsdPath.METARFILE));
        } else {
            LOGGER.info("[METAR]: Installed new METAR data.");
        }
        buildList();
        Manage.manager.setVar(varPrev, Support.mtime());
    }

    @Override
    public boolean run() {
        if (source == MetarSource.SOURCE_NETWORK) {
            return true;
        }

        if (downloading) {
            doDownload();
        }

        while (rootQ != null) {
            if (doParse(rootQ) == 0) {
                break;
            }
        }

        if (!downloading && newFileReady) {
            setupNewFile();
        }

        return true;
    }

    public void startDownload() {
        if (downloading) {
            LOGGER.error("[METAR]: server seems to be still loading");
            return;
        }

        prevDownload = Support.mtime();
        try {
            File metarFileNew = new File(FsdPath.METARFILENEW);
            if (!metarFileNew.exists()) {
                metarFileNew.createNewFile();
            }
            ioIn = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(metarFileNew)));
        } catch (IOException e) {
            LOGGER.error("[METAR]: Open metarnew.txt failed.");
            return;
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(metarHost);
        } catch (UnknownHostException e) {
            LOGGER.error(String.format("[METAR]: Could not lookup METAR host name %s.", metarHost));
            stopDownload();
            return;
        }

        try {
            sock = new Socket(address, 21);
            dataReadSock = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            dataSock = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
        } catch (IOException e) {
            LOGGER.error("[METAR]: Could not connect to ftp port on METAR host");
            stopDownload();
            return;
        }

        try {
            if (passiveMode) {
                String data = String.format("USER anonymous\nPASS %s\nCWD %s\nPASV\n", ftpEmail, metarDir);
                dataSock.write(data);
                downloading = true;
                metarSize = 0;
                dataReadSock.close();
                dataReadSock = null;
            } else {
                String url = "127.0.0.1";
                int dataPort = (int) (Math.random() * 100000 % 9999) + 1024;
                String port = String.format("127,0,0,1,%d", dataPort);
                String data = String.format("USER anonymous\nPASS %s\nCWD %s\nPORT %s\nRETR %02dZ.TXT\n",
                        ftpEmail, metarDir, port, (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 21) % 24);
                dataSock.write(data);
                LOGGER.info("[METAR]: Starting download of METAR data");
                downloading = true;
                metarSize = 0;
                dataReadSock.close();
                dataReadSock = null;
            }
        } catch (IOException e) {
            LOGGER.error("[METAR]: Socket operation failed.");
            stopDownload();
        }
    }

    private void doDownload() {
        long now = Support.mtime();
        if (now - prevDownload > MAX_METAR_DOWNLOAD_TIME) {
            LOGGER.warn("[METAR]: METAR download interrupted due to timeout");
            stopDownload();
            startDownload();
            return;
        }
        try {
            if (Objects.requireNonNull(dataReadSock).ready()) {
                char[] buf = new char[4096];
                int bytes = dataReadSock.read(buf);
                if (bytes <= 0) {
                    stopDownload();
                    newFileReady = true;
                } else if (passiveMode) {
                    String response = new String(buf);
                    String passHost;
                    int passPort;
                    if (!response.startsWith("2271 ")) {
                        LOGGER.error(String.format("[METAR]: Could not request passive mode: %s", response));
                        stopDownload();
                        return;
                    }
                    int opening = response.indexOf('(');
                    int closing = response.indexOf(')', opening + 1);
                    if (closing > 0) {
                        String dataLink = response.substring(opening + 1, closing);
                        StringTokenizer tokenizer = new StringTokenizer(dataLink, ",");
                        try {
                            passHost = tokenizer.nextToken() + "." + tokenizer.nextToken() + "."
                                    + tokenizer.nextToken() + "." + tokenizer.nextToken();
                            passPort = NumberUtils.toInt(tokenizer.nextToken()) * 256
                                    + NumberUtils.toInt(tokenizer.nextToken());
                        } catch (Exception e) {
                            LOGGER.error(
                                    "[METAR]: Received bad data link information: "
                                            + response);
                            stopDownload();
                            return;
                        }
                        Socket dataSocket;
                        try {
                            dataSocket = new Socket(passHost, passPort);
                            dataReadSock = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
                            String data = String.format("RETR %02dZ.TXT\n", (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 21) % 24);
                            Objects.requireNonNull(dataSock).write(data);
                            LOGGER.info("[METAR]: Starting download of METAR data");
                        } catch (IOException e) {
                            LOGGER.error("[METAR]: Could not connect to ftp port on METAR host");
                            stopDownload();
                            return;
                        }
                    }
                }
                try {
                    String data = dataReadSock.readLine();
                    metarSize = data.length();
                    Files.writeString(Paths.get(FsdPath.METARFILENEW), data);
                } catch (IOException e) {
                    LOGGER.error("[METAR]: Download METAR data error.");
                    stopDownload();
                    return;
                }
                stopDownload();
                newFileReady = true;
            }
        } catch (IOException e) {
            LOGGER.error("[METAR]: Start to download of METAR data failed.");
            stopDownload();
        }
    }

    public void stopDownload() {
        try {
            if (dataReadSock != null) {
                dataReadSock.close();
                dataReadSock = null;
            }

            if (dataSock != null) {
                dataSock.close();
                dataSock = null;
            }

            if (sock != null) {
                sock.close();
                sock = null;
            }
            ioIn = null;
            downloading = false;
        } catch (IOException e) {
            LOGGER.error("[METAR]: Close socket failed.");
        }
    }

    private void checkMetarFile() {
        try {
            Path file = Paths.get(FsdPath.METARFILE);
            BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
            long modifyTime = attr.lastModifiedTime().toMillis();
            if (modifyTime != metarFileTime) {
                buildList();
            }
        } catch (IOException e) {
            LOGGER.error("[METAR]: Check metar file error.");
        }
    }

    private int doParse(@NotNull Mmq q) {
        Station key = new Station();
        checkMetarFile();
        key.setName(StringUtils.substring(q.getMetarId(), 0, 4).toUpperCase());
        int index = Collections.binarySearch(stationList, key, Comparator.comparing(Station::getName));
        if (index < 0) {
            Main.serverInterface.sendNoWx(q.getDestination(), q.getFd(), q.getMetarId());
            delQ(q);
            return 1;
        }
        Station location = stationList.get(index);

        try {
            ioOut = new BufferedReader(new InputStreamReader(new FileInputStream(FsdPath.METARFILE)));
        } catch (IOException e) {
            LOGGER.error("[METAR]: Open metar.txt error. ");
            return 0;
        }

        String line;

        try {
            ioOut.skip(location.getLocation());
            boolean first = true;
            StringBuilder sb = new StringBuilder();
            do {
                String piece = getLine();
                if (piece == null) {
                    break;
                }
                boolean addition = piece.startsWith("     ");
                if (addition && first) {
                    break;
                }
                if (!addition && !first) {
                    break;
                }
                first = false;
                if (addition) {
                    sb.append(piece.substring(4));
                } else {
                    sb.append(piece);
                }
            } while (true);
            line = sb.toString();
            ioOut.close();
            ioOut = null;
        } catch (IOException e) {
            LOGGER.error("[METAR]: Read metar.txt error.");
            return 0;
        }

        if (q.isParsed() == 1) {
            List<String> arr = new ArrayList<>();
            int count = Support.breakArgs(line, arr, 100);
            WProfile profile = new WProfile(location.getName(), 0, null);
            profile.parseMetar(arr.toArray(new String[0]), count);
            Main.serverInterface.sendWeather(q.getDestination(), q.getFd(), profile);
        } else {
            Main.serverInterface.sendMetar(q.getDestination(), q.getFd(), location.getName(), line);
        }

        Manage.manager.incVar(varTotal);
        delQ(q);
        return 1;
    }

    private @Nullable String getLine() throws IOException {
        String line = Objects.requireNonNull(ioOut).readLine();
        if (line == null) {
            return null;
        }
        return prepareLine(line);
    }

    private String prepareLine(String line) {
        return StringUtils.stripEnd(line, "=\r\n");
    }

    public @Nullable Server requestMetar(@NotNull String client, String metar, int parsed, int fd) {
        if (source == MetarSource.SOURCE_NETWORK) {
            int hops = -1;
            Server best = null;
            for (Server tempServer : Server.servers) {
                if (tempServer != Server.myServer && ((tempServer.getFlags() & ServerConstants.SERVER_METAR) != 0)) {
                    if (hops == -1 || (tempServer.getHops() < hops && tempServer.getHops() != -1)) {
                        best = tempServer;
                        hops = tempServer.getHops();
                    }
                }
            }

            if (best == null) {
                return null;
            }
            Main.serverInterface.sendReqMetar(client, metar, fd, parsed, best);
            return best;
        } else {
            addQ(client, metar, parsed, fd);
        }
        return Server.myServer;
    }

    private void addQ(@NotNull String dest, String metar, int parsed, int fd) {
        WProfile prof = Weather.getWProfile(metar);
        if (prof != null && prof.getActive() == 1) {
            if (parsed == 1) {
                Main.serverInterface.sendWeather(dest, fd, prof);
            } else {
                Main.serverInterface.sendMetar(dest, fd, metar, prof.getRawCode());
            }
            return;
        }
        Mmq temp = new Mmq();
        temp.setDestination(dest);
        temp.setMetarId(metar);
        temp.setFd(fd);
        temp.setParsed(parsed);
        temp.setNext(rootQ);
        temp.setPrev(null);
        if (temp.getNext() != null) {
            rootQ = temp;
        }
    }

    private void delQ(@NotNull Mmq p) {
        if (p.getNext() != null) {
            p.getNext().setPrev(p.getPrev());
        }
        if (p.getPrev() != null) {
            p.getPrev().setNext(p.getNext());
        } else {
            rootQ = p.getNext();
        }
    }

    public int getVariation(int num, int min, int max) {
        int val = variation[num], range = max - min + 1;
        val = (Math.abs(val) % range) + min;
        return val;
    }

    public boolean isDownloading() {
        return downloading;
    }

    public void setDownloading(boolean downloading) {
        this.downloading = downloading;
    }

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }
}
