package com.securevault.desktop.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CryptoEngine {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // 96 bits for GCM
    private static final int TAG_LENGTH = 128; // GCM tag length in bits

    public static void encryptFile(Path inputFile, Path outputFile, SecretKey key) throws Exception {
        byte[] fileBytes = Files.readAllBytes(inputFile);

        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] encryptedBytes = cipher.doFinal(fileBytes);

        // Calculate SHA-256 checksum of the original file
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] checksum = digest.digest(fileBytes);

        // Combine IV, checksum, and encrypted data for storage
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + 32 + encryptedBytes.length);
        byteBuffer.put(iv);
        byteBuffer.put(checksum);
        byteBuffer.put(encryptedBytes);

        Files.write(outputFile, byteBuffer.array());
    }

    public static void decryptFile(Path inputFile, Path outputFile, SecretKey key) throws Exception {
        byte[] fileBytes = Files.readAllBytes(inputFile);

        ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes);
        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        byte[] storedChecksum = new byte[32];
        byteBuffer.get(storedChecksum);

        byte[] encryptedBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(encryptedBytes);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        // Verify checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] calculatedChecksum = digest.digest(decryptedBytes);

        if (!Arrays.equals(storedChecksum, calculatedChecksum)) {
            throw new SecurityException("Checksum verification failed. File may be corrupted or tampered with.");
        }

        Files.write(outputFile, decryptedBytes);
    }

    public static void encryptDirectory(Path inputDir, Path outputFile, SecretKey key) throws Exception {
        // Create ZIP archive of the directory in memory
        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBuffer)) {
            zipDirectory(inputDir, inputDir, zos);
        }
        byte[] zipBytes = zipBuffer.toByteArray();

        // Encrypt the ZIP archive
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] encryptedBytes = cipher.doFinal(zipBytes);

        // Calculate SHA-256 checksum of the original ZIP data
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] checksum = digest.digest(zipBytes);

        // Combine IV, checksum, and encrypted data for storage
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + 32 + encryptedBytes.length);
        byteBuffer.put(iv);
        byteBuffer.put(checksum);
        byteBuffer.put(encryptedBytes);

        Files.write(outputFile, byteBuffer.array());
    }

    private static void zipDirectory(Path rootDir, Path currentDir, ZipOutputStream zos) throws IOException {
        try (var stream = Files.list(currentDir)) {
            for (Path path : stream.toList()) {
                String relativePath = rootDir.relativize(path).toString();
                if (Files.isDirectory(path)) {
                    // Add directory entry (with trailing slash)
                    zos.putNextEntry(new ZipEntry(relativePath + "/"));
                    zos.closeEntry();
                    // Recursively process subdirectory
                    zipDirectory(rootDir, path, zos);
                } else {
                    // Add file entry
                    zos.putNextEntry(new ZipEntry(relativePath));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    public static void decryptDirectory(Path inputFile, Path outputDir, SecretKey key) throws Exception {
        byte[] fileBytes = Files.readAllBytes(inputFile);

        ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes);
        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        byte[] storedChecksum = new byte[32];
        byteBuffer.get(storedChecksum);

        byte[] encryptedBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(encryptedBytes);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        // Verify checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] calculatedChecksum = digest.digest(decryptedBytes);

        if (!Arrays.equals(storedChecksum, calculatedChecksum)) {
            throw new SecurityException("Checksum verification failed. File may be corrupted or tampered with.");
        }

        // Extract the ZIP archive
        unzipToDirectory(decryptedBytes, outputDir);
    }

    private static void unzipToDirectory(byte[] zipData, Path outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = outputDir.resolve(entry.getName()).normalize();
                // Ensure the target path is within the output directory (prevent path traversal)
                if (!targetPath.startsWith(outputDir)) {
                    throw new IOException("Entry is outside of the target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    // Ensure parent directories exist
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath);
                }
                zis.closeEntry();
            }
        }
    }
}
