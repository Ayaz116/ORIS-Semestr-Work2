package ru.itis;

import ru.itis.server.GameServer;
import ru.itis.client.ClientMain;

import javax.swing.*;

public class MainApp {
    public static void main(String[] args) {
        Object[] options = { "Host", "Join", "Cancel" };
        int choice = JOptionPane.showOptionDialog(
                null,
                "Do you want to host or join an existing game?",
                "Cats & Mice",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            startAsHost();
        } else if (choice == 1) {
            startAsClient();
        } else {
            System.exit(0);
        }
    }

    private static void startAsHost() {
        // Запрашиваем имя хоста
        String name = JOptionPane.showInputDialog(null, "Enter your name (Host):", "HostUser");
        if (name == null || name.isBlank()) {
            return;
        }
        // Запрашиваем порт
        String portStr = JOptionPane.showInputDialog(null, "Enter port to host on:", "5000");
        if (portStr == null) {
            return;
        }
        int port = Integer.parseInt(portStr);

        // 1) Запуск сервера
        GameServer server = new GameServer(port, true, null);
        new Thread(server::startServer).start();

        // 2) Запуск локального клиента (передаём name)
        // content CONNECT будет "host|<имя>"
        ClientMain.startClient("localhost", port, true, name);
    }

    private static void startAsClient() {
        // Запрашиваем имя
        String name = JOptionPane.showInputDialog(null, "Enter your name:", "User");
        if (name == null || name.isBlank()) {
            return;
        }

        String host = JOptionPane.showInputDialog(null, "Enter host address:", "localhost");
        if (host == null) {
            return;
        }
        String portStr = JOptionPane.showInputDialog(null, "Enter port:", "5000");
        if (portStr == null) {
            return;
        }
        int port = Integer.parseInt(portStr);

        ClientMain.startClient(host, port, false, name);
    }
}
