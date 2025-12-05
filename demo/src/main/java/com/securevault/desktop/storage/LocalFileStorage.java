package com.securevault.desktop.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalFileStorage {

    private static final String VAULT_DIR_NAME = ".securevault";

    public static Path getVaultPath() {
        return Paths.get(System.getProperty("user.home"), VAULT_DIR_NAME);
    }

    public static void init() throws IOException {
        Path vaultPath = getVaultPath();
        if (!Files.exists(vaultPath)) {
            Files.createDirectories(vaultPath);
        }
    }
}
