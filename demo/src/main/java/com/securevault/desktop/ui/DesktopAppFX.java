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
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
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

    // Icons as Unicode symbols
    private static final String ICON_DASHBOARD = "🏠";
    private static final String ICON_FILES = "📁";
    private static final String ICON_UPLOAD = "⬆️";
    private static final String ICON_DOWNLOAD = "⬇️";
    private static final String ICON_ENCRYPT = "🔐";
    private static final String ICON_SETTINGS = "⚙️";
    private static final String ICON_USER = "👤";
    private static final String ICON_LOCK = "🔒";
    private static final String ICON_REFRESH = "🔄";

    private final ObservableList<FileRecord> files = FXCollections.observableArrayList();
    private final TextArea logArea = new TextArea();
    private Label statusLabel;
    private String username = null;
    private VBox logPanel;
    private boolean logPanelExpanded = false;
    private TableView<FileRecord> tableView;
    private Button activeMenuItem;

    @Override
    public void start(Stage stage) {
        try {
            LocalFileStorage.init();
            log("Vault path: " + LocalFileStorage.getVaultPath());
        } catch (Exception e) {
            log("Failed to init vault: " + e.getMessage());
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-container");

        // Header
        HBox header = createHeader();
        root.setTop(header);

        // Sidebar
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        // Main content area
        VBox contentArea = createContentArea();
        root.setCenter(contentArea);

        // Log panel (collapsible, at bottom)
        logPanel = createLogPanel();
        root.setBottom(logPanel);

        Scene scene = new Scene(root, 1100, 700);
        
        // Load CSS stylesheet
        String css = getClass().getResource("/styles/app.css").toExternalForm();
        scene.getStylesheets().add(css);
        
        stage.setTitle("SecureVault Desktop");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    private HBox createHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);

        // Logo and title (left side)
        HBox logoSection = new HBox(10);
        logoSection.setAlignment(Pos.CENTER_LEFT);
        
        Label logoIcon = new Label(ICON_LOCK);
        logoIcon.getStyleClass().add("app-icon");
        
        Label title = new Label("SecureVault");
        title.getStyleClass().add("app-title");
        
        logoSection.getChildren().addAll(logoIcon, title);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // User status (right side)
        HBox userSection = new HBox(8);
        userSection.setAlignment(Pos.CENTER_RIGHT);
        
        Label userIcon = new Label(ICON_USER);
        userIcon.getStyleClass().add("user-icon");
        
        statusLabel = new Label("Not logged in");
        statusLabel.getStyleClass().add("user-status");
        
        userSection.getChildren().addAll(userIcon, statusLabel);

        header.getChildren().addAll(logoSection, spacer, userSection);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // Menu items
        Button dashboardBtn = createSidebarItem(ICON_DASHBOARD, "Dashboard");
        Button filesBtn = createSidebarItem(ICON_FILES, "My Files");
        Button uploadBtn = createSidebarItem(ICON_UPLOAD, "Upload");
        Button downloadBtn = createSidebarItem(ICON_DOWNLOAD, "Download");
        Button encryptBtn = createSidebarItem(ICON_ENCRYPT, "Encrypt/Decrypt");
        Button settingsBtn = createSidebarItem(ICON_SETTINGS, "Settings");

        // Set Dashboard as default active
        dashboardBtn.getStyleClass().add("active");
        activeMenuItem = dashboardBtn;

        // Separator before settings
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Separator separator = new Separator();
        separator.setPadding(new Insets(8, 16, 8, 16));

        // Login/Register section
        VBox authSection = new VBox(4);
        authSection.setPadding(new Insets(8, 16, 16, 16));
        
        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().addAll("button", "button-primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setOnAction(e -> showLogin(false));
        
        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("button");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setOnAction(e -> showLogin(true));
        
        authSection.getChildren().addAll(loginBtn, registerBtn);

        sidebar.getChildren().addAll(
            dashboardBtn, filesBtn, uploadBtn, downloadBtn, encryptBtn,
            spacer, separator, settingsBtn, authSection
        );

        // Sidebar item actions
        dashboardBtn.setOnAction(e -> handleMenuClick(dashboardBtn, "dashboard"));
        filesBtn.setOnAction(e -> {
            handleMenuClick(filesBtn, "files");
            refreshFiles();
        });
        uploadBtn.setOnAction(e -> {
            handleMenuClick(uploadBtn, "upload");
            uploadFile();
        });
        downloadBtn.setOnAction(e -> {
            handleMenuClick(downloadBtn, "download");
            downloadSelected(tableView.getSelectionModel().getSelectedItem());
        });
        encryptBtn.setOnAction(e -> {
            handleMenuClick(encryptBtn, "encrypt");
            decryptSelected(tableView.getSelectionModel().getSelectedItem());
        });

        return sidebar;
    }

    private Button createSidebarItem(String icon, String text) {
        Button btn = new Button(icon + "  " + text);
        btn.getStyleClass().add("sidebar-item");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        return btn;
    }

    private void handleMenuClick(Button clicked, String section) {
        if (activeMenuItem != null) {
            activeMenuItem.getStyleClass().remove("active");
        }
        clicked.getStyleClass().add("active");
        activeMenuItem = clicked;
    }

    private VBox createContentArea() {
        VBox contentArea = new VBox(20);
        contentArea.getStyleClass().add("content-area");
        contentArea.setPadding(new Insets(24));

        // Page title
        Label pageTitle = new Label("My Files");
        pageTitle.getStyleClass().add("card-title");
        pageTitle.setStyle("-fx-font-size: 24px;");

        // Action bar
        HBox actionBar = new HBox(12);
        actionBar.getStyleClass().add("action-bar");
        actionBar.setAlignment(Pos.CENTER_LEFT);

        Button refreshBtn = new Button(ICON_REFRESH + " Refresh");
        refreshBtn.getStyleClass().addAll("button", "button-primary");
        refreshBtn.setOnAction(e -> refreshFiles());

        Button uploadActionBtn = new Button(ICON_UPLOAD + " Upload File");
        uploadActionBtn.getStyleClass().add("button");
        uploadActionBtn.setOnAction(e -> uploadFile());

        Button downloadActionBtn = new Button(ICON_DOWNLOAD + " Download");
        downloadActionBtn.getStyleClass().add("button");
        downloadActionBtn.setOnAction(e -> downloadSelected(tableView.getSelectionModel().getSelectedItem()));

        Button decryptActionBtn = new Button(ICON_ENCRYPT + " Decrypt");
        decryptActionBtn.getStyleClass().add("button");
        decryptActionBtn.setOnAction(e -> decryptSelected(tableView.getSelectionModel().getSelectedItem()));

        actionBar.getChildren().addAll(refreshBtn, uploadActionBtn, downloadActionBtn, decryptActionBtn);

        // File table card
        VBox tableCard = new VBox(0);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        tableView = createFileTable();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        
        tableCard.getChildren().add(tableView);

        contentArea.getChildren().addAll(pageTitle, actionBar, tableCard);
        return contentArea;
    }

    private TableView<FileRecord> createFileTable() {
        TableView<FileRecord> table = new TableView<>(files);
        table.getStyleClass().add("table-view");

        // ID Column
        TableColumn<FileRecord, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(60);
        idCol.setMinWidth(60);
        idCol.setMaxWidth(80);

        // File icon + name column
        TableColumn<FileRecord, String> nameCol = new TableColumn<>("Filename");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("filenameEnc"));
        nameCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(ICON_FILES + "  " + item);
                }
            }
        });
        nameCol.setPrefWidth(300);

        // Size column
        TableColumn<FileRecord, Long> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatFileSize(item));
                }
            }
        });
        sizeCol.setPrefWidth(100);

        // Uploaded at column
        TableColumn<FileRecord, String> atCol = new TableColumn<>("Uploaded At");
        atCol.setCellValueFactory(new PropertyValueFactory<>("uploadedAt"));
        atCol.setPrefWidth(180);

        table.getColumns().addAll(idCol, nameCol, sizeCol, atCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Placeholder when no files
        Label placeholder = new Label("No files yet. Upload a file to get started.");
        placeholder.setStyle("-fx-text-fill: #666666; -fx-font-size: 14px;");
        table.setPlaceholder(placeholder);

        return table;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private VBox createLogPanel() {
        VBox panel = new VBox();
        panel.getStyleClass().add("log-panel");
        panel.setMaxHeight(40); // Collapsed by default

        // Header with toggle button
        HBox logHeader = new HBox();
        logHeader.getStyleClass().add("log-header");
        logHeader.setAlignment(Pos.CENTER_LEFT);

        Label logTitle = new Label("📋 Activity Log");
        logTitle.getStyleClass().add("log-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button toggleBtn = new Button("▲ Expand");
        toggleBtn.getStyleClass().add("log-toggle-button");
        toggleBtn.setOnAction(e -> {
            logPanelExpanded = !logPanelExpanded;
            if (logPanelExpanded) {
                panel.setMaxHeight(200);
                panel.setPrefHeight(200);
                toggleBtn.setText("▼ Collapse");
            } else {
                panel.setMaxHeight(40);
                panel.setPrefHeight(40);
                toggleBtn.setText("▲ Expand");
            }
        });

        logHeader.getChildren().addAll(logTitle, spacer, toggleBtn);

        // Log content
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("text-area");
        logArea.setPrefHeight(160);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        panel.getChildren().addAll(logHeader, logArea);
        logArea.setVisible(false);
        logArea.setManaged(false);

        toggleBtn.setOnAction(e -> {
            logPanelExpanded = !logPanelExpanded;
            if (logPanelExpanded) {
                panel.setMaxHeight(200);
                panel.setPrefHeight(200);
                logArea.setVisible(true);
                logArea.setManaged(true);
                toggleBtn.setText("▼ Collapse");
            } else {
                panel.setMaxHeight(40);
                panel.setPrefHeight(40);
                logArea.setVisible(false);
                logArea.setManaged(false);
                toggleBtn.setText("▲ Expand");
            }
        });

        return panel;
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
        dialog.setTitle(ICON_ENCRYPT + " Encryption Password");
        ButtonType ok = new ButtonType("Encrypt & Upload", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        
        // Styled content
        VBox content = new VBox(16);
        content.setPadding(new Insets(16, 0, 8, 0));
        
        Label titleLabel = new Label("Enter password to encrypt file");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        
        PasswordField pwd = new PasswordField();
        pwd.setPromptText("Password to encrypt file");
        pwd.getStyleClass().add("password-field");
        
        content.getChildren().addAll(titleLabel, pwd);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        
        // Style buttons
        dialog.getDialogPane().lookupButton(ok).getStyleClass().addAll("button", "button-primary");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("button");
        
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
        dialog.setTitle(register ? ICON_USER + " Register" : ICON_USER + " Login");

        ButtonType okType = new ButtonType(register ? "Register" : "Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        // Styled form
        VBox box = new VBox(16);
        box.setPadding(new Insets(16, 0, 8, 0));
        
        Label userLabel = new Label("Username");
        userLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        TextField userField = new TextField();
        userField.setPromptText("Enter your username");
        userField.getStyleClass().add("text-field");
        
        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Enter your password");
        passField.getStyleClass().add("password-field");
        
        box.getChildren().addAll(userLabel, userField, passLabel, passField);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        
        // Style buttons
        dialog.getDialogPane().lookupButton(okType).getStyleClass().addAll("button", "button-primary");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("button");

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

        // Styled password dialog for decryption
        Dialog<String> pwd = new Dialog<>();
        pwd.setTitle(ICON_ENCRYPT + " Decryption Password");
        ButtonType decryptBtn = new ButtonType("Decrypt", ButtonBar.ButtonData.OK_DONE);
        pwd.getDialogPane().getButtonTypes().addAll(decryptBtn, ButtonType.CANCEL);
        
        VBox pwdContent = new VBox(16);
        pwdContent.setPadding(new Insets(16, 0, 8, 0));
        
        Label pwdLabel = new Label("Enter password used to encrypt the file");
        pwdLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        
        PasswordField pwdField = new PasswordField();
        pwdField.setPromptText("Decryption password");
        pwdField.getStyleClass().add("password-field");
        
        pwdContent.getChildren().addAll(pwdLabel, pwdField);
        pwd.getDialogPane().setContent(pwdContent);
        pwd.getDialogPane().getStyleClass().add("dialog-pane");
        
        pwd.getDialogPane().lookupButton(decryptBtn).getStyleClass().addAll("button", "button-primary");
        pwd.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("button");
        
        pwd.setResultConverter(btn -> btn == decryptBtn ? pwdField.getText() : null);
        
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
            a.setTitle("⚠️ Error");
            a.setHeaderText(title);
            a.setContentText(t == null ? title : t.getMessage());
            a.getDialogPane().getStyleClass().add("dialog-pane");
            a.getDialogPane().setMinWidth(400);
            a.showAndWait();
            if (t != null) log(title + ": " + t.getMessage());
        });
    }

    private void showInfo(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("ℹ️ Information");
            a.setHeaderText(null);
            a.setContentText(msg);
            a.getDialogPane().getStyleClass().add("dialog-pane");
            a.getDialogPane().setMinWidth(400);
            a.showAndWait();
        });
    }

    private void showSuccess(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("✓ Success");
            a.setHeaderText(null);
            a.setContentText(msg);
            a.getDialogPane().getStyleClass().add("dialog-pane");
            a.getDialogPane().setMinWidth(400);
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
