package ru.itis.client;

import ru.itis.client.ui.ConnectionWindow;
import ru.itis.client.ui.GameWindow;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ConnectionWindow connectionWindow = new ConnectionWindow();
            connectionWindow.setVisible(true);
        });
    }

    public static void startClient(String host, int port, boolean isHost, String name, ConnectionWindow connectionWindow) {
        SwingUtilities.invokeLater(() -> {
            new GameWindow(host, port, isHost, name, connectionWindow);
            System.out.println("Клиент запущен");
            System.out.println("Сервер: " + host);
        });
    }
}