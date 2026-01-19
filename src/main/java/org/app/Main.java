package org.app;

import org.app.ui.GeneratorFrame;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GeneratorFrame frame = new GeneratorFrame();
            frame.setVisible(true);
        });
    }
}
