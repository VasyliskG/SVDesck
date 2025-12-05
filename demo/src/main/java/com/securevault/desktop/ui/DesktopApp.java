package com.securevault.desktop.ui;

import com.securevault.desktop.crypto.CryptoEngine;
import com.securevault.desktop.crypto.KeyDerivation;
import com.securevault.desktop.storage.LocalFileStorage;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class DesktopApp extends JFrame {

    private final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTextArea logArea = new JTextArea(8, 80);

    public DesktopApp() {
        super("SecureVault Desktop - Local Mode");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Top panel - actions
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton encryptBtn = new JButton("Encrypt File");
        JButton decryptBtn = new JButton("Decrypt File");
        JButton refreshBtn = new JButton("Refresh Local Files");
        JLabel titleLabel = new JLabel("SecureVault - Local File Encryption");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));

        topPanel.add(titleLabel);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(encryptBtn);
        topPanel.add(decryptBtn);
        topPanel.add(refreshBtn);

        add(topPanel, BorderLayout.NORTH);

        // Table for local encrypted files
        tableModel.setColumnIdentifiers(new Object[]{"Filename", "Size", "Location"});
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
        encryptBtn.addActionListener(this::onEncryptFile);
        decryptBtn.addActionListener(this::onDecryptFile);
        refreshBtn.addActionListener(e -> refreshLocalFiles());

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 600));
        
        // Load local files on startup
        refreshLocalFiles();
    }

    private void onEncryptFile(ActionEvent e) {
        // Step 1: Choose file to encrypt
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose file to encrypt");
        int res = chooser.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File selected = chooser.getSelectedFile();
        
        // Step 2: Password with confirmation
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

        // Step 3: Choose output directory
        JFileChooser dirChooser = new JFileChooser();
        dirChooser.setDialogTitle("Choose output directory for encrypted file");
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        dirChooser.setCurrentDirectory(LocalFileStorage.getVaultPath().toFile());
        int dirRes = dirChooser.showSaveDialog(this);
        if (dirRes != JFileChooser.APPROVE_OPTION) {
            JOptionPane.showMessageDialog(this, "Output directory not selected.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        File outputDir = dirChooser.getSelectedFile();
        Path outputPath = outputDir.toPath().resolve(selected.getName() + ".enc");

        new SwingWorker<Void, Void>() {
            private Exception ex;

            @Override
            protected Void doInBackground() {
                try {
                    SecretKey key = KeyDerivation.deriveKeyFromPassword(p1);
                    CryptoEngine.encryptFile(selected.toPath(), outputPath, key);
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
                    log("File encrypted: " + outputPath);
                    JOptionPane.showMessageDialog(DesktopApp.this, "File encrypted successfully!\nSaved to: " + outputPath, "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshLocalFiles();
                }
            }
        }.execute();
    }

    private void onDecryptFile(ActionEvent e) {
        // Step 1: Choose encrypted file
        Path vaultPath = LocalFileStorage.getVaultPath();
        JFileChooser chooser;
        if (vaultPath != null && java.nio.file.Files.exists(vaultPath)) {
            chooser = new JFileChooser(vaultPath.toFile());
        } else {
            chooser = new JFileChooser();
        }
        chooser.setDialogTitle("Choose encrypted (.enc) file");
        int res = chooser.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File selected = chooser.getSelectedFile();
        if (!selected.getName().endsWith(".enc")) {
            JOptionPane.showMessageDialog(this, "Selected file is not an .enc file.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Step 2: Enter password
        JPasswordField pwd = new JPasswordField();
        int ok = JOptionPane.showConfirmDialog(this, pwd, "Enter password for decryption", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        char[] password = pwd.getPassword();
        if (password.length == 0) {
            JOptionPane.showMessageDialog(this, "Password cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Step 3: Choose output directory
        JFileChooser dirChooser = new JFileChooser();
        dirChooser.setDialogTitle("Choose output directory for decrypted file");
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int dirRes = dirChooser.showSaveDialog(this);
        if (dirRes != JFileChooser.APPROVE_OPTION) {
            JOptionPane.showMessageDialog(this, "Output directory not selected.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        File outputDir = dirChooser.getSelectedFile();
        String originalName = selected.getName().substring(0, selected.getName().length() - 4);
        Path outputPath = outputDir.toPath().resolve(originalName);

        new SwingWorker<Void, Void>() {
            private Exception ex;

            @Override
            protected Void doInBackground() {
                try {
                    SecretKey key = KeyDerivation.deriveKeyFromPassword(password);
                    CryptoEngine.decryptFile(selected.toPath(), outputPath, key);
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
                    log("File decrypted: " + outputPath);
                    JOptionPane.showMessageDialog(DesktopApp.this, "File decrypted successfully!\nSaved to: " + outputPath, "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    private void refreshLocalFiles() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                SwingUtilities.invokeLater(() -> tableModel.setRowCount(0));
                
                Path vaultPath = LocalFileStorage.getVaultPath();
                if (vaultPath != null && Files.exists(vaultPath)) {
                    try {
                        Files.list(vaultPath)
                            .filter(p -> p.toString().endsWith(".enc"))
                            .forEach(p -> {
                                try {
                                    long size = Files.size(p);
                                    String sizeStr = formatFileSize(size);
                                    SwingUtilities.invokeLater(() -> 
                                        tableModel.addRow(new Object[]{
                                            p.getFileName().toString(),
                                            sizeStr,
                                            p.getParent().toString()
                                        })
                                    );
                                } catch (Exception ex) {
                                    // Skip files we can't read
                                }
                            });
                    } catch (Exception ex) {
                        log("Failed to list files: " + ex.getMessage());
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                log("Refreshed local files: " + tableModel.getRowCount() + " encrypted files found");
            }
        }.execute();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
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
