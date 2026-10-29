package org.eu.suisei.xray;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class CommandRunnerTest {
    public static class Child {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("hang")) Thread.sleep(10000);
            if (args[0].equals("flood")) {
                for (int i = 0; i < 20000; i++) System.out.println("stdout pipe must be drained");
            }
            if (args[0].equals("fail")) System.exit(7);
            System.out.print("done");
        }
    }

    private ProcessBuilder child(String mode) throws Exception {
        String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        String classes = new File(Child.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        return new ProcessBuilder(java, "-cp", classes, Child.class.getName(), mode);
    }

    @Test public void silentCommandsRespectDeadline() throws Exception {
        long start = System.nanoTime();
        CommandRunner.Result result = CommandRunner.run(child("hang"), 500, 100);
        assertTrue(result.timedOut);
        assertTrue((System.nanoTime() - start) / 1000000 < 4000);
    }

    @Test public void largeOutputsAreDrainedButMemoryIsBounded() throws Exception {
        CommandRunner.Result result = CommandRunner.run(child("flood"), 10000, 50);
        assertFalse(result.timedOut);
        assertEquals(0, result.exitCode);
        assertEquals(50, result.output.length());
    }

    @Test public void preservesNonzeroExitStatus() throws Exception {
        assertEquals(7, CommandRunner.run(child("fail"), 5000, 50).exitCode);
    }
}
