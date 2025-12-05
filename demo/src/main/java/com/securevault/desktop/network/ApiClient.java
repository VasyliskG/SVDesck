package com.securevault.desktop.network;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.desktop.storage.LocalFileStorage;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ApiClient implements AutoCloseable {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String token;

    public ApiClient(String token) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.baseUrl = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080");
        this.token = token;
    }

    public ApiClient() {
        this(null);
    }

    public void register(String username, String password) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.get("application/json")
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/api/auth/register")
                .post(requestBody)
                .build();

        executeRequest(request, 201);
    }

    public String login(String username, String password) throws IOException {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.get("application/json")
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/api/auth/login")
                .post(requestBody)
                .build();

        try (Response response = executeRequest(request, 200)) {
            Map<String, String> responseBody = objectMapper.readValue(response.body().string(), new TypeReference<>() {});
            return responseBody.get("token");
        }
    }

    public void uploadFile(Path filePath) throws Exception {
        byte[] fileBytes = Files.readAllBytes(filePath);

        // Extract checksum from the encrypted file itself
        byte[] storedChecksum = new byte[32];
        System.arraycopy(fileBytes, 12, storedChecksum, 0, 32);
        String checksumHex = bytesToHex(storedChecksum);

        RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse("application/octet-stream"));

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filePath.getFileName().toString(), fileBody)
                .addFormDataPart("filename_enc", filePath.getFileName().toString())
                .addFormDataPart("checksum", checksumHex)
                .build();

        Request request = new Request.Builder()
                .url(baseUrl + "/api/files/upload")
                .header("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        executeRequest(request, 201);
    }

    public java.nio.file.Path downloadFile(Long fileId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/files/" + fileId)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = executeRequest(request, 200)) {
            // Need to retrieve the real filename from headers or list command
            List<Map<String, Object>> remoteFiles = listFiles();
            String filename = remoteFiles.stream()
                .filter(f -> Objects.equals(f.get("id").toString(), fileId.toString()))
                .map(f -> f.get("filenameEnc").toString())
                .findFirst()
                .orElse("downloaded_file_" + fileId + ".enc");

            java.nio.file.Path outputPath = LocalFileStorage.getVaultPath().resolve(filename);
            Files.write(outputPath, response.body().bytes());
            return outputPath;
        }
    }

    public List<Map<String, Object>> listFiles() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/files")
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        try (Response response = executeRequest(request, 200)) {
            return objectMapper.readValue(response.body().string(), new TypeReference<>() {});
        }
    }

    public void deleteFile(Long fileId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/files/" + fileId)
                .header("Authorization", "Bearer " + token)
                .delete()
                .build();
        executeRequest(request, 204);
    }


    private Response executeRequest(Request request, int expectedStatusCode) throws IOException {
        Response response = client.newCall(request).execute();
        if (response.code() != expectedStatusCode) {
            String errorBody = response.body() != null ? response.body().string() : "No error body";
            throw new IOException("Unexpected status code: " + response.code() + ". Body: " + errorBody);
        }
        return response;
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
    
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
