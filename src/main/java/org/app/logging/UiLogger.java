package org.app.logging;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UiLogger implements AppLogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JTextArea console;
    public UiLogger(JTextArea console) {
        this.console = console;
    }

    @Override
    public void info(String message) {
        write("INFO", message, null);
    }

    @Override
    public void warn(String message) {
        write("WARN", message, null);
    }

    @Override
    public void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    private void write(String level, String message, Throwable error) {
        StringBuilder builder = new StringBuilder();
        String timestamp = LocalDateTime.now().format(FORMATTER);
        builder.append(timestamp)
                .append(" [")
                .append(level)
                .append("] ")
                .append(message == null ? "" : message)
                .append(System.lineSeparator());

        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            builder.append(sw);
            if (!sw.toString().endsWith(System.lineSeparator())) {
                builder.append(System.lineSeparator());
            }
        }

        String text = builder.toString();
        appendToConsole(text);
    }

    private void appendToConsole(String text) {
        if (console == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            console.append(text);
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    
}
