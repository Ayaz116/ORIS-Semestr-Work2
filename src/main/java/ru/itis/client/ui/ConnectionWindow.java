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
        setTitle("Подключение к игре");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

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

        UIManager.put("TextField.background", new Color(255, 248, 220));
        UIManager.put("TextField.foreground", new Color(101, 67, 33));
        UIManager.put("TextField.font", new Font("Arial", Font.BOLD, 14));

        hostField = new JTextField("localhost", 20);
        portField = new JTextField("5555", 20);
        nameField = new JTextField(20);

        hostButton = new JRadioButton("Создать игру");
        joinButton = new JRadioButton("Присоединиться");
        ButtonGroup group = new ButtonGroup();
        group.add(hostButton);
        group.add(joinButton);
        hostButton.setSelected(true);

        connectButton = new JButton("Подключиться");
        styleButton(connectButton);

        addComponent(mainPanel, new JLabel("Имя:"), gbc, 0, 0);
        addComponent(mainPanel, nameField, gbc, 1, 0);
        addComponent(mainPanel, new JLabel("Хост:"), gbc, 0, 1);
        addComponent(mainPanel, hostField, gbc, 1, 1);
        addComponent(mainPanel, new JLabel("Порт:"), gbc, 0, 2);
        addComponent(mainPanel, portField, gbc, 1, 2);
        addComponent(mainPanel, hostButton, gbc, 0, 3);
        addComponent(mainPanel, joinButton, gbc, 1, 3);
        addComponent(mainPanel, connectButton, gbc, 0, 4, 2, 1);

        for (Component comp : mainPanel.getComponents()) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                label.setForeground(Color.WHITE);
                label.setFont(new Font("Arial", Font.BOLD, 14));
            } else if (comp instanceof JRadioButton) {
                JRadioButton radio = (JRadioButton) comp;
                radio.setForeground(Color.WHITE);
                radio.setOpaque(false);
                radio.setFont(new Font("Arial", Font.BOLD, 14));
            }
        }

        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(mainPanel);

        pack();
        setLocationRelativeTo(null);

        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(ConnectionWindow.this, 
                        "Имя не может быть пустым!", "Ошибка", 
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String host = hostField.getText();
                int port = Integer.parseInt(portField.getText());
                boolean isHost = hostButton.isSelected();

                if (isHost) {
                    new Thread(() -> {
                        GameServer server = new GameServer(port, true, null);
                        server.startServer();
                    }).start();
                }

                ClientMain.startClient(host, port, isHost, name, ConnectionWindow.this);
                setVisible(false);
            }
        });
    }

    private void addComponent(JPanel panel, Component comp, GridBagConstraints gbc, int x, int y) {
        gbc.gridx = x;
        gbc.gridy = y;
        panel.add(comp, gbc);
    }

    private void addComponent(JPanel panel, Component comp, GridBagConstraints gbc, int x, int y, int width, int height) {
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        panel.add(comp, gbc);
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
    }

    private void styleButton(JButton button) {
        button.setBackground(new Color(255, 248, 220));
        button.setForeground(new Color(101, 67, 33));
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setFocusPainted(false);
    }

    public void showConnectionWindow() {
        setVisible(true);
    }
}