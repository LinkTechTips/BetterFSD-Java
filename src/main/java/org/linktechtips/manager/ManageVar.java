/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.manager;

public class ManageVar {
    private int type;

    private String name;

    private ManageVarValue value;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ManageVarValue getValue() {
        return value;
    }

    public void setValue(ManageVarValue value) {
        this.value = value;
    }
}
