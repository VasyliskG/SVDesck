package com.securevault.desktop.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class ConfigurationManager {

    private static final String TOKEN_FILE = "auth.token";

    private static Path getTokenFilePath() {
        return LocalFileStorage.getVaultPath().resolve(TOKEN_FILE);
    }

    public static void saveToken(String token) throws IOException {
        Path tokenPath = getTokenFilePath();
        Files.writeString(tokenPath, token);

        // Set file permissions to be readable/writable only by the owner
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("win")) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(tokenPath, perms);
            }
        } catch (UnsupportedOperationException e) {
            // Filesystem does not support POSIX permissions, ignore.
        }
    }

    public static String loadToken() throws IOException {
        Path tokenPath = getTokenFilePath();
        if (Files.exists(tokenPath)) {
            return Files.readString(tokenPath).trim();
        }
        return null;
    }
}
