package org.eu.suisei.xray;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DashboardConfigManager {

    private static final String PREFS_NAME = "dashboard_config_prefs";
    static final String KEY_DASHBOARD_JSON = "dashboard_json_v2";

    private DashboardConfigManager() {}

    public static List<DashboardSection> load(Context context) {
        SharedPreferences sp = context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_DASHBOARD_JSON, null);
        if (json == null) {
            // Read older installations without discarding their custom layout.
            json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_DASHBOARD_JSON, null);
        }
        if (json != null && !json.trim().isEmpty()) {
            try {
                return parseJson(json);
            } catch (Exception ignored) {
            }
        }

        // Generate default initial layout
        List<DashboardSection> defaults = createDefaults();
        save(context, defaults);
        return defaults;
    }

    public static void save(Context context, List<DashboardSection> sections) {
        try {
            context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_DASHBOARD_JSON, toJson(sections).toString()).apply();
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException("Unable to serialize dashboard", e);
        }
    }

    static JSONArray toJson(List<DashboardSection> sections) throws org.json.JSONException {
            JSONArray sectionsArr = new JSONArray();
            for (DashboardSection sec : sections) {
                JSONObject secObj = new JSONObject();
                secObj.put("id", sec.id);
                secObj.put("title", sec.title);
                secObj.put("titleKey", sec.titleKey == null ? "" : sec.titleKey);

                JSONArray itemsArr = new JSONArray();
                for (DashboardItem item : sec.items) {
                    JSONObject itemObj = new JSONObject();
                    itemObj.put("id", item.id);
                    itemObj.put("title", item.title);
                    itemObj.put("titleKey", item.titleKey == null ? "" : item.titleKey);
                    itemObj.put("type", item.type);
                    itemObj.put("icon", item.icon);
                    itemObj.put("onCommand", item.onCommand);
                    itemObj.put("offCommand", item.offCommand);
                    itemObj.put("stateCheckCommand", item.stateCheckCommand);
                    itemObj.put("summaryCommand", item.summaryCommand);
                    itemObj.put("disableConditionCommand", item.disableConditionCommand);
                    itemObj.put("isCore", item.isCore);
                    itemsArr.put(itemObj);
                }
                secObj.put("items", itemsArr);
                sectionsArr.put(secObj);
            }

            return sectionsArr;
    }

    public static List<DashboardSection> createDefaults() {
        List<DashboardSection> sections = new ArrayList<>();

        // Section 1: Proxy
        List<DashboardItem> proxyItems = new ArrayList<>();
        proxyItems.add(new DashboardItem(
                "core_xray",
                "Core",
                DashboardItem.TYPE_SWITCH,
                "shield",
                AppConfig.DEFAULT_START_CMD,
                AppConfig.DEFAULT_STOP_CMD,
                "", // Default check
                "", // Default 4-state summary
                "",
                true
        ));
        sections.add(new DashboardSection("sec_proxy", "Proxy", proxyItems));

        // Section 2: Actions
        List<DashboardItem> actionItems = new ArrayList<>();
        actionItems.add(new DashboardItem(
                UUID.randomUUID().toString(),
                "Disable Debugging",
                DashboardItem.TYPE_BUTTON,
                "bug",
                "su -c 'settings put global adb_enabled 0; settings put global adb_wifi_enabled 0'",
                "",
                "",
                "", // Default summaryCommand is empty: no description shown by default
                "su -c '[ \"$(settings get global adb_enabled)\" = \"0\" ] && [ \"$(settings get global adb_wifi_enabled)\" = \"0\" ]'",
                false
        ));
        sections.add(new DashboardSection("sec_actions", "Actions", actionItems));

        sections.get(0).titleKey = "default_section_proxy";
        sections.get(1).titleKey = "default_section_actions";
        proxyItems.get(0).titleKey = "default_item_core";
        actionItems.get(0).titleKey = "default_item_disable_debugging";
        return sections;
    }

    public static String localizedTitle(Context context, String key, String fallback) {
        if (key == null) return fallback;
        switch (key) {
            case "default_section_proxy": return context.getString(R.string.default_section_proxy);
            case "default_section_actions": return context.getString(R.string.default_section_actions);
            case "default_item_core": return context.getString(R.string.default_item_core);
            case "default_item_disable_debugging": return context.getString(R.string.default_item_disable_debugging);
            default: return fallback;
        }
    }

    private static List<DashboardSection> parseJson(String json) throws Exception {
        List<DashboardSection> sections = new ArrayList<>();
        JSONArray sectionsArr = new JSONArray(json);
        for (int i = 0; i < sectionsArr.length(); i++) {
            JSONObject secObj = sectionsArr.getJSONObject(i);
            String secId = secObj.getString("id");
            String secTitle = secObj.getString("title");

            List<DashboardItem> items = new ArrayList<>();
            JSONArray itemsArr = secObj.optJSONArray("items");
            if (itemsArr != null) {
                for (int j = 0; j < itemsArr.length(); j++) {
                    JSONObject itemObj = itemsArr.getJSONObject(j);
                    items.add(new DashboardItem(
                            itemObj.getString("id"),
                            itemObj.getString("title"),
                            itemObj.optString("type", DashboardItem.TYPE_BUTTON),
                            itemObj.optString("icon", "shield"),
                            itemObj.optString("onCommand", ""),
                            itemObj.optString("offCommand", ""),
                            itemObj.optString("stateCheckCommand", ""),
                            itemObj.optString("summaryCommand", ""),
                            itemObj.optString("disableConditionCommand", ""),
                            itemObj.optBoolean("isCore", false)
                    ));
                    DashboardItem item = items.get(items.size() - 1);
                    item.titleKey = itemObj.optString("titleKey", null);
                    // Migrate only recognizable, unchanged built-in names from older versions.
                    if (!itemObj.has("titleKey")) {
                        if (item.isCore && "core_xray".equals(item.id) && "Core".equals(item.title)) {
                            item.titleKey = "default_item_core";
                        } else if ("sec_actions".equals(secId) && "Disable Debugging".equals(item.title)
                                && "bug".equals(item.icon)
                                && "su -c 'settings put global adb_enabled 0; settings put global adb_wifi_enabled 0'".equals(item.onCommand)) {
                            item.titleKey = "default_item_disable_debugging";
                        }
                    }
                }
            }
            DashboardSection section = new DashboardSection(secId, secTitle, items);
            section.titleKey = secObj.optString("titleKey", null);
            if (!secObj.has("titleKey")) {
                if ("sec_proxy".equals(secId) && "Proxy".equals(secTitle)) section.titleKey = "default_section_proxy";
                if ("sec_actions".equals(secId) && "Actions".equals(secTitle)) section.titleKey = "default_section_actions";
            }
            sections.add(section);
        }
        return sections;
    }
}
