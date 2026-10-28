package org.eu.suisei.xray;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes root shell commands for Xray process, HTTP proxy, and debugging management.
 * Follows the Single Transaction principle to minimize root overhead.
 * Commands and proxy parameters are dynamically read from AppConfig.
 */
public final class RootShell {

    public enum State {
        ON,
        OFF,
        EXITED,
        ERROR
    }

    public enum ActionResult {
        OK,
        ERROR
    }

    public static final class Result {
        public final ActionResult result;
        public final State state;

        public Result(ActionResult result, State state) {
            this.result = result;
            this.state = state;
        }

        @Override
        public String toString() {
            return "Result{result=" + result + ", state=" + state + "}";
        }
    }

    public static final class DetailedStatus {
        public final State state;
        public final String httpProxy;
        public final String pid;
        public final boolean isRootGranted;
        public final boolean isAdbEnabled;

        public DetailedStatus(State state, String httpProxy, String pid,
                              boolean isRootGranted, boolean isAdbEnabled) {
            this.state = state;
            this.httpProxy = httpProxy;
            this.pid = pid;
            this.isRootGranted = isRootGranted;
            this.isAdbEnabled = isAdbEnabled;
        }
    }

    private static final long TOGGLE_TIMEOUT_MS = 10000L;
    private static final long QUERY_TIMEOUT_MS = 5000L;

    private static String buildScriptFunctions(Context context) {
        String startCmd = AppConfig.getStartCommand(context);
        String stopCmd = AppConfig.getStopCommand(context);
        String proxy = AppConfig.getHttpProxy(context);
        String procName = AppConfig.getProcessName(context);

        return "stop_xray() {\n" +
                stopCmd + "\n" +
                "    i=0\n" +
                "    while [ $i -lt 10 ]; do\n" +
                "        [ -z \"$(pidof " + procName + ")\" ] && break\n" +
                "        usleep 150000 2>/dev/null || sleep 1\n" +
                "        i=$((i + 1))\n" +
                "    done\n" +
                "    p=$(pidof " + procName + ")\n" +
                "    if [ -n \"$p\" ]; then\n" +
                "        kill -9 $p 2>/dev/null\n" +
                "        i=0\n" +
                "        while [ $i -lt 5 ]; do\n" +
                "            [ -z \"$(pidof " + procName + ")\" ] && break\n" +
                "            usleep 100000 2>/dev/null || sleep 1\n" +
                "            i=$((i + 1))\n" +
                "        done\n" +
                "    fi\n" +
                "    [ -z \"$(pidof " + procName + ")\" ]\n" +
                "}\n" +
                "start_xray() {\n" +
                startCmd + "\n" +
                "    i=0\n" +
                "    while [ $i -lt 10 ]; do\n" +
                "        usleep 150000 2>/dev/null || sleep 1\n" +
                "        [ -n \"$(pidof " + procName + ")\" ] && return 0\n" +
                "        i=$((i + 1))\n" +
                "    done\n" +
                "    return 1\n" +
                "}\n" +
                "set_proxy() {\n" +
                "    settings put global http_proxy " + proxy + "\n" +
                "    [ \"$(settings get global http_proxy)\" = \"" + proxy + "\" ]\n" +
                "}\n" +
                "clear_proxy() {\n" +
                "    settings put global http_proxy :0\n" +
                "    [ \"$(settings get global http_proxy)\" != \"" + proxy + "\" ]\n" +
                "}\n" +
                "report_status() {\n" +
                "    p=$(settings get global http_proxy)\n" +
                "    x=$(pidof " + procName + ")\n" +
                "    adb_u=$(settings get global adb_enabled)\n" +
                "    adb_w=$(settings get global adb_wifi_enabled)\n" +
                "    echo \"PROXY=$p\"\n" +
                "    echo \"PID=$x\"\n" +
                "    echo \"ADB_USB=$adb_u\"\n" +
                "    echo \"ADB_WIFI=$adb_w\"\n" +
                "    if [ \"$p\" = \"" + proxy + "\" ]; then\n" +
                "        [ -n \"$x\" ] && echo \"STATE=ON\" || echo \"STATE=EXITED\"\n" +
                "    else\n" +
                "        echo \"STATE=OFF\"\n" +
                "    fi\n" +
                "}\n";
    }

    private RootShell() {
        // Prevent instantiation
    }

