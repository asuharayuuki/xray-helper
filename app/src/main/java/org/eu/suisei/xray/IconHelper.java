package org.eu.suisei.xray;

import androidx.annotation.DrawableRes;

public final class IconHelper {

    public static final String[] AVAILABLE_ICONS = {
            "shield", "power", "bug", "terminal", "key", "wifi", "bolt", "lock", "refresh", "settings", "mic", "coffee"
    };

    private IconHelper() {}

    @DrawableRes
    public static int getDrawableRes(String iconName) {
        if (iconName == null) return R.drawable.ic_shield;
        switch (iconName.toLowerCase()) {
            case "power":
                return R.drawable.ic_power;
            case "bug":
                return R.drawable.ic_bug;
            case "terminal":
                return R.drawable.ic_terminal;
            case "key":
                return R.drawable.ic_vpn_key;
            case "wifi":
                return R.drawable.ic_wifi;
            case "bolt":
                return R.drawable.ic_bolt;
            case "lock":
                return R.drawable.ic_lock;
            case "refresh":
                return R.drawable.ic_refresh;
            case "settings":
                return R.drawable.ic_settings;
            case "mic":
                return R.drawable.ic_mic;
            case "coffee":
                return R.drawable.local_cafe_outline;
            case "shield":
            default:
                return R.drawable.ic_shield;
        }
    }
}
