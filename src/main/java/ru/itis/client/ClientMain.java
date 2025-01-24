package ru.itis.client;

import javax.swing.SwingUtilities;
import ru.itis.client.ui.GameWindow;

public class ClientMain {
    public static void main(String[] args) {
        // Можно для отладки
        // startClient("localhost", 5000, false);
    }

    /**
     * @param isHost - true, если это хост
     * @param name   - имя игрока
     */
    public static void startClient(String host, int port, boolean isHost, String name) {
        SwingUtilities.invokeLater(() -> {
            new GameWindow(host, port, isHost, name);
            System.out.println("Клиент запущен");
            System.out.println("Сервер: " + host);

        });
    }
}
