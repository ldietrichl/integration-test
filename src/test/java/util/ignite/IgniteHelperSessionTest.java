package util.ignite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real process/pipe regression tests with a standalone fake helper; no Ignite or service calls. */
class IgniteHelperSessionTest {
    @TempDir Path temporaryDirectory;

    @Test
    void retainsPidAndSessionAfterCommandFailureForCleanup() throws Exception {
        try (var child = child("normal", 20)) {
            try (var session = new IgniteHelperSession(child.launch())) {
                long pid = child.pid();
                var prepared = session.call("prepare");
                assertEquals(pid, prepared.path("pid").asLong());
                assertEquals(1, prepared.path("ordinal").asInt());
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> session.call("verify"));
                assertTrue(failure.getMessage().contains("fixture.VerifyFailure"));
                assertTrue(session.isUsable(), "A valid command error must leave the session usable");
                assertTrue(child.isAlive(), "The same helper must survive the command error");
                var cleaned = session.call("cleanup");
                assertEquals(pid, cleaned.path("pid").asLong());
                assertEquals(prepared.path("sessionId"), cleaned.path("sessionId"));
                assertEquals("cleanup", cleaned.path("mode").asText());
                assertEquals(3, cleaned.path("ordinal").asInt(), "Cleanup must run after the failed verify in the same child");
            }
            assertFalse(child.isAlive());
            assertEquals("3", Files.readString(child.closedFile(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void operationTimeoutConfirmsChildExitBeforeReturning() throws Exception {
        // The same deadline covers JVM startup: avoid an unrealistically short startup budget.
        assertTransportFailureKillsChild("timeout", 5, "timed out");
    }

    @Test
    void eofWhileChildStillAliveAbortsChild() throws Exception {
        assertTransportFailureKillsChild("eof", 20, "helper exited or protocol unavailable");
    }

    @Test
    void unexpectedReplyIdAbortsChild() throws Exception {
        assertTransportFailureKillsChild("bad-id", 20, "Unexpected Ignite session reply");
    }

    @Test
    void closesIdleSessionWithoutFixtureCommands() throws Exception {
        try (var child = child("normal", 20)) {
            var session = new IgniteHelperSession(child.launch());
            try {
                assertTrue(child.isAlive());
                session.close();
                assertFalse(child.isAlive(), "Normal close must wait for process exit");
                assertFalse(session.isUsable());
                assertEquals("0", Files.readString(child.closedFile(), StandardCharsets.UTF_8));
                session.close(); // Repeated close is harmless.
            } finally {
                session.close();
            }
        }
    }

    @Test
    void startupProtocolMismatchKillsChild() throws Exception {
        try (var child = child("protocol-mismatch", 20)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new IgniteHelperSession(child.launch()));
            assertTrue(failure.getMessage().contains("Unexpected Ignite session reply"));
            assertFalse(child.isAlive(), "A rejected ready envelope must not leave a helper running");
        }
    }

    @Test
    void interruptedWaitPreservesInterruptAndConfirmsChildExit() throws Exception {
        try (var child = child("timeout", 20)) {
            var session = new IgniteHelperSession(child.launch());
            var failure = new AtomicReference<Throwable>();
            var interruptPreserved = new AtomicBoolean();
            var finished = new CountDownLatch(1);
            Thread waiting = new Thread(() -> {
                try {
                    session.call("prepare");
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    interruptPreserved.set(Thread.currentThread().isInterrupted());
                    finished.countDown();
                }
            }, "ignite-session-interruption-test");
            waiting.setDaemon(true);
            try {
                waiting.start();
                awaitCommand(child.commandFile());
                waiting.interrupt();
                assertTrue(finished.await(10, TimeUnit.SECONDS), "Interrupted operation must finish promptly");
                assertTrue(failure.get() instanceof InterruptedException, "Unexpected failure: " + failure.get());
                assertTrue(interruptPreserved.get(), "The calling thread's interrupt flag must be restored");
                assertFalse(child.isAlive(), "Interrupted call must confirm exit before propagating the interruption");
                assertFalse(session.isUsable());
            } finally {
                // Test cleanup is separate from the transport-exit assertions above.
                waiting.interrupt();
                try {
                    child.stop();
                    waiting.join(10_000);
                } finally {
                    // A stuck caller owns the session monitor; do not deadlock this test while reporting it.
                    if (!waiting.isAlive()) session.close();
                }
                assertFalse(waiting.isAlive(), "Waiting test thread did not stop after child termination");
            }
        }
    }

    private void assertTransportFailureKillsChild(String scenario, long timeoutSeconds, String message) throws Exception {
        try (var child = child(scenario, timeoutSeconds)) {
            try (var session = new IgniteHelperSession(child.launch())) {
                assertTrue(child.isAlive());
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> session.call("prepare"));
                assertTrue(failure.getMessage().contains(message), failure.getMessage());
                // Check before close/abort: the failing request itself must have confirmed termination.
                assertFalse(child.isAlive(), "Transport failure returned while the helper was still alive");
                assertFalse(session.isUsable());
                session.abort();
                assertFalse(child.isAlive());
            }
        }
    }

    private FakeChild child(String scenario, long timeoutSeconds) throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve(scenario + "-" + UUID.randomUUID()));
        Path pidFile = directory.resolve("child.pid");
        Path manifest = directory.resolve("manifest.json");
        Files.writeString(manifest, "{}", StandardCharsets.UTF_8);
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        // Only test classes are on the child classpath. The fixture itself uses the JDK exclusively.
        String classpath = Path.of(IgniteSessionProtocolFixture.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        var builder = new ProcessBuilder(java.toString(), "-Dfile.encoding=UTF-8", "-cp", classpath,
                IgniteSessionProtocolFixture.class.getName(), scenario, pidFile.toString()).redirectErrorStream(true);
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        return new FakeChild(new IgniteClientRuntime.HelperLaunch(builder, manifest,
                directory.resolve("session.log"), timeoutSeconds), pidFile);
    }

    private static void awaitCommand(Path marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(marker) && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(Files.exists(marker), "Fake helper did not receive the command");
    }

    private record FakeChild(IgniteClientRuntime.HelperLaunch launch, Path pidFile) implements AutoCloseable {
        long pid() throws Exception { return Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).trim()); }
        boolean isAlive() throws Exception { return ProcessHandle.of(pid()).map(ProcessHandle::isAlive).orElse(false); }
        Path commandFile() { return pidFile.resolveSibling("command-received.txt"); }
        Path closedFile() { return pidFile.resolveSibling("closed.txt"); }

        void stop() throws Exception {
            if (!Files.exists(pidFile)) return;
            var process = ProcessHandle.of(pid());
            if (process.isPresent() && process.get().isAlive()) {
                process.get().destroyForcibly();
                process.get().onExit().get(5, TimeUnit.SECONDS);
            }
        }

        @Override public void close() throws Exception { stop(); }
    }
}
