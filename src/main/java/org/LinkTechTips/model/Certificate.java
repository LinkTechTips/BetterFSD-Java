/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.model;

import org.LinkTechTips.constants.GlobalConstants;
import org.LinkTechTips.support.Support;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Certificate {
    public static final List<Certificate> certs = new ArrayList<>();

    private String cid, password, origin;

    private int level, liveCheck;

    private long prevVisit, creation;

    public Certificate(String c, String p, int l, long crea, String o) {
        certs.add(this);
        liveCheck = 1;
        cid = c;
        password = p;
        level = l;
        creation = crea;
        origin = o;
        if (level > GlobalConstants.LEV_MAX) {
            level = GlobalConstants.LEV_MAX;
        }
        prevVisit = 0;
    }

    public void close() {
        certs.remove(this);
    }

    public void configure(@Nullable String pwd, int l, long c, @Nullable String o) {
        level = l;
        if (pwd != null) {
            password = pwd;
        }
        if (o != null) {
            origin = o;
        }
        creation = c;
    }

    public static int maxLevel(String id, String p, int[] max /* pass by ref */) {
        Certificate cert = getCert(id);
        if (cert == null) {
            max[0] = GlobalConstants.LEV_OBSPILOT;
            return 0;
        }
        if (cert.getPassword().equals(p)) {
            max[0] = cert.getLevel();
            cert.setPrevVisit(Support.mtime());
            return 1;
        }
        max[0] = GlobalConstants.LEV_OBSPILOT;
        return 0;
    }

    public static @Nullable Certificate getCert(String cid) {
        return certs.stream().filter(e -> e.getCid().equals(cid)).findFirst().orElse(null);
    }

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLiveCheck() {
        return liveCheck;
    }

    public void setLiveCheck(int liveCheck) {
        this.liveCheck = liveCheck;
    }

    public long getPrevVisit() {
        return prevVisit;
    }

    public void setPrevVisit(long prevVisit) {
        this.prevVisit = prevVisit;
    }

    public long getCreation() {
        return creation;
    }

    public void setCreation(long creation) {
        this.creation = creation;
    }
}
