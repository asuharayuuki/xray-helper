package org.eu.suisei.xray;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenTimeoutOverrideTest {
    private static class Memory implements ScreenTimeoutOverride.Storage {
        Integer current = 30000, original;
        int applied;
        boolean backedUp, failWrite;
        public Integer read() { return current; }
        public void write(Integer value) throws Exception {
            if (failWrite) throw new Exception("Root denied");
            current = value;
        }
        public boolean hasBackup() { return backedUp; }
        public Integer original() { return original; }
        public int applied() { return applied; }
        public void backup(Integer value, int next) { original = value; applied = next; backedUp = true; }
        public void clear() { backedUp = false; }
    }

    @Test public void cyclingDurationsRestoresFirstOriginal() throws Exception {
        Memory memory = new Memory();
        ScreenTimeoutOverride timeout = new ScreenTimeoutOverride(memory);
        timeout.apply(300);
        assertEquals(Integer.valueOf(330000), memory.current);
        timeout.apply(600);
        assertEquals(Integer.valueOf(630000), memory.current);
        timeout.apply(Integer.MAX_VALUE);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), memory.current);
        timeout.restore();
        assertEquals(Integer.valueOf(30000), memory.current);
        assertFalse(memory.backedUp);
    }

    @Test public void recoveryAfterProcessDeathUsesPersistedBackup() throws Exception {
        Memory memory = new Memory();
        new ScreenTimeoutOverride(memory).apply(300);
        new ScreenTimeoutOverride(memory).restore();
        assertEquals(Integer.valueOf(30000), memory.current);
    }

    @Test public void preservesUserChangesAndUsesThemForNextCycle() throws Exception {
        Memory memory = new Memory();
        ScreenTimeoutOverride timeout = new ScreenTimeoutOverride(memory);
        timeout.apply(300);
        memory.current = 60000;
        timeout.restore();
        assertEquals(Integer.valueOf(60000), memory.current);
        timeout.apply(300);
        memory.current = 120000;
        timeout.apply(600);
        timeout.restore();
        assertEquals(Integer.valueOf(120000), memory.current);
    }

    @Test public void failedRestoreRetainsBackupForRetry() throws Exception {
        Memory memory = new Memory();
        ScreenTimeoutOverride timeout = new ScreenTimeoutOverride(memory);
        timeout.apply(300);
        memory.failWrite = true;
        try { timeout.restore(); fail(); } catch (Exception expected) { }
        assertTrue(memory.backedUp);
        memory.failWrite = false;
        timeout.restore();
        assertEquals(Integer.valueOf(30000), memory.current);
    }

    @Test public void restoresAbsentSettingAndAvoidsOverflow() throws Exception {
        Memory memory = new Memory();
        memory.current = null;
        ScreenTimeoutOverride timeout = new ScreenTimeoutOverride(memory);
        timeout.apply(300);
        timeout.restore();
        assertNull(memory.current);
        memory.current = Integer.MAX_VALUE;
        timeout.apply(1800);
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), memory.current);
    }

    @Test public void deniedApplyDoesNotChangeOriginalTimeout() throws Exception {
        Memory memory = new Memory();
        ScreenTimeoutOverride timeout = new ScreenTimeoutOverride(memory);
        memory.failWrite = true;
        try { timeout.apply(300); fail(); } catch (Exception expected) { }
        assertEquals(Integer.valueOf(30000), memory.current);
        timeout.restore(); // No privileged write is needed if apply never took effect.
        assertFalse(memory.backedUp);
    }
}
