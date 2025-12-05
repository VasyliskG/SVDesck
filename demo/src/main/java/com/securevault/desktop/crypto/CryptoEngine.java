package com.securevault.desktop.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

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
}
