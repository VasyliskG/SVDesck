package com.securevault.desktop.ui;

import com.securevault.desktop.crypto.CryptoEngine;
import com.securevault.desktop.crypto.KeyDerivation;
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

import javax.crypto.SecretKey;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class DesktopAppFX extends Application {

    // Icons as Unicode symbols
    private static final String ICON_DASHBOARD = "🏠";
    private static final String ICON_FILES = "📁";
    private static final String ICON_ENCRYPT = "🔐";
    private static final String ICON_DECRYPT = "🔓";
    private static final String ICON_VIEWER = "👁️";
    private static final String ICON_LOCK = "🔒";

    // File extensions
    private static final String ENCRYPTED_FILE_EXTENSION = ".enc";
    private static final String ENCRYPTED_DIR_EXTENSION = ".encdir";

    private final ObservableList<FileRecord> files = FXCollections.observableArrayList();
    private final TextArea logArea = new TextArea();
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
        
        // Load local encrypted files on startup
        refreshLocalFiles();
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

        // App description (right side)
        HBox descSection = new HBox(8);
        descSection.setAlignment(Pos.CENTER_RIGHT);
        
        Label descLabel = new Label("Local File Encryption");
        descLabel.getStyleClass().add("user-status");
        
        descSection.getChildren().add(descLabel);

        header.getChildren().addAll(logoSection, spacer, descSection);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // Menu items
        Button dashboardBtn = createSidebarItem(ICON_DASHBOARD, "Dashboard");
        Button encryptBtn = createSidebarItem(ICON_ENCRYPT, "Encrypt");
        Button decryptBtn = createSidebarItem(ICON_DECRYPT, "Decrypt");
        Button viewerBtn = createSidebarItem(ICON_VIEWER, "File Viewer");

        // Set Dashboard as default active
        dashboardBtn.getStyleClass().add("active");
        activeMenuItem = dashboardBtn;

        // Separator before spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
            dashboardBtn, encryptBtn, decryptBtn, viewerBtn,
            spacer
        );

        // Sidebar item actions
        dashboardBtn.setOnAction(e -> handleMenuClick(dashboardBtn, "dashboard"));
        encryptBtn.setOnAction(e -> {
            handleMenuClick(encryptBtn, "encrypt");
            showEncryptDialog();
        });
        decryptBtn.setOnAction(e -> {
            handleMenuClick(decryptBtn, "decrypt");
            decryptFileOrDirectory();
        });
        viewerBtn.setOnAction(e -> {
            handleMenuClick(viewerBtn, "viewer");
            refreshLocalFiles();
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
        Label pageTitle = new Label("File Encryption");
        pageTitle.getStyleClass().add("card-title");
        pageTitle.setStyle("-fx-font-size: 24px;");

        // Action bar
        HBox actionBar = new HBox(12);
        actionBar.getStyleClass().add("action-bar");
        actionBar.setAlignment(Pos.CENTER_LEFT);

        Button encryptActionBtn = new Button(ICON_ENCRYPT + " Encrypt");
        encryptActionBtn.getStyleClass().addAll("button", "button-primary");
        encryptActionBtn.setOnAction(e -> showEncryptDialog());

        Button decryptActionBtn = new Button(ICON_DECRYPT + " Decrypt");
        decryptActionBtn.getStyleClass().addAll("button", "button-success");
        decryptActionBtn.setOnAction(e -> decryptFileOrDirectory());

        actionBar.getChildren().addAll(encryptActionBtn, decryptActionBtn);

        // File table card (for future file viewer functionality)
        VBox tableCard = new VBox(0);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label tableTitle = new Label(ICON_FILES + " Local Encrypted Files");
        tableTitle.getStyleClass().add("card-title");
        tableTitle.setPadding(new Insets(0, 0, 12, 0));

        tableView = createFileTable();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        
        tableCard.getChildren().addAll(tableTitle, tableView);

        contentArea.getChildren().addAll(pageTitle, actionBar, tableCard);
        return contentArea;
    }

    private TableView<FileRecord> createFileTable() {
        TableView<FileRecord> table = new TableView<>(files);
        table.getStyleClass().add("table-view");

        // File icon + name column
        TableColumn<FileRecord, String> nameCol = new TableColumn<>("Filename");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("filename"));
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
        nameCol.setPrefWidth(350);

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

        // Path column
        TableColumn<FileRecord, String> pathCol = new TableColumn<>("Location");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        pathCol.setPrefWidth(300);

        table.getColumns().add(nameCol);
        table.getColumns().add(sizeCol);
        table.getColumns().add(pathCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Placeholder when no files
        Label placeholder = new Label("No encrypted files found in vault. Encrypt a file to get started.");
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

    private void showEncryptDialog() {
        // Show dialog to choose between file or directory
        Alert choiceDialog = new Alert(Alert.AlertType.CONFIRMATION);
        choiceDialog.setTitle(ICON_ENCRYPT + " Encrypt");
        choiceDialog.setHeaderText("What do you want to encrypt?");
        choiceDialog.setContentText("Choose whether to encrypt a file or a directory.");

        ButtonType fileButton = new ButtonType("File");
        ButtonType directoryButton = new ButtonType("Directory");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        choiceDialog.getButtonTypes().setAll(fileButton, directoryButton, cancelButton);
        choiceDialog.getDialogPane().getStyleClass().add("dialog-pane");
        choiceDialog.getDialogPane().setMinWidth(400);

        Optional<ButtonType> result = choiceDialog.showAndWait();
        if (result.isPresent()) {
            if (result.get() == fileButton) {
                encryptFile();
            } else if (result.get() == directoryButton) {
                encryptDirectory();
            }
        }
    }

    private void encryptFile() {
        // Step 1: Select file to encrypt
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to encrypt");
        File selected = chooser.showOpenDialog(null);
        if (selected == null) {
            return;
        }

        // Step 2: Password dialog with confirmation
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(ICON_ENCRYPT + " Encryption Password");
        ButtonType ok = new ButtonType("Encrypt", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        
        VBox content = new VBox(16);
        content.setPadding(new Insets(16, 0, 8, 0));
        
        Label titleLabel = new Label("Enter password to encrypt file: " + selected.getName());
        titleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        
        Label pwd1Label = new Label("Password");
        pwd1Label.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        PasswordField pwd1 = new PasswordField();
        pwd1.setPromptText("Enter password");
        pwd1.getStyleClass().add("password-field");
        
        Label pwd2Label = new Label("Confirm Password");
        pwd2Label.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        PasswordField pwd2 = new PasswordField();
        pwd2.setPromptText("Confirm password");
        pwd2.getStyleClass().add("password-field");
        
        content.getChildren().addAll(titleLabel, pwd1Label, pwd1, pwd2Label, pwd2);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        
        dialog.getDialogPane().lookupButton(ok).getStyleClass().addAll("button", "button-primary");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("button");
        
        dialog.setResultConverter(btn -> {
            if (btn == ok) {
                char[] p1 = pwd1.getText().toCharArray();
                char[] p2 = pwd2.getText().toCharArray();
                if (p1.length == 0) {
                    showError("Password cannot be empty", null);
                    return null;
                }
                if (!java.util.Arrays.equals(p1, p2)) {
                    showError("Passwords do not match", null);
                    return null;
                }
                return p1;
            }
            return null;
        });
        
        Optional<char[]> res = dialog.showAndWait();
        if (res.isEmpty() || res.get() == null) return;
        char[] password = res.get();

        // Step 3: Select output directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Choose output directory for encrypted file");
        dirChooser.setInitialDirectory(LocalFileStorage.getVaultPath().toFile());
        File outDir = dirChooser.showDialog(null);
        if (outDir == null) {
            showInfo("Output directory not selected");
            return;
        }

        Path outputPath = outDir.toPath().resolve(selected.getName() + ENCRYPTED_FILE_EXTENSION);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                CryptoEngine.encryptFile(selected.toPath(), outputPath, key);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            log("File encrypted: " + outputPath);
            showInfo("File encrypted successfully!\nSaved to: " + outputPath);
            refreshLocalFiles();
        });
        task.setOnFailed(e -> showError("Encryption failed", task.getException()));
        new Thread(task).start();
    }

    private void encryptDirectory() {
        // Step 1: Select directory to encrypt
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select directory to encrypt");
        File selected = chooser.showDialog(null);
        if (selected == null) {
            return;
        }

        // Step 2: Password dialog with confirmation
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(ICON_ENCRYPT + " Encryption Password");
        ButtonType ok = new ButtonType("Encrypt", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        
        VBox content = new VBox(16);
        content.setPadding(new Insets(16, 0, 8, 0));
        
        Label titleLabel = new Label("Enter password to encrypt directory: " + selected.getName());
        titleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        
        Label pwd1Label = new Label("Password");
        pwd1Label.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        PasswordField pwd1 = new PasswordField();
        pwd1.setPromptText("Enter password");
        pwd1.getStyleClass().add("password-field");
        
        Label pwd2Label = new Label("Confirm Password");
        pwd2Label.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        PasswordField pwd2 = new PasswordField();
        pwd2.setPromptText("Confirm password");
        pwd2.getStyleClass().add("password-field");
        
        content.getChildren().addAll(titleLabel, pwd1Label, pwd1, pwd2Label, pwd2);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        
        dialog.getDialogPane().lookupButton(ok).getStyleClass().addAll("button", "button-primary");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("button");
        
        dialog.setResultConverter(btn -> {
            if (btn == ok) {
                char[] p1 = pwd1.getText().toCharArray();
                char[] p2 = pwd2.getText().toCharArray();
                if (p1.length == 0) {
                    showError("Password cannot be empty", null);
                    return null;
                }
                if (!java.util.Arrays.equals(p1, p2)) {
                    showError("Passwords do not match", null);
                    return null;
                }
                return p1;
            }
            return null;
        });
        
        Optional<char[]> res = dialog.showAndWait();
        if (res.isEmpty() || res.get() == null) return;
        char[] password = res.get();

        // Step 3: Select output directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Choose output directory for encrypted directory archive");
        dirChooser.setInitialDirectory(LocalFileStorage.getVaultPath().toFile());
        File outDir = dirChooser.showDialog(null);
        if (outDir == null) {
            showInfo("Output directory not selected");
            return;
        }

        Path outputPath = outDir.toPath().resolve(selected.getName() + ENCRYPTED_DIR_EXTENSION);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                CryptoEngine.encryptDirectory(selected.toPath(), outputPath, key);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            log("Directory encrypted: " + outputPath);
            showInfo("Directory encrypted successfully!\nSaved to: " + outputPath);
            refreshLocalFiles();
        });
        task.setOnFailed(e -> showError("Encryption failed", task.getException()));
        new Thread(task).start();
    }

    private void decryptFileOrDirectory() {
        // Step 1: Select encrypted file (either .enc or .encdir)
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select encrypted file (" + ENCRYPTED_FILE_EXTENSION + " or " + ENCRYPTED_DIR_EXTENSION + ")");
        chooser.setInitialDirectory(LocalFileStorage.getVaultPath().toFile());
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Encrypted Files", "*" + ENCRYPTED_FILE_EXTENSION, "*" + ENCRYPTED_DIR_EXTENSION),
            new FileChooser.ExtensionFilter("Encrypted Files", "*" + ENCRYPTED_FILE_EXTENSION),
            new FileChooser.ExtensionFilter("Encrypted Directories", "*" + ENCRYPTED_DIR_EXTENSION)
        );
        File selected = chooser.showOpenDialog(null);
        if (selected == null) {
            return;
        }

        String fileName = selected.getName();
        if (fileName.endsWith(ENCRYPTED_DIR_EXTENSION)) {
            decryptDirectory(selected);
        } else if (fileName.endsWith(ENCRYPTED_FILE_EXTENSION)) {
            decryptFile(selected);
        } else {
            showError("Selected file is not an " + ENCRYPTED_FILE_EXTENSION + " or " + ENCRYPTED_DIR_EXTENSION + " file", null);
        }
    }

    private void decryptFile(File selected) {
        if (selected == null) {
            return;
        }

        if (!selected.getName().endsWith(ENCRYPTED_FILE_EXTENSION)) {
            showError("Selected file is not an " + ENCRYPTED_FILE_EXTENSION + " file", null);
            return;
        }

        // Step 2: Password dialog
        Dialog<String> pwd = new Dialog<>();
        pwd.setTitle(ICON_DECRYPT + " Decryption Password");
        ButtonType decryptBtn = new ButtonType("Decrypt", ButtonBar.ButtonData.OK_DONE);
        pwd.getDialogPane().getButtonTypes().addAll(decryptBtn, ButtonType.CANCEL);
        
        VBox pwdContent = new VBox(16);
        pwdContent.setPadding(new Insets(16, 0, 8, 0));
        
        Label pwdTitleLabel = new Label("Enter password for: " + selected.getName());
        pwdTitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        
        Label pwdLabel = new Label("Password");
        pwdLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        PasswordField pwdField = new PasswordField();
        pwdField.setPromptText("Decryption password");
        pwdField.getStyleClass().add("password-field");
        
        pwdContent.getChildren().addAll(pwdTitleLabel, pwdLabel, pwdField);
        pwd.getDialogPane().setContent(pwdContent);
        pwd.getDialogPane().getStyleClass().add("dialog-pane");
        
        pwd.getDialogPane().lookupButton(decryptBtn).getStyleClass().addAll("button", "button-primary");
        pwd.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("button");
        
        pwd.setResultConverter(btn -> btn == decryptBtn ? pwdField.getText() : null);
        
        Optional<String> pwdRes = pwd.showAndWait();
        if (pwdRes.isEmpty() || pwdRes.get().isEmpty()) {
            return;
        }
        char[] password = pwdRes.get().toCharArray();

        // Step 3: Select output directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Choose output directory for decrypted file");
        File outDir = dirChooser.showDialog(null);
        if (outDir == null) {
            showInfo("Output directory not selected");
            return;
        }

        // Remove .enc extension for output filename
        String originalName = selected.getName();
        if (originalName.endsWith(ENCRYPTED_FILE_EXTENSION)) {
            originalName = originalName.substring(0, originalName.length() - ENCRYPTED_FILE_EXTENSION.length());
        }
        Path outputPath = outDir.toPath().resolve(originalName);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                CryptoEngine.decryptFile(selected.toPath(), outputPath, key);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            log("File decrypted: " + outputPath);
            showInfo("File decrypted successfully!\nSaved to: " + outputPath);
        });
        task.setOnFailed(e -> showError("Decryption failed", task.getException()));
        new Thread(task).start();
    }

    private void decryptDirectory(File selected) {
        if (selected == null) {
            return;
        }

        if (!selected.getName().endsWith(ENCRYPTED_DIR_EXTENSION)) {
            showError("Selected file is not an " + ENCRYPTED_DIR_EXTENSION + " file", null);
            return;
        }

        // Step 2: Password dialog
        Dialog<String> pwd = new Dialog<>();
        pwd.setTitle(ICON_DECRYPT + " Decryption Password");
        ButtonType decryptBtn = new ButtonType("Decrypt", ButtonBar.ButtonData.OK_DONE);
        pwd.getDialogPane().getButtonTypes().addAll(decryptBtn, ButtonType.CANCEL);
        
        VBox pwdContent = new VBox(16);
        pwdContent.setPadding(new Insets(16, 0, 8, 0));
        
        Label pwdTitleLabel = new Label("Enter password for: " + selected.getName());
        pwdTitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333333;");
        
        Label pwdLabel = new Label("Password");
        pwdLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-weight: bold;");
        PasswordField pwdField = new PasswordField();
        pwdField.setPromptText("Decryption password");
        pwdField.getStyleClass().add("password-field");
        
        pwdContent.getChildren().addAll(pwdTitleLabel, pwdLabel, pwdField);
        pwd.getDialogPane().setContent(pwdContent);
        pwd.getDialogPane().getStyleClass().add("dialog-pane");
        
        pwd.getDialogPane().lookupButton(decryptBtn).getStyleClass().addAll("button", "button-primary");
        pwd.getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("button");
        
        pwd.setResultConverter(btn -> btn == decryptBtn ? pwdField.getText() : null);
        
        Optional<String> pwdRes = pwd.showAndWait();
        if (pwdRes.isEmpty() || pwdRes.get().isEmpty()) {
            return;
        }
        char[] password = pwdRes.get().toCharArray();

        // Step 3: Select output directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Choose output directory for decrypted directory");
        File outDir = dirChooser.showDialog(null);
        if (outDir == null) {
            showInfo("Output directory not selected");
            return;
        }

        // Remove .encdir extension for output directory name
        String originalName = selected.getName();
        if (originalName.endsWith(ENCRYPTED_DIR_EXTENSION)) {
            originalName = originalName.substring(0, originalName.length() - ENCRYPTED_DIR_EXTENSION.length());
        }
        Path outputPath = outDir.toPath().resolve(originalName);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                CryptoEngine.decryptDirectory(selected.toPath(), outputPath, key);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            log("Directory decrypted: " + outputPath);
            showInfo("Directory decrypted successfully!\nSaved to: " + outputPath);
        });
        task.setOnFailed(e -> showError("Decryption failed", task.getException()));
        new Thread(task).start();
    }

    private void refreshLocalFiles() {
        Task<java.util.List<FileRecord>> task = new Task<>() {
            @Override
            protected java.util.List<FileRecord> call() throws Exception {
                java.util.List<FileRecord> localFiles = new java.util.ArrayList<>();
                Path vaultPath = LocalFileStorage.getVaultPath();
                
                if (Files.exists(vaultPath)) {
                    Files.list(vaultPath)
                        .filter(p -> p.toString().endsWith(ENCRYPTED_FILE_EXTENSION) || p.toString().endsWith(ENCRYPTED_DIR_EXTENSION))
                        .forEach(p -> {
                            try {
                                long size = Files.size(p);
                                localFiles.add(new FileRecord(
                                    p.getFileName().toString(),
                                    size,
                                    p.getParent().toString()
                                ));
                            } catch (Exception e) {
                                // Skip files we can't read
                            }
                        });
                }
                return localFiles;
            }
        };
        
        task.setOnSucceeded(e -> {
            files.clear();
            files.addAll(task.getValue());
            log("Refreshed local files: " + files.size() + " encrypted files found");
        });
        task.setOnFailed(e -> log("Failed to refresh files: " + task.getException().getMessage()));
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

    public static void main(String[] args) {
        launch(args);
    }

    public static class FileRecord {
        private final String filename;
        private final Long size;
        private final String path;

        public FileRecord(String filename, Long size, String path) {
            this.filename = filename;
            this.size = size;
            this.path = path;
        }

        public String getFilename() { return filename; }
        public Long getSize() { return size; }
        public String getPath() { return path; }
    }
}
