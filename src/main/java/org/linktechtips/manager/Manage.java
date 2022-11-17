/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.manager;

import org.linktechtips.constants.ManageVarType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class Manage {

    public static Manage manager;

    private int nVars;

    private final @NotNull List<ManageVar> variables;

    public Manage() {
        nVars = 0;
        variables = new ArrayList<>();
    }

    public int addVar(String name, int type) {
        ManageVar var = new ManageVar();
        var.setType(type);
        var.setName(name);
        var.setValue(new ManageVarValue());
        ++nVars;
        variables.add(var);
        return variables.size() - 1;
    }

    public void delVar(int num) {
        if (variables.size() > num) {
            variables.remove(num);
        }
    }

    public void incVar(int num) {
        ManageVar var = variables.get(num);
        ManageVarValue value = var.getValue();
        value.setNumber(value.getNumber() + 1);
    }

    public void decVar(int num) {
        ManageVar var = variables.get(num);
        ManageVarValue value = var.getValue();
        value.setNumber(value.getNumber() - 1);
    }

    public void setVar(int num, int number) {
        ManageVar var = variables.get(num);
        ManageVarValue value = var.getValue();
        value.setNumber(number);
    }

    public void setVar(int num, String str) {
        ManageVar var = variables.get(num);
        ManageVarValue value = var.getValue();
        value.setString(str);
    }

    public void setVar(int num, long timeVal) {
        ManageVar var = variables.get(num);
        ManageVarValue value = var.getValue();
        value.setTimeVal(timeVal);
    }

    public @Nullable ManageVar getVar(int num) {
        if (num >= nVars) {
            return null;
        }
        return variables.get(num);
    }

    public int getNVars() {
        return nVars;
    }

    public @Nullable String sprintValue(int num) {
        if (num >= nVars || variables.get(num).getName() == null) {
            return null;
        }
        ManageVar var = variables.get(num);

        return switch (var.getType()) {
            case ManageVarType
                    .ATT_INT -> String.valueOf(var.getValue().getNumber());
            case ManageVarType.ATT_VARCHAR -> var.getValue().getString();
            case ManageVarType.ATT_DATE -> new Date(var.getValue().getTimeVal()).toString();
            default -> "";
        };
    }

    public int getVarNum(String name) {
        for (int i = 0; i < variables.size(); i++) {
            ManageVar variable = variables.get(i);
            if (Objects.equals(variable.getName(), name)) {
                return i;
            }
        }

        return -1;
    }
}