    /**
     * Executes the click transaction for the TileService.
     */
    public static Result toggle(Context context) {
        String functions = buildScriptFunctions(context);
        String proxy = AppConfig.getHttpProxy(context);
        String procName = AppConfig.getProcessName(context);

        String script = functions +
                "p=$(settings get global http_proxy)\n" +
                "x=$(pidof " + procName + ")\n" +
                "res=\"OK\"\n" +
                "if [ \"$p\" = \"" + proxy + "\" ]; then\n" +
                "    if [ -n \"$x\" ]; then\n" +
                "        clear_proxy\n" +
                "        if ! stop_xray; then\n" +
                "            res=\"ERROR\"\n" +
                "        fi\n" +
                "    else\n" +
                "        if start_xray; then\n" +
                "            if ! set_proxy; then\n" +
                "                stop_xray\n" +
                "                res=\"ERROR\"\n" +
                "            fi\n" +
                "        else\n" +
                "            clear_proxy\n" +
                "            res=\"ERROR\"\n" +
                "        fi\n" +
                "    fi\n" +
                "else\n" +
                "    if [ -n \"$x\" ]; then\n" +
                "        if stop_xray; then\n" +
                "            if start_xray; then\n" +
                "                if ! set_proxy; then\n" +
                "                    stop_xray\n" +
                "                    res=\"ERROR\"\n" +
                "                fi\n" +
                "            else\n" +
                "                res=\"ERROR\"\n" +
                "            fi\n" +
                "        else\n" +
                "            res=\"ERROR\"\n" +
                "        fi\n" +
                "    else\n" +
                "        if start_xray; then\n" +
                "            if ! set_proxy; then\n" +
                "                stop_xray\n" +
                "                res=\"ERROR\"\n" +
                "            fi\n" +
                "        else\n" +
                "            res=\"ERROR\"\n" +
                "        fi\n" +
                "    fi\n" +
                "fi\n" +
                "fp=$(settings get global http_proxy)\n" +
                "fx=$(pidof " + procName + ")\n" +
                "if [ \"$fp\" = \"" + proxy + "\" ]; then\n" +
                "    [ -n \"$fx\" ] && final_state=\"ON\" || final_state=\"EXITED\"\n" +
                "else\n" +
                "    final_state=\"OFF\"\n" +
                "fi\n" +
                "echo \"RESULT=$res\"\n" +
                "echo \"STATE=$final_state\"\n";

        return execute(script, TOGGLE_TIMEOUT_MS);
    }

    /**
     * Queries the actual current state for the TileService.
     */
    public static Result queryState(Context context) {
        String proxy = AppConfig.getHttpProxy(context);
        String procName = AppConfig.getProcessName(context);

        String script =
                "p=$(settings get global http_proxy)\n" +
                "x=$(pidof " + procName + ")\n" +
                "if [ \"$p\" = \"" + proxy + "\" ]; then\n" +
                "    [ -n \"$x\" ] && echo \"STATE=ON\" || echo \"STATE=EXITED\"\n" +
                "else\n" +
                "    echo \"STATE=OFF\"\n" +
                "fi\n" +
                "echo \"RESULT=OK\"\n";

        return execute(script, QUERY_TIMEOUT_MS);
    }

    /**
     * Starts Xray and enables global HTTP proxy.
     */
    public static DetailedStatus startService(Context context) {
        String script = buildScriptFunctions(context) +
                "stop_xray\n" +
                "if start_xray; then\n" +
                "    set_proxy\n" +
                "    echo \"RESULT=OK\"\n" +
                "else\n" +
                "    clear_proxy\n" +
                "    echo \"RESULT=ERROR\"\n" +
                "fi\n" +
                "report_status\n";
        return executeDetailed(script, TOGGLE_TIMEOUT_MS);
    }

    /**
     * Clears HTTP proxy and stops Xray.
     */
    public static DetailedStatus stopService(Context context) {
        String script = buildScriptFunctions(context) +
                "clear_proxy\n" +
                "if stop_xray; then\n" +
                "    echo \"RESULT=OK\"\n" +
                "else\n" +
                "    echo \"RESULT=ERROR\"\n" +
                "fi\n" +
                "report_status\n";
        return executeDetailed(script, TOGGLE_TIMEOUT_MS);
    }

    /**
     * Restarts Xray and enables global HTTP proxy.
     */
    public static DetailedStatus restartService(Context context) {
        String script = buildScriptFunctions(context) +
                "stop_xray\n" +
                "if start_xray; then\n" +
                "    set_proxy\n" +
                "    echo \"RESULT=OK\"\n" +
                "else\n" +
                "    clear_proxy\n" +
                "    echo \"RESULT=ERROR\"\n" +
                "fi\n" +
                "report_status\n";
        return executeDetailed(script, TOGGLE_TIMEOUT_MS);
    }

