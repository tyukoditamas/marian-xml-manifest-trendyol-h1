package org.app.ui;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import org.app.logging.UiLogger;
import org.app.service.ProgressListener;
import org.app.service.ManifestService;
import javax.swing.SwingUtilities;

public class GeneratorFrame extends JFrame {
    private final JTextField excelField = new JTextField(28);
    private final JTextField lrnField = new JTextField(28);
    private final JButton generateButton = new JButton("Generate XML");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea consoleArea = new JTextArea(10, 46);
    private final JProgressBar progressBar = new JProgressBar();

    private File selectedFile;
    private SwingWorker<File, Void> currentWorker;
    private final UiLogger logger = new UiLogger(consoleArea);
    private final ManifestService manifestService;

    public GeneratorFrame() {
        super("XML Manifest Trendyol H1");
        this.manifestService = new ManifestService(logger, buildProgressListener());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel excelLabel = new JLabel("Excel file:");
        excelField.setEditable(false);
        JButton browseButton = new JButton("Browse...");

        JLabel lrnLabel = new JLabel("LRN:");

        generateButton.setEnabled(false);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(excelLabel, gbc);

        gbc.gridx = 1;
        panel.add(excelField, gbc);

        gbc.gridx = 2;
        panel.add(browseButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(lrnLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(lrnField, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(generateButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        panel.add(statusLabel, gbc);

        consoleArea.setEditable(false);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Consola"));
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setString(" ");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        panel.add(progressBar, gbc);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel.add(consoleScroll, gbc);
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;

        DocumentListener validator = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateGenerateState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateGenerateState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateGenerateState();
            }
        };

        lrnField.getDocument().addDocumentListener(validator);

        browseButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Excel files (*.xls, *.xlsx)", "xls", "xlsx"));
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedFile = chooser.getSelectedFile();
                excelField.setText(selectedFile.getAbsolutePath());
                statusLabel.setText(" ");
                logger.info("Fisier Excel selectat: " + selectedFile.getAbsolutePath());
                updateGenerateState();
            }
        });

        generateButton.addActionListener(event -> handleGenerate());

        return panel;
    }

    private void updateGenerateState() {
        boolean hasFile = selectedFile != null && selectedFile.isFile();
        boolean hasLrn = !lrnField.getText().trim().isEmpty();
        boolean idle = currentWorker == null || currentWorker.isDone();
        generateButton.setEnabled(hasFile && hasLrn && idle);
    }

    private void handleGenerate() {
        String lrn = lrnField.getText().trim();
        if (selectedFile == null || !selectedFile.isFile()) {
            statusLabel.setText("Please choose a valid Excel file.");
            updateGenerateState();
            return;
        }
        if (lrn.isEmpty()) {
            statusLabel.setText("Please enter the LRN.");
            updateGenerateState();
            return;
        }

        generateButton.setEnabled(false);
        statusLabel.setText("Generating XML...");
        progressBar.setIndeterminate(true);
        progressBar.setString("Se proceseaza...");

        File fileToProcess = selectedFile;
        currentWorker = new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                logger.info("Pornire generare XML.");
                logger.info("Fisier Excel: " + fileToProcess.getAbsolutePath());
                logger.info("LRN: " + lrn);
                return manifestService.generate(fileToProcess, lrn);
            }

            @Override
            protected void done() {
                try {
                    File outputFile = get();
                    statusLabel.setText("Generated: " + outputFile.getAbsolutePath());
                    logger.info("Generare XML finalizata: " + outputFile.getAbsolutePath());
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Finalizat");
                } catch (Exception ex) {
                    String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("Generation failed: " + message);
                    logger.error("Eroare la generarea XML.", ex);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Eroare");
                } finally {
                    currentWorker = null;
                    updateGenerateState();
                }
            }
        };
        currentWorker.execute();
    }

    private ProgressListener buildProgressListener() {
        return new ProgressListener() {
            @Override
            public void onStart(String stage, int total) {
                SwingUtilities.invokeLater(() -> {
                    if (total > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setMaximum(total);
                        progressBar.setValue(0);
                        progressBar.setString("Descrieri TARIC: 0/" + total);
                    } else {
                        progressBar.setIndeterminate(true);
                        progressBar.setString("Se proceseaza...");
                    }
                });
            }

            @Override
            public void onProgress(int current, int total) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setMaximum(Math.max(total, 1));
                    progressBar.setValue(Math.min(current, total));
                    progressBar.setString("Descrieri TARIC: " + current + "/" + total);
                });
            }

            @Override
            public void onDone(String stage) {
                SwingUtilities.invokeLater(() -> {
                    int max = progressBar.getMaximum();
                    progressBar.setIndeterminate(false);
                    if (max > 0) {
                        progressBar.setValue(max);
                    }
                    progressBar.setString("Descrieri TARIC finalizate");
                });
            }
        };
    }
}
