package org.eu.suisei.xray;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ConfigCodecTest {
    private ConfigCodec.Snapshot sample() throws Exception {
        JSONObject core = new JSONObject().put("id", "core_xray").put("title", "核心 日本語")
                .put("titleKey", "default_item_core").put("type", "SWITCH").put("icon", "shield")
                .put("onCommand", "echo 'hello'\nprintf \"日本語\"").put("offCommand", "stop")
                .put("stateCheckCommand", "").put("summaryCommand", "").put("disableConditionCommand", "")
                .put("isCore", true);
        JSONArray dashboard = new JSONArray().put(new JSONObject().put("id", "proxy").put("title", "自定义")
                .put("titleKey", "").put("items", new JSONArray().put(core)));
        return new ConfigCodec.Snapshot("start\n中文", "stop", "127.0.0.1:2080", "xray", dashboard.toString());
    }

    @Test public void roundTripPreservesCommandsAndLocalizedNames() throws Exception {
        ConfigCodec.Snapshot original = sample();
        ConfigCodec.Snapshot restored = ConfigCodec.decode(ConfigCodec.encode(original));
        assertEquals(original.startCommand, restored.startCommand);
        assertEquals(original.stopCommand, restored.stopCommand);
        assertEquals(original.httpProxy, restored.httpProxy);
        assertEquals(original.processName, restored.processName);
        assertEquals(original.dashboardJson, restored.dashboardJson);
    }

    @Test public void rejectsMalformedOrUnsupportedBackups() throws Exception {
        String valid = ConfigCodec.encode(sample());
        for (String invalid : new String[]{"{}", valid + " garbage", valid.replace("\"version\": 1", "\"version\": 2"),
                valid.replace("\"SWITCH\"", "\"OTHER\""), valid.replace("\"isCore\": true", "\"isCore\": false"),
                valid.replace("\"isCore\": true", "\"isCore\": \"true\"")}) {
            assertThrows(Exception.class, () -> ConfigCodec.decode(invalid));
        }
    }

    @Test public void rejectsDuplicateIdsBeforePersistence() throws Exception {
        JSONObject backup = new JSONObject(ConfigCodec.encode(sample()));
        JSONArray items = backup.getJSONArray("dashboard").getJSONObject(0).getJSONArray("items");
        items.put(new JSONObject(items.getJSONObject(0).toString()).put("isCore", false));
        assertThrows(Exception.class, () -> ConfigCodec.decode(backup.toString()));
    }

    @Test public void limitsInputAndRejectsInvalidUtf8() throws Exception {
        assertThrows(Exception.class, () -> ConfigCodec.read(new ByteArrayInputStream(new byte[ConfigCodec.MAX_BYTES + 1])));
        assertThrows(Exception.class, () -> ConfigCodec.read(new ByteArrayInputStream(new byte[]{(byte) 0xff})));
        String json = ConfigCodec.encode(sample());
        assertEquals(json, ConfigCodec.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        assertEquals(sample().startCommand, ConfigCodec.decode("\uFEFF" + json).startCommand);
    }
}
