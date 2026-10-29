package org.eu.suisei.xray;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Drains stdout while enforcing a deadline without blocking on command output. */
public final class CommandRunner {
    private CommandRunner() {}

    public static final class Result {
        public final int exitCode;
        public final String output;
        public final boolean timedOut;

        Result(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }
    }

    public static Result run(ProcessBuilder builder, long timeoutMs, int maxChars) throws Exception {
        Process process = builder.redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStreamReader stream = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[2048];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    synchronized (output) {
                        int keep = Math.min(count, Math.max(0, maxChars - output.length()));
                        output.append(buffer, 0, keep);
                    }
                }
            } catch (Exception ignored) {
                // Closing the stream after a deadline also ends the reader.
            }
        }, "Command-Output");
        reader.setDaemon(true);
        reader.start();
        try {
            process.getOutputStream().close();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            int exitCode;
            boolean timedOut = false;
            while (true) {
                try {
                    exitCode = process.exitValue();
                    break;
                } catch (IllegalThreadStateException running) {
                    if (System.nanoTime() >= deadline) {
                        timedOut = true;
                        exitCode = -1;
                        process.destroy();
                        break;
                    }
                    Thread.sleep(20);
                }
            }
            reader.join(500);
            synchronized (output) {
                return new Result(exitCode, output.toString(), timedOut);
            }
        } finally {
            process.destroy();
            try { process.getInputStream().close(); } catch (Exception ignored) {}
            reader.interrupt();
        }
    }
}
