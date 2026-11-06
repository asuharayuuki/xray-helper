package org.eu.suisei.xray;

/** A recoverable temporary setting. Callers serialize all operations off the UI thread. */
final class ScreenTimeoutOverride {
    interface Storage {
        Integer read() throws Exception;
        void write(Integer value) throws Exception;
        boolean hasBackup();
        Integer original();
        int applied();
        void backup(Integer original, int applied) throws Exception;
        void clear() throws Exception;
    }

    private final Storage storage;

    ScreenTimeoutOverride(Storage storage) {
        this.storage = storage;
    }

    void apply(int seconds) throws Exception {
        Integer current = storage.read();
        // Preserve the first original across duration changes. Respect edits made in Settings.
        Integer original = storage.hasBackup() && current != null && current == storage.applied()
                ? storage.original() : current;
        long baseline = original == null ? 30000L : Math.max(0, original);
        int timeout = seconds == Integer.MAX_VALUE ? Integer.MAX_VALUE
                : (int) Math.min(Integer.MAX_VALUE, baseline + seconds * 1000L);
        // Journal before writing so interrupted writes can be recovered on the next launch.
        storage.backup(original, timeout);
        storage.write(timeout);
        if (!Integer.valueOf(timeout).equals(storage.read())) {
            throw new IllegalStateException("Screen timeout write was not applied");
        }
    }

    void restore() throws Exception {
        if (!storage.hasBackup()) return;
        if (Integer.valueOf(storage.applied()).equals(storage.read())) {
            storage.write(storage.original());
            if (!java.util.Objects.equals(storage.original(), storage.read())) {
                throw new IllegalStateException("Screen timeout restore was not applied");
            }
        }
        // Do not overwrite a timeout the user changed while Caffeine was running.
        storage.clear();
    }
}
