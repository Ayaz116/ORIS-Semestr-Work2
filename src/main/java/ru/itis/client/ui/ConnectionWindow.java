package ru.itis.client.ui;

import ru.itis.client.ClientMain;
import ru.itis.server.GameServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConnectionWindow extends JFrame {
    private JTextField hostField;
    private JTextField portField;
    private JTextField nameField;
    private JRadioButton hostButton;
    private JRadioButton joinButton;
    private JButton connectButton;

    public ConnectionWindow() {
        setTitle("Connect to Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // Создаем градиентную панель
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gradient = new GradientPaint(
                        0, 0, new Color(139, 69, 19),
                        0, getHeight(), new Color(101, 67, 33)
                );
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Стиль для текстовых полей
        UIManager.put("TextField.background", new Color(255, 248, 220));
        UIManager.put("TextField.foreground", new Color(101, 67, 33));
        UIManager.put("TextField.font", new Font("Arial", Font.BOLD, 14));

        // Создаем компоненты
        hostField = new JTextField("localhost", 20);
        portField = new JTextField("5555", 20);
        nameField = new JTextField(20);
        hostButton = new JRadioButton("Create Game");
        joinButton = new JRadioButton("Join Game");
        connectButton = new JButton("Connect");

        // Группируем радио кнопки
        ButtonGroup group = new ButtonGroup();
        group.add(hostButton);
        group.add(joinButton);
        joinButton.setSelected(true);

        // Стилизуем компоненты
        Color textColor = new Color(255, 223, 186);
        hostButton.setForeground(textColor);
        joinButton.setForeground(textColor);
        hostButton.setOpaque(false);
        joinButton.setOpaque(false);

        connectButton.setBackground(new Color(218, 165, 32));
        connectButton.setForeground(Color.WHITE);
        connectButton.setFocusPainted(false);
        connectButton.setFont(new Font("Arial", Font.BOLD, 14));

        // Добавляем компоненты
        addComponent(mainPanel, new JLabel("Server:"), gbc, 0, 0);
        addComponent(mainPanel, hostField, gbc, 1, 0);

        addComponent(mainPanel, new JLabel("Port:"), gbc, 0, 1);
        addComponent(mainPanel, portField, gbc, 1, 1);

        addComponent(mainPanel, new JLabel("Name:"), gbc, 0, 2);
        addComponent(mainPanel, nameField, gbc, 1, 2);

        gbc.gridwidth = 2;
        addComponent(mainPanel, hostButton, gbc, 0, 3);
        addComponent(mainPanel, joinButton, gbc, 0, 4);
        addComponent(mainPanel, connectButton, gbc, 0, 5);

        // Стилизуем labels
        Component[] components = mainPanel.getComponents();
        for (Component comp : components) {
            if (comp instanceof JLabel) {
                comp.setForeground(textColor);
                ((JLabel) comp).setFont(new Font("Arial", Font.BOLD, 14));
            }
        }

        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(mainPanel);

        pack();
        setLocationRelativeTo(null);

        // Добавляем обработчик для кнопки Connect
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(ConnectionWindow.this, "Name cannot be empty!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String host = hostField.getText();
                int port = Integer.parseInt(portField.getText());
                boolean isHost = hostButton.isSelected();

                if (isHost) {
                    // Запускаем сервер
                    new Thread(() -> {
                        GameServer server = new GameServer(port, true, null);
                        server.startServer();
                    }).start();
                }

                // Запускаем клиент
                ClientMain.startClient(host, port, isHost, name, ConnectionWindow.this);

                // Закрываем окно подключения
                setVisible(false);
            }
        });
    }

    private void addComponent(JPanel panel, Component comp, GridBagConstraints gbc, int x, int y) {
        gbc.gridx = x;
        gbc.gridy = y;
        panel.add(comp, gbc);
    }

    // Метод для повторного показа окна подключения
    public void showConnectionWindow() {
        setVisible(true);
    }
}