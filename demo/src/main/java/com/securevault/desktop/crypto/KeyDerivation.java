package com.securevault.desktop.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class KeyDerivation {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32; // AES-256 key
    private static final int ITERATIONS = 10;
    private static final int MEMORY = 65536; // 64 MB
    private static final int PARALLELISM = 1;

    public static SecretKey deriveKeyFromPassword(char[] password) {
        // For simplicity, using a static salt. IN A REAL-WORLD SCENARIO, GENERATE AND STORE A UNIQUE SALT.
        byte[] salt = "static-salt-for-mvp-demo-app".getBytes(StandardCharsets.UTF_8);

        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY)
                .withParallelism(PARALLELISM)
                .withSalt(salt);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] hash = new byte[HASH_LENGTH];
        generator.generateBytes(password, hash);

        return new SecretKeySpec(hash, "AES");
    }
}