    /**
     * Disables USB debugging and Wireless debugging.
     */
    public static DetailedStatus disableDebugging(Context context) {
        String script = buildScriptFunctions(context) +
                "settings put global adb_enabled 0\n" +
                "settings put global adb_wifi_enabled 0\n" +
                "echo \"RESULT=OK\"\n" +
                "report_status\n";
        return executeDetailed(script, TOGGLE_TIMEOUT_MS);
    }

    /**
     * Queries detailed status of proxy, PID, and ADB status.
     */
    public static DetailedStatus queryDetailedStatus(Context context) {
        String script = buildScriptFunctions(context) + "report_status\necho \"RESULT=OK\"\n";
        return executeDetailed(script, QUERY_TIMEOUT_MS);
    }

    private static Result execute(String script, long timeoutMs) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", script);
            pb.redirectErrorStream(true);
            process = pb.start();

            List<String> lines = new ArrayList<>();
            final Process proc = process;

            // Concurrently drain process input stream to prevent pipe buffer deadlocks
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line.trim());
                    }
                } catch (Exception ignored) {
                }
            }, "RootShell-Reader");
            readerThread.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                readerThread.interrupt();
                return new Result(ActionResult.ERROR, State.ERROR);
            }

            readerThread.join(1000);

            if (process.exitValue() != 0) {
                return new Result(ActionResult.ERROR, State.ERROR);
            }

            ActionResult parsedResult = ActionResult.ERROR;
            State parsedState = State.ERROR;

            for (String line : lines) {
                if (line.startsWith("RESULT=")) {
                    String val = line.substring(7).trim();
                    if ("OK".equalsIgnoreCase(val)) {
                        parsedResult = ActionResult.OK;
                    } else {
                        parsedResult = ActionResult.ERROR;
                    }
                } else if (line.startsWith("STATE=")) {
                    String val = line.substring(6).trim();
                    if ("ON".equalsIgnoreCase(val)) {
                        parsedState = State.ON;
                    } else if ("OFF".equalsIgnoreCase(val)) {
                        parsedState = State.OFF;
                    } else if ("EXITED".equalsIgnoreCase(val)) {
                        parsedState = State.EXITED;
                    } else {
                        parsedState = State.ERROR;
                    }
                }
            }

            return new Result(parsedResult, parsedState);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(ActionResult.ERROR, State.ERROR);
        }
    }

    private static DetailedStatus executeDetailed(String script, long timeoutMs) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", script);
            pb.redirectErrorStream(true);
            process = pb.start();

            List<String> lines = new ArrayList<>();
            final Process proc = process;

            // Concurrently drain process input stream to prevent pipe buffer deadlocks
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line.trim());
                    }
                } catch (Exception ignored) {
                }
            }, "RootShell-DetailedReader");
            readerThread.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                readerThread.interrupt();
                return new DetailedStatus(State.ERROR, "Unknown", null, false, false);
            }

            readerThread.join(1000);

            if (process.exitValue() != 0) {
                return new DetailedStatus(State.ERROR, "Unknown", null, false, false);
            }

            boolean isRootGranted = true;
            String proxy = null;
            String pid = null;
            boolean isAdbEnabled = false;
            State state = State.ERROR;

            for (String line : lines) {
                if (line.startsWith("PROXY=")) {
                    proxy = line.substring(6).trim();
                } else if (line.startsWith("PID=")) {
                    pid = line.substring(4).trim();
                    if (pid.isEmpty()) {
                        pid = null;
                    }
                } else if (line.startsWith("ADB_USB=")) {
                    String val = line.substring(8).trim();
                    if ("1".equals(val)) {
                        isAdbEnabled = true;
                    }
                } else if (line.startsWith("ADB_WIFI=")) {
                    String val = line.substring(9).trim();
                    if ("1".equals(val)) {
                        isAdbEnabled = true;
                    }
                } else if (line.startsWith("STATE=")) {
                    String val = line.substring(6).trim();
                    if ("ON".equalsIgnoreCase(val)) {
                        state = State.ON;
                    } else if ("OFF".equalsIgnoreCase(val)) {
                        state = State.OFF;
                    } else if ("EXITED".equalsIgnoreCase(val)) {
                        state = State.EXITED;
                    } else {
                        state = State.ERROR;
                    }
                }
            }

            return new DetailedStatus(state, proxy, pid, isRootGranted, isAdbEnabled);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new DetailedStatus(State.ERROR, "Unknown", null, false, false);
        }
    }
}
