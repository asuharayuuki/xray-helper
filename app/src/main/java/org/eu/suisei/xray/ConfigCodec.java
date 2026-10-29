package org.eu.suisei.xray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Versioned interchange format. Parsing and validation never change settings. */
public final class ConfigCodec {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final String FORMAT = "xray-helper-config";
    private ConfigCodec() {}

    public static final class Snapshot {
        public final String startCommand, stopCommand, httpProxy, processName;
        public final String dashboardJson;

        public Snapshot(String startCommand, String stopCommand, String httpProxy,
                        String processName, String dashboardJson) {
            this.startCommand = startCommand;
            this.stopCommand = stopCommand;
            this.httpProxy = httpProxy;
            this.processName = processName;
            this.dashboardJson = dashboardJson;
        }
    }

    public static String read(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (data.size() + count > MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB");
            data.write(buffer, 0, count);
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data.toByteArray())).toString();
    }

    public static String encode(Snapshot snapshot) throws JSONException {
        JSONObject settings = new JSONObject()
                .put("startCommand", snapshot.startCommand).put("stopCommand", snapshot.stopCommand)
                .put("httpProxy", snapshot.httpProxy).put("processName", snapshot.processName);
        String json = new JSONObject().put("format", FORMAT).put("version", 1)
                .put("settings", settings).put("dashboard", new JSONArray(snapshot.dashboardJson)).toString(2);
        decode(json); // Exports obey the same validation and size limits as imports.
        return json;
    }

    public static Snapshot decode(String json) throws JSONException {
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid();
        JSONTokener tokener = new JSONTokener(json.startsWith("\uFEFF") ? json.substring(1) : json);
        Object value = tokener.nextValue();
        if (!(value instanceof JSONObject) || tokener.nextClean() != 0) throw invalid();
        JSONObject root = (JSONObject) value;
        if (!FORMAT.equals(string(root, "format", true)) || !Integer.valueOf(1).equals(root.get("version"))) throw invalid();
        JSONObject settings = root.getJSONObject("settings");
        String start = string(settings, "startCommand", true);
        String stop = string(settings, "stopCommand", true);
        String proxy = string(settings, "httpProxy", true);
        String process = string(settings, "processName", true);
        JSONArray sections = root.getJSONArray("dashboard");
        if (sections.length() > 100) throw invalid();
        Set<String> sectionIds = new HashSet<>(), itemIds = new HashSet<>();
        int coreCount = 0;
        for (int s = 0; s < sections.length(); s++) {
            JSONObject section = sections.getJSONObject(s);
            if (!sectionIds.add(string(section, "id", true))) throw invalid();
            string(section, "title", true);
            titleKey(section, true);
            JSONArray items = section.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!itemIds.add(string(item, "id", true)) || itemIds.size() > 1000) throw invalid();
                string(item, "title", true);
                titleKey(item, false);
                String type = string(item, "type", true);
                if (!DashboardItem.TYPE_SWITCH.equals(type) && !DashboardItem.TYPE_BUTTON.equals(type)) throw invalid();
                string(item, "icon", false);
                for (String key : new String[]{"onCommand", "offCommand", "stateCheckCommand", "summaryCommand", "disableConditionCommand"}) {
                    string(item, key, false);
                }
                Object core = item.get("isCore");
                if (!(core instanceof Boolean)) throw invalid();
                if ((Boolean) core) {
                    coreCount++;
                    if (!DashboardItem.TYPE_SWITCH.equals(type)) throw invalid();
                }
            }
        }
        if (coreCount != 1) throw invalid();
        return new Snapshot(start, stop, proxy, process, sections.toString());
    }

    private static String string(JSONObject object, String key, boolean nonempty) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String) || (nonempty && ((String) value).trim().isEmpty())) throw invalid();
        return (String) value;
    }

    private static void titleKey(JSONObject object, boolean section) throws JSONException {
        if (!object.has("titleKey")) return;
        String key = string(object, "titleKey", false);
        if (!key.isEmpty() && !(section
                ? key.equals("default_section_proxy") || key.equals("default_section_actions")
                : key.equals("default_item_core") || key.equals("default_item_disable_debugging"))) throw invalid();
    }

    private static JSONException invalid() { return new JSONException("Invalid or unsupported configuration"); }
}
