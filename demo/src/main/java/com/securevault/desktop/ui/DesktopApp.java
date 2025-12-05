package com.securevault.desktop.ui;

import com.securevault.desktop.crypto.CryptoEngine;
import com.securevault.desktop.crypto.KeyDerivation;
import com.securevault.desktop.network.ApiClient;
import com.securevault.desktop.storage.ConfigurationManager;
import com.securevault.desktop.storage.LocalFileStorage;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DesktopApp extends JFrame {

    private final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTextArea logArea = new JTextArea(8, 80);
    private final JLabel statusLabel = new JLabel("Not logged in");

    public DesktopApp() {
        super("SecureVault Desktop");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Top panel - actions
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loginBtn = new JButton("Login");
        JButton refreshBtn = new JButton("Refresh Files");
        JButton encryptBtn = new JButton("Encrypt File");
        JButton decryptBtn = new JButton("Decrypt File");

        topPanel.add(loginBtn);
        topPanel.add(refreshBtn);
        topPanel.add(encryptBtn);
        topPanel.add(decryptBtn);
        topPanel.add(statusLabel);

        add(topPanel, BorderLayout.NORTH);

        // Table for files
        tableModel.setColumnIdentifiers(new Object[]{"ID", "Encrypted Filename", "Size", "Uploaded At"});
    JTable table = new JTable(tableModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
        JScrollPane tableScroll = new JScrollPane(table);
        add(tableScroll, BorderLayout.CENTER);

    // Log area
    logArea.setEditable(false);
    logArea.setLineWrap(true);
    logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
    logScroll.setPreferredSize(new Dimension(800, 160));
        add(logScroll, BorderLayout.SOUTH);

        // Ensure vault directory exists
        try {
            LocalFileStorage.init();
            log("Vault path: " + LocalFileStorage.getVaultPath());
        } catch (Exception e) {
            log("Failed to initialize local vault: " + e.getMessage());
        }

    // Actions
        loginBtn.addActionListener(this::onLogin);
        refreshBtn.addActionListener(e -> onRefreshFiles());
        encryptBtn.addActionListener(this::onEncryptFile);
        decryptBtn.addActionListener(this::onDecryptFile);

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 600));
    }

    private void onLogin(ActionEvent e) {
        JPanel panel = new JPanel(new GridLayout(2, 2));
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        panel.add(new JLabel("Username:"));
        panel.add(userField);
        panel.add(new JLabel("Password:"));
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String username = userField.getText().trim();
            char[] password = passField.getPassword();
            if (username.isEmpty() || password.length == 0) {
                JOptionPane.showMessageDialog(this, "Username and password are required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // perform login in background
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try (ApiClient apiClient = new ApiClient()) {
                        String token = apiClient.login(username, new String(password));
                        ConfigurationManager.saveToken(token);
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Logged in as " + username);
                            log("Login successful.");
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            log("Login failed: " + ex.getMessage());
                            JOptionPane.showMessageDialog(DesktopApp.this, "Login failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        });
                    } finally {
                        java.util.Arrays.fill(password, '\0');
                    }
                    return null;
                }
            }.execute();
        }
    }

    private void onRefreshFiles() {
        new SwingWorker<Void, Void>() {
            private List<Map<String, Object>> files;
            private Exception ex;

            @Override
            protected Void doInBackground() {
                try {
                    String token = ConfigurationManager.loadToken();
                    if (token == null) {
                        throw new IllegalStateException("Not logged in. Please login first.");
                    }
                    try (ApiClient apiClient = new ApiClient(token)) {
                        files = apiClient.listFiles();
                    }
                } catch (Exception e) {
                    ex = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (ex != null) {
                    log("Failed to refresh files: " + ex.getMessage());
                    JOptionPane.showMessageDialog(DesktopApp.this, "Failed to refresh files: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                tableModel.setRowCount(0);
                if (files == null || files.isEmpty()) {
                    log("No files found on server.");
                } else {
                    for (Map<String, Object> f : files) {
                        tableModel.addRow(new Object[]{f.get("id"), f.get("filenameEnc"), f.get("size"), f.get("uploadedAt")});
                    }
                    log("Refreshed file list. " + files.size() + " items.");
                }
            }
        }.execute();
    }

    private void onEncryptFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose file to encrypt");
        int res = chooser.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File selected = chooser.getSelectedFile();
        JPanel panel = new JPanel(new GridLayout(2, 2));
        JPasswordField pwd1 = new JPasswordField();
        JPasswordField pwd2 = new JPasswordField();
        panel.add(new JLabel("Password:"));
        panel.add(pwd1);
        panel.add(new JLabel("Confirm password:"));
        panel.add(pwd2);

        int ok = JOptionPane.showConfirmDialog(this, panel, "Encryption Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        char[] p1 = pwd1.getPassword();
        char[] p2 = pwd2.getPassword();
        if (p1.length == 0 || !java.util.Arrays.equals(p1, p2)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match or empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new SwingWorker<Void, Void>() {
            private Exception ex;

            @Override
            protected Void doInBackground() {
                try {
                    SecretKey key = KeyDerivation.deriveKeyFromPassword(p1);
                    Path vault = LocalFileStorage.getVaultPath();
                    if (vault == null) {
                        throw new IllegalStateException("Local vault path is not initialized.");
                    }
                    Path output = vault.resolve(selected.getName() + ".enc");
                    CryptoEngine.encryptFile(selected.toPath(), output, key);
                } catch (Exception exx) {
                    ex = exx;
                } finally {
                    java.util.Arrays.fill(p1, '\0');
                    java.util.Arrays.fill(p2, '\0');
                }
                return null;
            }

            @Override
            protected void done() {
                if (ex != null) {
                    log("Encryption failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(DesktopApp.this, "Encryption failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    log("File encrypted and saved to vault.");
                    JOptionPane.showMessageDialog(DesktopApp.this, "File encrypted and saved to vault.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    private void onDecryptFile(ActionEvent e) {
        Path vaultPath = LocalFileStorage.getVaultPath();
        JFileChooser chooser;
        if (vaultPath != null && java.nio.file.Files.exists(vaultPath)) {
            chooser = new JFileChooser(vaultPath.toFile());
        } else {
            chooser = new JFileChooser();
        }
        chooser.setDialogTitle("Choose encrypted (.enc) file from vault");
        int res = chooser.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File selected = chooser.getSelectedFile();
        if (!selected.getName().endsWith(".enc")) {
            JOptionPane.showMessageDialog(this, "Selected file is not an .enc file.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPasswordField pwd = new JPasswordField();
        int ok = JOptionPane.showConfirmDialog(this, pwd, "Enter password for decryption", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        char[] password = pwd.getPassword();
        if (password.length == 0) {
            JOptionPane.showMessageDialog(this, "Password cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new SwingWorker<Void, Void>() {
            private Exception ex;

            @Override
            protected Void doInBackground() {
                try {
                    SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                    String originalName = selected.getName().substring(0, selected.getName().length() - 4);
                    Path output = Path.of(originalName).toAbsolutePath();
                    CryptoEngine.decryptFile(selected.toPath(), output, key);
                } catch (Exception exx) {
                    ex = exx;
                } finally {
                    java.util.Arrays.fill(password, '\0');
                }
                return null;
            }

            @Override
            protected void done() {
                if (ex != null) {
                    log("Decryption failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(DesktopApp.this, "Decryption failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    log("File decrypted to current directory.");
                    JOptionPane.showMessageDialog(DesktopApp.this, "File decrypted to current directory.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopApp app = new DesktopApp();
            app.setVisible(true);
        });
    }
}
