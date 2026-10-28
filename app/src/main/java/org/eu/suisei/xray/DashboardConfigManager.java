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
    private static final String KEY_DASHBOARD_JSON = "dashboard_json_v1";

    private DashboardConfigManager() {}

    public static List<DashboardSection> load(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_DASHBOARD_JSON, null);
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
            JSONArray sectionsArr = new JSONArray();
            for (DashboardSection sec : sections) {
                JSONObject secObj = new JSONObject();
                secObj.put("id", sec.id);
                secObj.put("title", sec.title);

                JSONArray itemsArr = new JSONArray();
                for (DashboardItem item : sec.items) {
                    JSONObject itemObj = new JSONObject();
                    itemObj.put("id", item.id);
                    itemObj.put("title", item.title);
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

            SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_DASHBOARD_JSON, sectionsArr.toString()).apply();
        } catch (Exception ignored) {
        }
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
                "", // Default start
                "", // Default stop
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
                "adb_u=$(settings get global adb_enabled); adb_w=$(settings get global adb_wifi_enabled); [ \"$adb_u\" = \"0\" ] && [ \"$adb_w\" = \"0\" ] && echo \"Disabled\" || echo \"Active\"",
                "su -c '[ \"$(settings get global adb_enabled)\" = \"0\" ] && [ \"$(settings get global adb_wifi_enabled)\" = \"0\" ]'",
                false
        ));
        sections.add(new DashboardSection("sec_actions", "Actions", actionItems));

        return sections;
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
                }
            }
            sections.add(new DashboardSection(secId, secTitle, items));
        }
        return sections;
    }
}
