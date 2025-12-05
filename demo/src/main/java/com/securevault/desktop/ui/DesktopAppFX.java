package com.securevault.desktop.ui;

import com.securevault.desktop.crypto.CryptoEngine;
import com.securevault.desktop.crypto.KeyDerivation;
import com.securevault.desktop.network.ApiClient;
import com.securevault.desktop.storage.ConfigurationManager;
import com.securevault.desktop.storage.LocalFileStorage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;

import javax.crypto.SecretKey;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DesktopAppFX extends Application {

    private final ObservableList<FileRecord> files = FXCollections.observableArrayList();
    private final TextArea logArea = new TextArea();
    private Label statusLabel = new Label("Not logged in");
    private String username = null;

    @Override
    public void start(Stage stage) {
        try {
            LocalFileStorage.init();
            log("Vault path: " + LocalFileStorage.getVaultPath());
        } catch (Exception e) {
            log("Failed to init vault: " + e.getMessage());
        }

        BorderPane root = new BorderPane();
        HBox top = new HBox(8);
        top.setPadding(new Insets(8));

    Button loginBtn = new Button("Login");
    Button registerBtn = new Button("Register");
    Button refreshBtn = new Button("Refresh Files");
    Button uploadBtn = new Button("Upload");
    Button downloadBtn = new Button("Download");
    Button decryptBtn = new Button("Decrypt");

    top.getChildren().addAll(loginBtn, registerBtn, refreshBtn, uploadBtn, downloadBtn, decryptBtn, statusLabel);

        TableView<FileRecord> tableView = new TableView<>(files);
        TableColumn<FileRecord, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<FileRecord, String> nameCol = new TableColumn<>("Encrypted Filename");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("filenameEnc"));
        TableColumn<FileRecord, Long> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        TableColumn<FileRecord, String> atCol = new TableColumn<>("Uploaded At");
        atCol.setCellValueFactory(new PropertyValueFactory<>("uploadedAt"));

        tableView.getColumns().addAll(idCol, nameCol, sizeCol, atCol);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        logArea.setEditable(false);
        logArea.setWrapText(true);

        VBox center = new VBox(8, tableView);
        center.setPadding(new Insets(8));

        root.setTop(top);
        root.setCenter(center);
        root.setBottom(logArea);

        loginBtn.setOnAction(e -> showLogin(false));
        registerBtn.setOnAction(e -> showLogin(true));
        refreshBtn.setOnAction(e -> refreshFiles());
    uploadBtn.setOnAction(e -> uploadFile());
        downloadBtn.setOnAction(e -> downloadSelected(tableView.getSelectionModel().getSelectedItem()));
        decryptBtn.setOnAction(e -> decryptSelected(tableView.getSelectionModel().getSelectedItem()));

        Scene scene = new Scene(root, 900, 600);
        stage.setTitle("SecureVault Desktop (JavaFX)");
        stage.setScene(scene);
        stage.show();
    }

    private void uploadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to upload");
        File selected = chooser.showOpenDialog(null);
        if (selected == null) {
            showInfo("No file selected");
            return;
        }

        // ask for password to encrypt before upload
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle("Encryption password");
        ButtonType ok = new ButtonType("Encrypt & Upload", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        PasswordField pwd = new PasswordField();
        pwd.setPromptText("Password to encrypt file");
        dialog.getDialogPane().setContent(new VBox(8, new Label("Enter password to encrypt file before upload"), pwd));
        dialog.setResultConverter(btn -> btn == ok ? pwd.getText().toCharArray() : null);
        Optional<char[]> res = dialog.showAndWait();
        if (res.isEmpty()) return;
        char[] password = res.get();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String token = ConfigurationManager.loadToken();
                if (token == null) throw new IllegalStateException("Not logged in");

                // encrypt into vault
                Path outPath = LocalFileStorage.getVaultPath().resolve(selected.getName() + ".enc");
                javax.crypto.SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                CryptoEngine.encryptFile(selected.toPath(), outPath, key);

                try (ApiClient c = new ApiClient(token)) {
                    c.uploadFile(outPath);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            log("Upload successful: " + selected.getName());
            showInfo("Upload successful");
            refreshFiles();
        });
        task.setOnFailed(e -> showError("Upload failed", task.getException()));
        new Thread(task).start();
    }

    private void showLogin(boolean register) {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle(register ? "Register" : "Login");

        ButtonType okType = new ButtonType(register ? "Register" : "Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        TextField userField = new TextField();
        PasswordField passField = new PasswordField();

        VBox box = new VBox(8, new Label("Username:"), userField, new Label("Password:"), passField);
        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(btn -> {
            if (btn == okType) return new Pair<>(userField.getText(), passField.getText());
            return null;
        });

        Optional<Pair<String, String>> res = dialog.showAndWait();
        res.ifPresent(pair -> {
            String user = pair.getKey();
            String pass = pair.getValue();
            if (register) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        try (ApiClient client = new ApiClient()) {
                            client.register(user, pass);
                        }
                        return null;
                    }
                };
                task.setOnSucceeded(ev -> log("Registration successful - please login."));
                task.setOnFailed(ev -> showError("Registration failed", task.getException()));
                new Thread(task).start();
            } else {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        try (ApiClient c = new ApiClient()) {
                            String token = c.login(user, pass);
                            ConfigurationManager.saveToken(token);
                            username = user;
                            Platform.runLater(() -> statusLabel.setText("Logged in as " + user));
                            log("Login successful.");
                        }
                        return null;
                    }
                };
                task.setOnFailed(ev -> showError("Login failed", task.getException()));
                new Thread(task).start();
            }
        });
    }

    private void refreshFiles() {
        Task<List<Map<String, Object>>> task = new Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws Exception {
                String token = ConfigurationManager.loadToken();
                if (token == null) throw new IllegalStateException("Not logged in");
                try (ApiClient c = new ApiClient(token)) {
                    return c.listFiles();
                }
            }
        };
        task.setOnSucceeded(e -> {
            files.clear();
            for (Map<String, Object> f : task.getValue()) {
                files.add(new FileRecord(
                        Long.parseLong(f.get("id").toString()),
                        f.get("filenameEnc").toString(),
                        Long.parseLong(f.getOrDefault("size", "0").toString()),
                        f.getOrDefault("uploadedAt", "").toString()
                ));
            }
            log("Refreshed file list: " + files.size());
        });
        task.setOnFailed(e -> showError("Failed to refresh files", task.getException()));
        new Thread(task).start();
    }

    private void downloadSelected(FileRecord selected) {
        if (selected == null) {
            showInfo("Select a file first");
            return;
        }
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                String token = ConfigurationManager.loadToken();
                if (token == null) throw new IllegalStateException("Not logged in");
                try (ApiClient c = new ApiClient(token)) {
                    return c.downloadFile(selected.getId());
                }
            }
        };
        task.setOnSucceeded(e -> {
            Path p = task.getValue();
            log("Downloaded to: " + p);
            showInfo("Downloaded to: " + p);
        });
        task.setOnFailed(e -> showError("Download failed", task.getException()));
        new Thread(task).start();
    }

    private void decryptSelected(FileRecord selected) {
        if (selected == null) {
            showInfo("Select a file first");
            return;
        }

        // choose file from vault
        Path vault = LocalFileStorage.getVaultPath();
        File encFile = vault.resolve(selected.getFilenameEnc()).toFile();
        if (!encFile.exists()) {
            showError("Encrypted file not found in vault", null);
            return;
        }

        TextInputDialog pwd = new TextInputDialog();
        pwd.setTitle("Decryption password");
        pwd.setHeaderText("Enter password used to encrypt the file");
        Optional<String> res = pwd.showAndWait();
        if (res.isEmpty() || res.get().isEmpty()) return;
        char[] password = res.get().toCharArray();

        String filename = selected.getFilenameEnc();
        if (filename == null || filename.length() < 5 || !filename.endsWith(".enc")) {
            showError("Invalid encrypted filename format", null);
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose output directory");
        File outDir = chooser.showDialog(null);
        if (outDir == null) {
            showInfo("Output directory not selected");
            return;
        }
        Path output = outDir.toPath().resolve(filename.substring(0, filename.length() - 4));

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                CryptoEngine.decryptFile(encFile.toPath(), output, key);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            log("Decrypted to: " + output);
            showInfo("Decrypted to: " + output);
        });
        task.setOnFailed(e -> showError("Decryption failed", task.getException()));
        new Thread(task).start();
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private void showError(String title, Throwable t) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle(title);
            a.setHeaderText(t == null ? title : t.getMessage());
            a.showAndWait();
            if (t != null) log(title + ": " + t.getMessage());
        });
    }

    private void showInfo(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class FileRecord {
        private final Long id;
        private final String filenameEnc;
        private final Long size;
        private final String uploadedAt;

        public FileRecord(Long id, String filenameEnc, Long size, String uploadedAt) {
            this.id = id;
            this.filenameEnc = filenameEnc;
            this.size = size;
            this.uploadedAt = uploadedAt;
        }

        public Long getId() { return id; }
        public String getFilenameEnc() { return filenameEnc; }
        public Long getSize() { return size; }
        public String getUploadedAt() { return uploadedAt; }
    }
}
