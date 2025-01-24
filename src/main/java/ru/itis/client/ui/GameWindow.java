package ru.itis.client.ui;

import ru.itis.client.GameClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

/**
 * Лобби (верхняя панель) + Игра (центральная панель)
 */
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

    private final GamePanel gamePanel;
    private final GameClient client;
    private boolean gameStarted = false;

    // Конструктор с name
    public GameWindow(String host, int port, boolean isHost, String playerName) {
        super(isHost ? "Cats & Mice (HOST)" : "Cats & Mice (CLIENT)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        // center - game panel
        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);

        // client (теперь принимает name)
        client = new GameClient(host, port, gamePanel, isHost, playerName);
        client.setGameWindow(this); // Передаем ссылку на GameWindow

        createLobbyPanel(isHost);

        // Формируем content CONNECT:
        // Если host -> "host|<имя>"
        // иначе -> "pending|<имя>"
        String connectType = isHost ? "host" : "pending";
        String connectMsg = connectType + "|" + playerName;
        client.connect(connectMsg);

        // set client
        gamePanel.setClient(client);
        gamePanel.requestFocusInWindow();

//        setUndecorated(true);
//        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setSize(990,800);
        setVisible(true);



    }

    private void createLobbyPanel(boolean isHost) {
        lobbyPanel = new JPanel(new BorderLayout());
        lobbyPanel.setPreferredSize(new Dimension(1000, 200));
        
        // Добавляем градиентный фон для лобби
        lobbyPanel.setOpaque(false);
        lobbyPanel.setBackground(new Color(0, 0, 0, 80));
        
        // Стилизуем кнопки
        UIManager.put("Button.background", new Color(70, 130, 180));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.font", new Font("Arial", Font.BOLD, 14));
        
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lobbyLabel = new JLabel("Lobby (waiting for players...)");
        top.add(lobbyLabel);
        lobbyPanel.add(top, BorderLayout.NORTH);

        playersListModel = new DefaultListModel<>();
        playersList = new JList<>(playersListModel);
        playersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(playersList);
        lobbyPanel.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout());
        randomRolesBtn = new JButton("Random Roles");
        makeCatBtn = new JButton("Make Selected Cat");
        startGameBtn = new JButton("Start Game");
        exitButton = new JButton("Exit");
        kickPlayerBtn = new JButton("Kick Player"); // Новая кнопка

        bottom.add(exitButton);
        bottom.add(randomRolesBtn);
        bottom.add(makeCatBtn);
        bottom.add(startGameBtn);
        bottom.add(kickPlayerBtn); // Добавляем кнопку в панель
        lobbyPanel.add(bottom, BorderLayout.SOUTH);

        add(lobbyPanel, BorderLayout.NORTH);

        if (!isHost) {
            randomRolesBtn.setEnabled(false);
            makeCatBtn.setEnabled(false);
            startGameBtn.setEnabled(false);
            kickPlayerBtn.setEnabled(false); // Кнопка кика доступна только хосту
        }

        // Обработчики событий
        exitButton.addActionListener(e -> onExitGame());
        randomRolesBtn.addActionListener(this::onRandomRoles);
        makeCatBtn.addActionListener(this::onMakeCat);
        startGameBtn.addActionListener(this::onStartGame);
        kickPlayerBtn.addActionListener(this::onKickPlayer); // Обработчик для кнопки кика

        // Добавляем тултипы
        randomRolesBtn.setToolTipText("Случайно назначить роли игрокам");
        makeCatBtn.setToolTipText("Сделать выбранного игрока котом");
        startGameBtn.setToolTipText("Начать игру");
        kickPlayerBtn.setToolTipText("Выгнать выбранного игрока");
    }

    /**
     * Обработчик нажатия на кнопку "Kick Player".
     */
    private void onKickPlayer(ActionEvent e) {
        String selectedPlayer = playersList.getSelectedValue();
        if (selectedPlayer == null) {
            JOptionPane.showMessageDialog(this, "Please select a player to kick.", "No Player Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Извлекаем clientId из строки (например, "Alex (client-123),cat")
        String[] parts = selectedPlayer.split(",");
        if (parts.length < 1) return;
        String clientId = parts[0].substring(parts[0].indexOf("(") + 1, parts[0].indexOf(")"));

        // Отправляем запрос на кик игрока
        client.kickPlayer(clientId);
    }

    private void onRandomRoles(ActionEvent e) {
        var pm = client.getPlayersMap();
        // Соберём всех, кто не "host"
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
        if (size<2 || size>5) {
            JOptionPane.showMessageDialog(this,"Need 2..5 players!","Cannot start",JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean catFound=false, mouseFound=false;
        for (GameClient.PlayerInfo r : pm.values()) {
            if ("cat".equals(r.role)) catFound=true;
            if ("mouse".equals(r.role)) mouseFound=true;
        }
        if (!catFound || !mouseFound) {
            JOptionPane.showMessageDialog(this,"Need at least 1 cat and 1 mouse!","Cannot start",JOptionPane.WARNING_MESSAGE);
            return;
        }

        client.startGame();
    }

    // ========== Методы, которые вызывает client при LOBBY_UPDATE / START_GAME / RESET_LOBBY

    public void updateLobbyPlayers(Map<String, String> players) {
        playersListModel.clear();
        for (var entry : players.entrySet()) {
            String line = entry.getKey() + "," + entry.getValue();
            playersListModel.addElement(line);
        }
        lobbyLabel.setText("Lobby: " + players.size() + " players connected.");
    }

    public void onGameStart() {
        gameStarted = true;
        lobbyLabel.setText("Game in progress...");
        randomRolesBtn.setEnabled(false);
        makeCatBtn.setEnabled(false);
        startGameBtn.setEnabled(false);

        // Фокус на игровую панель
        gamePanel.requestFocusInWindow();
    }

    public void onResetLobby() {
        gameStarted = false;
        lobbyLabel.setText("Lobby (waiting for players...)");
        if (client.isHost()) {
            randomRolesBtn.setEnabled(true);
            makeCatBtn.setEnabled(true);
            startGameBtn.setEnabled(true);
        }

        // Чтобы при новой игре все могли двигаться:
        gamePanel.requestFocusInWindow();
    }

    private void onExitGame() {
        // Отключаемся от сервера
        client.disconnect();
        // Закрываем окно
        dispose();
        if (client.isHost()) {
            System.exit(0);
        }
    }
}
