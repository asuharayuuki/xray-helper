package org.eu.suisei.xray;

import androidx.annotation.DrawableRes;

public final class IconHelper {

    public static final String[] AVAILABLE_ICONS = {
            "shield", "security", "power", "terminal", "code", "bug", "build",
            "vpn_key", "wifi", "dns", "globe", "router", "cloud", "link",
            "speed", "sync", "download", "upload", "storage", "memory",
            "lock", "fingerprint", "visibility", "visibility_off",
            "tune", "settings", "refresh", "history", "timer", "analytics",
            "folder", "bell", "airplane", "rocket", "star", "heart",
            "android", "coffee", "mic", "check"
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
            case "vpn_key":
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
            case "dns":
                return R.drawable.ic_dns;
            case "globe":
            case "public":
                return R.drawable.ic_globe;
            case "router":
                return R.drawable.ic_router;
            case "cloud":
                return R.drawable.ic_cloud;
            case "speed":
                return R.drawable.ic_speed;
            case "sync":
                return R.drawable.ic_sync;
            case "download":
                return R.drawable.ic_download;
            case "upload":
                return R.drawable.ic_upload;
            case "fingerprint":
                return R.drawable.ic_fingerprint;
            case "visibility":
            case "eye":
                return R.drawable.ic_visibility;
            case "visibility_off":
            case "eye_off":
                return R.drawable.ic_visibility_off;
            case "code":
                return R.drawable.ic_code;
            case "build":
            case "wrench":
                return R.drawable.ic_build;
            case "memory":
            case "cpu":
                return R.drawable.ic_memory;
            case "storage":
            case "database":
                return R.drawable.ic_storage;
            case "analytics":
            case "chart":
                return R.drawable.ic_analytics;
            case "folder":
                return R.drawable.ic_folder;
            case "history":
                return R.drawable.ic_history;
            case "tune":
                return R.drawable.ic_tune;
            case "timer":
                return R.drawable.ic_timer;
            case "star":
                return R.drawable.ic_star;
            case "heart":
            case "favorite":
                return R.drawable.ic_heart;
            case "rocket":
            case "rocket_launch":
                return R.drawable.ic_rocket;
            case "android":
                return R.drawable.ic_android;
            case "bell":
            case "notifications":
                return R.drawable.ic_bell;
            case "airplane":
            case "flight":
                return R.drawable.ic_airplane;
            case "link":
                return R.drawable.ic_link;
            case "security":
                return R.drawable.ic_security;
            case "check":
                return R.drawable.ic_check;
            case "shield":
            default:
                return R.drawable.ic_shield;
        }
    }
}
