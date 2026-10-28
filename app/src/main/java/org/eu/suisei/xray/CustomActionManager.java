package org.eu.suisei.xray;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CustomActionManager {

    private static final String PREFS_NAME = "xray_custom_actions";
    private static final String KEY_CUSTOM_ACTIONS = "custom_actions_json";

    private CustomActionManager() {}

    public static List<CustomAction> load(Context context) {
        List<CustomAction> list = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_CUSTOM_ACTIONS, null);
        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new CustomAction(
                            obj.getString("id"),
                            obj.getString("name"),
                            obj.getString("command")
                    ));
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public static void save(Context context, List<CustomAction> list) {
        try {
            JSONArray arr = new JSONArray();
            for (CustomAction action : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", action.id);
                obj.put("name", action.name);
                obj.put("command", action.command);
                arr.put(obj);
            }
            SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_CUSTOM_ACTIONS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
