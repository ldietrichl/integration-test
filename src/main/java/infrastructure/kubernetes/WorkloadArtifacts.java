package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Full pre-images stay private; only path/hash/identity metadata enters Allure. No kubeconfig export. */
final class WorkloadArtifacts {
    private final Path directory;
    private long sequence;
    private final java.util.function.BooleanSupplier attachmentAllowed;
    WorkloadArtifacts(String environment, java.util.function.BooleanSupplier attachmentAllowed) {
        this.attachmentAllowed = Objects.requireNonNull(attachmentAllowed);
        String phase = "RESOLVE_PATH";
        try {
            Path project = Path.of("").toAbsolutePath().normalize();
            Path build = project.resolve("build");
            Path root = Path.of(System.getProperty("stand.artifacts.directory",
                    project.resolve(".workload-recovery/private").toString())).toAbsolutePath().normalize();
            Path privateRoot = project.resolve(".workload-recovery/private");
            if (!root.startsWith(privateRoot) || root.startsWith(build))
                throw new IllegalStateException("PRIVATE_ARTIFACT_PATH_OUTSIDE_ROOT: configure stand.artifacts.directory under .workload-recovery/private, outside build");
            phase = "CHECK_REDIRECTS";
            for (Path cursor = root; cursor != null; cursor = cursor.getParent())
                if (Files.exists(cursor) && (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor)))
                    throw new IllegalStateException("PRIVATE_ARTIFACT_PATH_REDIRECTED");
            directory = root.resolve(environment).resolve(UUID.randomUUID().toString());
            phase = "CREATE_DIRECTORY";
            Files.createDirectories(directory);
            phase = "SET_PRIVATE_PERMISSIONS";
            restrict(directory, true);
        } catch (Exception failure) {
            String reason = failure instanceof IllegalStateException && failure.getMessage() != null
                    && failure.getMessage().startsWith("PRIVATE_ARTIFACT_PATH_") ? failure.getMessage()
                    : failure.getClass().getSimpleName();
            // Do not attach the original exception: filesystem messages may contain sensitive paths.
            throw new IllegalStateException("PRIVATE_ARTIFACT_DIRECTORY_FAILED; phase=" + phase
                    + "; reason=" + reason + "; no mutation permitted");
        }
    }
    Path save(String name, JsonNode value) { return saveText(name, value.toPrettyString()); }
    synchronized Path event(String stage, JsonNode details) {
        var entry = WorkloadJson.JSON.createObjectNode().put("stage", stage)
                .put("sequence", ++sequence).put("time", java.time.Instant.now().toString());
        entry.set("details", details);
        return saveText(String.format(Locale.ROOT, "journal-%06d.json", sequence), entry.toPrettyString(), false);
    }
    Path saveText(String name, String value) {
        return saveText(name, value, true);
    }
    private Path saveText(String name, String value, boolean attachMetadata) {
        try {
            Path file = directory.resolve(name).normalize();
            if (!file.getParent().equals(directory)) throw new IllegalArgumentException("Artifact basename required");
            Files.createFile(file);
            restrict(file, false);
            try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
                var bytes = java.nio.ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true); // Complete the local pre-image before dispatching a remote mutation.
            }
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
            if (attachMetadata && attachmentAllowed.getAsBoolean() && Allure.getLifecycle().getCurrentTestCase().isPresent()) {
                try {
                    Allure.addAttachment("Private stand artifact: " + name,
                            "Full original/config values are NOT attached to Allure.\nPath: " + file + "\nSHA-256: " + sha
                            + "\nKeep private; do not upload this directory to TestOps.");
                } catch (RuntimeException attachmentFailure) {
                    System.err.println("[workload-control] Private artifact saved; Allure metadata unavailable: "
                            + attachmentFailure.getClass().getSimpleName());
                }
            }
            return file;
        } catch (Exception failure) {
            throw new IllegalStateException("PRIVATE_ARTIFACT_WRITE_FAILED; no next mutation permitted");
        }
    }
    private static void restrict(Path path, boolean directory) throws Exception {
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) {
            UserPrincipal user = path.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            AclEntry.Builder entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(user).setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (directory) entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
            acl.setAcl(List.of(entry.build())); // Do not copy/change the original file owner.
        } else if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } else throw new IllegalStateException("No supported private ACL mechanism");
    }
}
