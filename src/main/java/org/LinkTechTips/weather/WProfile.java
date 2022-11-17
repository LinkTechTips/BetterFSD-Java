
/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.weather;

import org.LinkTechTips.constants.WeatherConstants;
import org.LinkTechTips.process.metar.MetarManage;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WProfile {
    private String name;
    private String origin;
    private String rawCode;
    private List<WindLayer> winds;
    private List<CloudLayer> clouds;
    private List<TempLayer> temps;
    private CloudLayer tstorm;
    private int barometer;
    private float visibility;
    private int dewPoint;
    private long creation;
    private long version;
    private int active;
    public WProfile(String name, long creation, String origin) {
        this.creation = creation;
        active = 0;
        this.origin = origin;
        winds = new ArrayList<>(4);
        clouds = new ArrayList<>(2);
        temps = new ArrayList<>(4);

        winds.add(new WindLayer(-1, -1));
        winds.add(new WindLayer(10400, 2500));
        winds.add(new WindLayer(22600, 10400));
        winds.add(new WindLayer(90000, 22700));

        temps.add(new TempLayer(100));
        temps.add(new TempLayer(10000));
        temps.add(new TempLayer(18000));
        temps.add(new TempLayer(35000));

        dewPoint = 0;
        for (int i = 0; i < 2; i++) {
            clouds.add(new CloudLayer(-1, -1));
        }
        tstorm = new CloudLayer(-1, -1);

        visibility = 15.0f;
        barometer = 2950;

        this.name = name;
    }

    public void close() {
        Weather.wProfiles.remove(this);
    }

    public void activate() {
        active = 1;
    }

    public void parseMetar(String @NotNull [] array, int count) {
        int index = 0;
        String station;

        /* First field could be 'METAR' */
        if (index == array.length || StringUtils.isBlank(array[index])) {
            return;
        }
        if (StringUtils.equalsIgnoreCase(array[index], "metar")) {
            index++;
        }

        /* The station field */
        if (index >= array.length || StringUtils.isBlank(array[index])) {
            return;
        }
        station = array[index++];

        /* date and time */
        if (index >= array.length || StringUtils.isBlank(array[index])) {
            return;
        }
        if (array[index].endsWith("Z")) {
            index++;
        }

        /* Here there could be 'AUTO' or 'COR' */
        if (index >= array.length || StringUtils.isBlank(array[index])) {
            return;
        }
        if (StringUtils.equalsIgnoreCase(array[index], "auto")) {
            index++;
        } else if (StringUtils.equalsIgnoreCase(array[index], "cor")) {
            index++;
        }

        /* Wind speed and direction */
        index += parseWind(ArrayUtils.subarray(array, index, array.length), count - index);

        /* Visibility */
        index += parseVis(ArrayUtils.subarray(array, index, array.length), count - index);

        /* Runway visual range */
        index += parseRvr(ArrayUtils.subarray(array, index, array.length), count - index);

        /* Weather phenomena */
        index += parseWx(ArrayUtils.subarray(array, index, array.length), count - index);

        /* Sky conditions */
        index += parseSky(ArrayUtils.subarray(array, index, array.length), count - index);

        /* Temperature */
        index += parseTemp(ArrayUtils.subarray(array, index, array.length), count - index);

        /* Barometer */
        index += parseAlt(ArrayUtils.subarray(array, index, array.length), count - index);

        /* Use dewpoint to fix visibility */
        fixVisibility();
    }

    public void loadArray(String @NotNull [] array, int count) {
        int x;
        int index = 0;
        barometer = NumberUtils.toInt(array[index++]);
        visibility = NumberUtils.toInt(array[index++]);

        /* load cloud data */
        for (x = 0; x < 2; x++) {
            CloudLayer layer = clouds.get(x);
            layer.setCeiling(NumberUtils.toInt(array[index++]));
            layer.setFloor(NumberUtils.toInt(array[index++]));
            layer.setCoverage(NumberUtils.toInt(array[index++]));
            layer.setIcing(NumberUtils.toInt(array[index++]));
            layer.setTurbulence(NumberUtils.toInt(array[index++]));
        }

        /* load thunderstorm data */
        tstorm.setCeiling(NumberUtils.toInt(array[index++]));
        tstorm.setFloor(NumberUtils.toInt(array[index++]));
        tstorm.setCoverage(NumberUtils.toInt(array[index++]));
        tstorm.setIcing(NumberUtils.toInt(array[index++]));
        tstorm.setTurbulence(NumberUtils.toInt(array[index++]));

        /* load wind data */
        for (x = 0; x < 4; x++) {
            WindLayer layer = winds.get(x);
            layer.setCeiling(NumberUtils.toInt(array[index++]));
            layer.setFloor(NumberUtils.toInt(array[index++]));
            layer.setDirection(NumberUtils.toInt(array[index++]));
            layer.setSpeed(NumberUtils.toInt(array[index++]));
            layer.setGusting(NumberUtils.toInt(array[index++]));
            layer.setTurbulence(NumberUtils.toInt(array[index++]));
        }

        /* load temp data */
        for (x = 0; x < 4; x++) {
            TempLayer layer = temps.get(x);
            layer.setCeiling(NumberUtils.toInt(array[index++]));
            layer.setTemp(NumberUtils.toInt(array[index++]));
        }
    }

    public @NotNull String print() {
        int x;
        StringBuilder data;
        String piece;
        data = new StringBuilder(String.format("%d:%.2f:", barometer, visibility));
        /* Add cloud data */
        for (x = 0; x < 2; x++) {
            CloudLayer c = clouds.get(x);
            piece = String.format("%d:%d:%d:%d:%d:", c.getCeiling(), c.getFloor(), c.getCoverage(), c.getIcing(),
                    c.getTurbulence());
            data.append(piece);
        }
        /* Add thunderstorm data */
        CloudLayer c = tstorm;
        piece = String.format("%d:%d:%d:%d:%d:", c.getCeiling(), c.getFloor(), c.getCoverage(), c.getIcing(),
                c.getTurbulence());
        data.append(piece);
        /* Add wind data */
        for (x = 0; x < 4; x++) {
            WindLayer w = winds.get(x);
            piece = String.format("%d:%d:%d:%d:%d:%d:", w.getCeiling(), w.getFloor(), w.getDirection(), w.getSpeed(),
                    w.getGusting(), w.getTurbulence());
            data.append(piece);
        }
        /* Add temp data */
        for (x = 0; x < 4; x++) {
            TempLayer t = temps.get(x);
            piece = String.format("%d:%d:", t.getCeiling(), t.getTemp());
            data.append(piece);
        }

        return data.toString();
    }

    private int parseWind(String[] array, int count) {
        if (count == 0) {
            return 0;
        }
        if (array[0].length() < 3) {
            return 0;
        }
        if (!array[0].endsWith("kt") && !array[0].endsWith("mps")) {
            return 0;
        }
        String wind = array[0];
        String gusting = null;
        if (array[0].contains("G")) {
            winds.get(0).setGusting(1);
            String[] split = array[0].split("G");
            wind = split[0];
            gusting = split[1].replace("kt", "").replace("mps", "");
        }
        winds.get(0).setSpeed(NumberUtils.toInt(wind.substring(3)));
        winds.get(0).setCeiling(2500);
        winds.get(0).setFloor(0);
        winds.get(0).setDirection(NumberUtils.toInt(wind.substring(0, 3)));

        if (count == 1) {
            return 1;
        }

        if (array[1].contains("V")) {
            // TODO unused?
            return 2;
        }
        return 1;
    }

    private int parseVis(String[] array, int count) {
        if (count == 0) {
            return 0;
        }

        visibility = 10.00f;

        if (StringUtils.equalsIgnoreCase(array[0], "M1/4SM")) {
            visibility = 0.15f;
            return 1;
        }

        if (StringUtils.equalsIgnoreCase(array[0], "1/4SM")) {
            visibility = 0.25f;
            return 1;
        }

        if (StringUtils.equalsIgnoreCase(array[0], "1/2SM")) {
            visibility = 0.50f;
            return 1;
        }

        if (StringUtils.equalsIgnoreCase(array[0], "CAVOK") || StringUtils.equals(array[0], "////")
                || StringUtils.equals(array[0], "CLR")) {
            visibility = 15.00f;
            CloudLayer cloudLayer = clouds.get(1);
            cloudLayer.setCeiling(26000);
            cloudLayer.setFloor(24000);
            cloudLayer.setIcing(0);
            cloudLayer.setTurbulence(0);
            cloudLayer.setCoverage(1);
            return 1;
        }

        Scanner scanner = new Scanner(array[0]);
        visibility = scanner.nextFloat();
        if (array[0].contains("SM")) {
            return 1;
        }
        if (count > 1 && array[1].contains("SM")) {
            return 2;
        }
        if (array[0].contains("KM")) {
            visibility /= 1.609;
            return 1;
        }
        if (count > 1 && array[1].contains("KM")) {
            visibility /= 1.609;
            return 2;
        }
        if (visibility == 9999) {
            visibility = 15.00f;
        } else {
            visibility /= 1609.0f;
        }

        return 1;
    }

    private int parseWx(String[] array, int count) {
        int amount = 0, i;
        String[] patterns = { "+", "-", "VC", "MI", "BL", "PR", "SH", "BC", "TS", "DR", "FZ", "DZ", "OC", "UP", "RA",
                "PE", "SN", "GR", "SG", "GS", "BR", "DU", "FG", "SA", "FU", "HZ", "VA", "PY", "PO", "DS", "SQ", "FC",
                "SS", "PLUS" };
        while (amount != count) {
            for (i = 0; i < patterns.length; i++) {
                if (array[amount].startsWith(patterns[i])) {
                    break;
                }
            }
            amount++;
        }

        return amount;
    }

    int parseSky(String[] array, int count) {
        int amount = 0, i;
        String[] patterns = { "SKC", "CLR", "VV", "FEW", "SCT", "BKN", "OVC" };
        int[] coverage = { 0, 0, 8, 1, 3, 5, 8, 0 };
        while (amount != count) {
            for (i = 0; i < patterns.length; i++) {
                if (array[amount].startsWith(patterns[i])) {
                    break;
                }
            }
            if (amount < 2) {
                int base;
                String baseString = array[amount].substring(patterns[i].length());
                Scanner scanner = new Scanner(baseString);
                if (scanner.hasNextInt()) {
                    base = scanner.nextInt();
                } else {
                    base = 10;
                }
                base *= 100;
                clouds.get(amount).setCoverage(coverage[i]);
                clouds.get(amount).setFloor(base);
            }
            amount++;
        }

        if (amount == 1) {
            clouds.get(0).setCeiling(clouds.get(0).getFloor() + 3000);
            clouds.get(0).setTurbulence(17);
        } else if (amount > 1) {
            if (clouds.get(1).getFloor() > clouds.get(0).getFloor()) {
                clouds.get(0).setCeiling(
                        clouds.get(0).getFloor() + (clouds.get(1).getFloor() - clouds.get(0).getFloor()) / 2);
                clouds.get(1).setCeiling(clouds.get(1).getFloor() + 3000);
            } else {
                clouds.get(1).setCeiling(
                        clouds.get(1).getFloor() + (clouds.get(0).getFloor() - clouds.get(1).getFloor()) / 2);
                clouds.get(0).setCeiling(clouds.get(0).getFloor() + 3000);
            }
            clouds.get(0).setTurbulence((clouds.get(0).getCeiling() - clouds.get(0).getFloor()) / 175);
            clouds.get(1).setTurbulence((clouds.get(1).getCeiling() - clouds.get(1).getFloor()) / 175);
        }

        return amount;
    }

    private int parseTemp(String[] array, int count) {
        if (count == 0) {
            return 0;
        }

        int p = array[0].indexOf('/');
        if (p == -1) {
            return 0;
        }
        String[] split = array[0].split("/");
        if (split[0].startsWith("M")) {
            temps.get(0).setTemp(-(NumberUtils.toInt(split[0].substring(1))));
        } else {
            temps.get(0).setTemp(NumberUtils.toInt(split[0]));
        }
        temps.get(0).setCeiling(100);
        if (split[1].startsWith("M")) {
            dewPoint = -(NumberUtils.toInt(split[0].substring(1)));
        } else {
            dewPoint = NumberUtils.toInt(split[0]);
        }

        if (temps.get(0).getTemp() > -10 && temps.get(0).getTemp() < 10) {
            if (clouds.get(0).getCeiling() < 12000) {
                clouds.get(0).setIcing(1);
            }
            if (clouds.get(1).getCeiling() < 12000) {
                clouds.get(1).setIcing(1);
            }
        }

        return 1;
    }

    private int parseRvr(String[] array, int count) {
        int amount = 0;
        while (amount < count && array[amount].contains("/")) {
            amount++;
        }
        return amount;
    }

    private int parseAlt(String[] array, int count) {
        if (count == 0) {
            return 0;
        }
        barometer = 2992;
        if (array[0].charAt(0) == 'A') {
            barometer = NumberUtils.toInt(array[0].substring(1));
        } else if (array[0].charAt(0) == 'Q') {
            barometer = NumberUtils.toInt(array[0].substring(1));
            barometer = (int) Math.round((barometer * 100.0 * 100.0) / (1333.0 * 2.54));
        } else {
            return 0;
        }
        return 1;
    }

    private void fixVisibility() {
        if (visibility >= 10.00f) {
            visibility += temps.get(0).getTemp() - dewPoint - 10;
            if (visibility <= 30.00f) {
                if ((int) visibility % 5 > 2) {
                    visibility += 5;
                }
                visibility -= (int) visibility % 5;
            } else {
                if ((int) visibility % 10 > 5) {
                    visibility += 10;
                }
                visibility -= (int) visibility % 10;
            }
        }
    }

    public @Nullable WProfile getWProfile(String name) {
        return Weather.getWProfile(name);
    }

    public int getSeason(double lat) {
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH);
        int season = switch (month) {
            case 11, 0, 1 -> 0;
            case 5, 6, 7 -> 2;
            case 2, 3, 4 -> 1;
            case 8, 9, 10 -> 3; // fixed a bug
            default -> 1;
        };
        if (lat < 0) {
            if (season == 0) {
                season = 2;
            } else if (season == 2) {
                season = 0;
            }
        }
        return season;
    }

    public void fix(double lat, double lon) {
        double a2 = Math.abs(lon / 18.0);
        int season = getSeason(lat);
        int coriolisVar;
        int latVar = MetarManage.metarManager.getVariation(WeatherConstants.VAR_UPDIRECTION, -25, 25);
        if (lat > 0) {
            winds.get(3).setDirection((int) Math.round(6 * lat + latVar + a2));
        } else {
            winds.get(3).setDirection((int) Math.round(-6 * lat + latVar + a2));
        }
        winds.get(3).setDirection((winds.get(3).getDirection() + 360) % 360);

        int maxVelocity = switch (getSeason(lat)) {
            case 0 -> 120;
            case 1, 3 -> 80;
            case 2 -> 50;
            default -> 1;
        };
        winds.get(3).setSpeed((int) Math.round(Math.abs(Math.sin(lat * Math.PI / 180.0f)) * maxVelocity));

        latVar = MetarManage.metarManager.getVariation(WeatherConstants.VAR_MIDDIRECTION, 10, 45);
        coriolisVar = MetarManage.metarManager.getVariation(WeatherConstants.VAR_MIDCOR, 10, 30);
        if (lat > 0) {
            winds.get(2).setDirection((int) Math.round(6 * lat + latVar + a2 - coriolisVar));
        } else {
            winds.get(2).setDirection((int) Math.round(-6 * lat + latVar + a2 - coriolisVar));
        }
        winds.get(2).setDirection((winds.get(2).getDirection() + 360) % 360);
        winds.get(2).setSpeed((int) (winds.get(3).getSpeed()
                * (MetarManage.metarManager.getVariation(WeatherConstants.VAR_MIDSPEED, 500, 800) / 1000.0f)));

        int coriolisVarLow = coriolisVar + MetarManage.metarManager.getVariation(WeatherConstants.VAR_LOWCOR, 10, 30);
        latVar = MetarManage.metarManager.getVariation(WeatherConstants.VAR_LOWDIRECTION, 10, 45);
        if (lat > 0) {
            winds.get(1).setDirection((int) Math.round(6 * lat + latVar + a2 - coriolisVarLow));
        } else {
            winds.get(1).setDirection((int) Math.round(-6 * lat + latVar + a2 - coriolisVar));
        }
        winds.get(1).setDirection((winds.get(1).getDirection() + 360) % 360);

        winds.get(1).setSpeed((winds.get(0).getSpeed() + winds.get(1).getSpeed()) / 2);

        temps.get(3).setTemp(-57 + MetarManage.metarManager.getVariation(WeatherConstants.VAR_UPTEMP, -4, 4));
        temps.get(2).setTemp(-21 + MetarManage.metarManager.getVariation(WeatherConstants.VAR_MIDTEMP, -7, 7));
        temps.get(1).setTemp(-5 + MetarManage.metarManager.getVariation(WeatherConstants.VAR_LOWTEMP, -12, 12));
    }

    public void genRawCode() {
        StringBuilder data;
        String piece;

        /* Station */
        data = new StringBuilder(name);

        /* Zulu time */
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        piece = String.format("%02d%02d%02dZ ", calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
        data.append(piece);

        /* Winds */
        piece = String.format("%03d%02dKT ", winds.get(0).getDirection(), winds.get(0).getSpeed());
        data.append(piece);

        /* Visibility */
        piece = String.format("%02dSM ", (int) visibility);
        data.append(piece);

        /* Clouds */
        int x;
        for (x = 0; x < 2; x++) {
            if (clouds.get(x).getCeiling() != -1) {
                int c = clouds.get(0).getCoverage();
                piece = String.format("%s%03d ",
                        c == 0 ? "CLR"
                                : (c == 1 || c == 2) ? "FEW"
                                        : (c == 3 || c == 4) ? "SCT" : (c == 5 || c == 6) ? "BKN" : "OVC",
                        clouds.get(0).getFloor() / 100);
                data.append(piece);
            }
        }

        /* Temp */
        int temperature = temps.get(0).getTemp();
        int dew = visibility < 5 ? temperature - 1 : temperature + 1;
        piece = String.format("%s%02d/%s%02d ", temperature < 0 ? "M" : "", Math.abs(temperature), dew < 0 ? "M" : "",
                Math.abs(dew));
        data.append(piece);

        /* QNH */
        piece = String.format("A%04d", barometer);
        data.append(piece);
        rawCode = data.toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getRawCode() {
        return rawCode;
    }

    public void setRawCode(String rawCode) {
        this.rawCode = rawCode;
    }

    public List<WindLayer> getWinds() {
        return winds;
    }

    public void setWinds(List<WindLayer> winds) {
        this.winds = winds;
    }

    public List<CloudLayer> getClouds() {
        return clouds;
    }

    public void setClouds(List<CloudLayer> clouds) {
        this.clouds = clouds;
    }

    public List<TempLayer> getTemps() {
        return temps;
    }

    public void setTemps(List<TempLayer> temps) {
        this.temps = temps;
    }

    public CloudLayer getTstorm() {
        return tstorm;
    }

    public void setTstorm(CloudLayer tstorm) {
        this.tstorm = tstorm;
    }

    public int getBarometer() {
        return barometer;
    }

    public void setBarometer(int barometer) {
        this.barometer = barometer;
    }

    public float getVisibility() {
        return visibility;
    }

    public void setVisibility(float visibility) {
        this.visibility = visibility;
    }

    public int getDewPoint() {
        return dewPoint;
    }

    public void setDewPoint(int dewPoint) {
        this.dewPoint = dewPoint;
    }

    public long getCreation() {
        return creation;
    }

    public void setCreation(long creation) {
        this.creation = creation;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public int getActive() {
        return active;
    }

    public void setActive(int active) {
        this.active = active;
    }
}
