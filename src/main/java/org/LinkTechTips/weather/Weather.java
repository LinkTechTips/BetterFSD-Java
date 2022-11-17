/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.LinkTechTips.weather;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Weather {
    public static final List<WProfile> wProfiles = new ArrayList<>();

    public static @Nullable WProfile getWProfile(String name) {
        for (WProfile wProfile : wProfiles) {
            if (wProfile.getName().equals(name)) {
                return wProfile;
            }
        }

        return null;
    }
}
