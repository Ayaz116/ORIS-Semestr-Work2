package ru.itis;

import ru.itis.client.ui.ConnectionWindow;

import javax.swing.*;

public class MainApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ConnectionWindow connectionWindow = new ConnectionWindow();
            connectionWindow.setVisible(true);
        });
    }
}