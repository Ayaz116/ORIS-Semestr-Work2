package ru.itis.client.ui;

import ru.itis.client.GameClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class GameWindow extends JFrame {
    private JPanel lobbyPanel;
    private JLabel lobbyLabel;
    private DefaultListModel<String> playersListModel;
    private JList<String> playersList;
    private JButton randomRolesBtn;
    private JButton makeCatBtn;
    private JButton startGameBtn;
    private JButton exitButton;
    private JButton kickPlayerBtn;
    private JSpinner cheeseCountSpinner;

    private final GamePanel gamePanel;
    private final GameClient client;
    private boolean gameStarted = false;

    private ConnectionWindow connectionWindow;

    public GameWindow(String host, int port, boolean isHost, String playerName, ConnectionWindow connectionWindow) {
        super(isHost ? "Cats & Mice (HOST)" : "Cats & Mice (CLIENT)");
        this.connectionWindow = connectionWindow;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);
        client = new GameClient(host, port, gamePanel, isHost, playerName, connectionWindow);
        client.setGameWindow(this);
        createLobbyPanel(isHost);
        String connectType = isHost ? "host" : "pending";
        String connectMsg = connectType + "|" + playerName;
        client.connect(connectMsg);
        gamePanel.setClient(client);
        gamePanel.requestFocusInWindow();
        setSize(990, 800);
        setVisible(true);
    }

    private void createLobbyPanel(boolean isHost) {
        lobbyPanel = new JPanel(new BorderLayout());
        lobbyPanel.setPreferredSize(new Dimension(1000, 200));

        lobbyPanel.setOpaque(false);
        lobbyPanel.setBackground(new Color(0, 0, 0, 80));

        UIManager.put("Button.background", new Color(184, 103, 8));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.font", new Font("Arial", Font.BOLD, 14));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lobbyLabel = new JLabel("Лобби (Ожидание игроков)");
        top.add(lobbyLabel);
        lobbyPanel.add(top, BorderLayout.NORTH);

        playersListModel = new DefaultListModel<>();
        playersList = new JList<>(playersListModel);
        playersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(playersList);
        lobbyPanel.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout());
        randomRolesBtn = new JButton("Случайные роли");
        makeCatBtn = new JButton("Выбрать кота");
        startGameBtn = new JButton("Старт");
        exitButton = new JButton("Выйти");
        kickPlayerBtn = new JButton("Выгнать");

        if (isHost) {
            cheeseCountSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
            bottom.add(new JLabel("Количество сыра:"));
            bottom.add(cheeseCountSpinner);
        }

        bottom.add(exitButton);
        bottom.add(randomRolesBtn);
        bottom.add(makeCatBtn);
        bottom.add(startGameBtn);
        bottom.add(kickPlayerBtn);
        lobbyPanel.add(bottom, BorderLayout.SOUTH);

        add(lobbyPanel, BorderLayout.NORTH);

        if (!isHost) {
            randomRolesBtn.setEnabled(false);
            makeCatBtn.setEnabled(false);
            startGameBtn.setEnabled(false);
            kickPlayerBtn.setEnabled(false);
        }

        exitButton.addActionListener(e -> onExitGame());
        randomRolesBtn.addActionListener(this::onRandomRoles);
        makeCatBtn.addActionListener(this::onMakeCat);
        startGameBtn.addActionListener(this::onStartGame);
        kickPlayerBtn.addActionListener(this::onKickPlayer);
    }

    private void onKickPlayer(ActionEvent e) {
        String selectedPlayer = playersList.getSelectedValue();
        if (selectedPlayer == null) {
            JOptionPane.showMessageDialog(this, "Выберите игрока", "Не выбран игрок", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String[] parts = selectedPlayer.split(",");
        if (parts.length < 1) return;
        String clientId = parts[0].substring(parts[0].indexOf("(") + 1, parts[0].indexOf(")"));
        client.kickPlayer(clientId);
    }

    private void onRandomRoles(ActionEvent e) {
        var pm = client.getPlayersMap();
        List<String> nonHostIds = new ArrayList<>();
        for (var entry : pm.entrySet()) {
            if (!"host".equals(entry.getValue())) {
                nonHostIds.add(entry.getKey());
            }
        }
        if (nonHostIds.isEmpty()) return;

        int idx = (int)(Math.random()*nonHostIds.size());
        String catId = nonHostIds.get(idx);

        for (String cid : nonHostIds) {
            if (cid.equals(catId)) {
                client.assignRole(cid, "cat");
            } else {
                client.assignRole(cid, "mouse");
            }
        }
    }

    private void onMakeCat(ActionEvent e) {
        String sel = playersList.getSelectedValue();
        if (sel == null) return;
        String[] arr = sel.split(",");
        if (arr.length<2) return;
        System.out.println(arr[0]);
        int start = arr[0].indexOf("client-");
        int end = arr[0].indexOf(")", start);

        if (start != -1 && end != -1) {
            String clientId = arr[0].substring(start, end);
            System.out.println(clientId);
            client.assignRole(clientId, "cat");}
    }

    private void onStartGame(ActionEvent e) {
        var pm = client.getPlayersMap();
        int size = pm.size();
        if (size < 2 || size > 5) {
            JOptionPane.showMessageDialog(this, "Нужно от 2 до 5 игроков", "Невозможен старт", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean catFound = false, mouseFound = false;
        for (GameClient.PlayerInfo r : pm.values()) {
            if ("cat".equals(r.role)) catFound = true;
            if ("mouse".equals(r.role)) mouseFound = true;
        }
        if (!catFound || !mouseFound) {
            JOptionPane.showMessageDialog(this, "нужны хотя бы 1 кот и 1 мышь", "Невозможен старт", JOptionPane.WARNING_MESSAGE);
            return;
        }

        client.startGame((Integer) cheeseCountSpinner.getValue());
    }

    public void updateLobbyPlayers(Map<String, String> players) {
        playersListModel.clear();
        for (var entry : players.entrySet()) {
            String line = entry.getKey() + "," + entry.getValue();
            playersListModel.addElement(line);
        }
        lobbyLabel.setText("Лобби: " + players.size() + " игроков подключено");
    }

    public void onResetLobby() {
        gameStarted = false;
        lobbyLabel.setText("Лобби (Ожидание игроков...)");
        if (client.isHost()) {
            randomRolesBtn.setEnabled(true);
            makeCatBtn.setEnabled(true);
            startGameBtn.setEnabled(true);
        }
        gamePanel.requestFocusInWindow();
    }

    private void onExitGame() {
        client.disconnect();
        dispose();
        connectionWindow.showConnectionWindow();
    }
}
