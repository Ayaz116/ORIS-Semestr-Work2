package ru.itis.client;

import ru.itis.client.ui.ConnectionWindow;
import ru.itis.client.ui.MouseView;
import ru.itis.model.GameState;
import ru.itis.protocol.Message;
import ru.itis.protocol.MessageType;
import ru.itis.client.ui.GamePanel;
import ru.itis.client.ui.GameWindow;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Клиент, обрабатывающий лобби (LOBBY_UPDATE), назначение ролей (ASSIGN_ROLE),
 * старт игры (START_GAME), сброс игры (RESET_LOBBY) и т.д.
 */
public class GameClient {
    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;

    private final GamePanel gamePanel;
    private GameWindow gameWindow;
    private boolean isHost;
    private boolean gameStarted = false;
    private String myRole = "pending";

    // Имя игрока
    private final String playerName;

    // Модель лобби: {clientId -> PlayerInfo}
    private final Map<String, PlayerInfo> playersMap = new HashMap<>();

    private ConnectionWindow connectionWindow; // Ссылка на окно подключения

    public GameClient(String host, int port, GamePanel panel, boolean isHost, String playerName, ConnectionWindow connectionWindow) {
        this.host = host;
        this.port = port;
        this.gamePanel = panel;
        this.isHost = isHost;
        this.playerName = playerName;
        this.connectionWindow = connectionWindow; // Сохраняем ссылку на окно подключения
    }

    public void setGameWindow(GameWindow gameWindow) {
        this.gameWindow = gameWindow;
    }

    /**
     * Подключаемся к серверу, отправляем CONNECT (например, "host|Alex" или "pending|Bob").
     */
    public void connect(String content) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());
            running.set(true);

            // Отправляем CONNECT
            sendMessage(new Message(MessageType.CONNECT, content));

            // Стартуем поток чтения
            listenerThread = new Thread(this::listenServer);
            listenerThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Основной цикл приёма сообщений от сервера.
     */
    private boolean kicked = false; // Флаг для отслеживания кика

    private void listenServer() {
        while (running.get()) {
            try {
                Message msg = (Message) in.readObject();
                if (msg == null) break;

                switch (msg.getType()) {
                    case LOBBY_UPDATE:
                        handleLobbyUpdate(msg.getContent());
                        break;
                    case ASSIGN_ROLE:
                        myRole = msg.getContent();
                        System.out.println("[Client] My new role: " + myRole);
                        break;
                    case START_GAME:
                        handleStartGame();
                        break;
                    case RESET_LOBBY:
                        handleResetLobby();
                        break;
                    case STATE:
                        parseAndUpdateState(msg.getContent());
                        break;
                    case DISCONNECT: // Обработка отключения (кика)
                        handleDisconnect(msg.getContent());
                        break;
                    default:
                        // Игнорируем остальные
                }
            } catch (IOException | ClassNotFoundException e) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                System.out.println("Server disconnected or error: " + errorMessage);
                handleDisconnect("Connection lost: " + errorMessage); // Закрываем окно при разрыве соединения
                break;
            }
        }
    }


    /**
     * Обработка отключения (кика или разрыва соединения).
     */
    private void handleDisconnect(String message) {
        System.out.println("[Client] Disconnected: " + message);
        SwingUtilities.invokeLater(() -> {
            // Показываем сообщение о кике или разрыве соединения
            JOptionPane.showMessageDialog(null, message, "Disconnected", JOptionPane.INFORMATION_MESSAGE);

            // Закрываем окно игры
            if (gameWindow != null) {
                gameWindow.dispose(); // Закрываем окно игры
            }

            // Возвращаем игрока в окно подключения
            if (connectionWindow != null) {
                connectionWindow.showConnectionWindow(); // Показываем окно подключения
            }
        });
    }


    /**
     * Обработка LOBBY_UPDATE.
     * Формат, который мы приняли:
     * "HOST|client-123;PLAYERS|client-123|Alex,cat;client-456|Bob,mouse;..."
     */
    private void handleLobbyUpdate(String content) {
        System.out.println("[Client] LOBBY_UPDATE -> " + content);
        Map<String, PlayerInfo> newMap = parseLobbyData(content);
        playersMap.clear();
        playersMap.putAll(newMap);

        // Обновляем лобби в UI
        if (gameWindow != null) {
            // Преобразуем {clientId -> PlayerInfo} в {clientId -> role} для совместимости
            Map<String, String> simpleMap = new HashMap<>();
            for (var e : playersMap.entrySet()) {
                // Отображаем: "Имя (clientId)"
                String displayLine = e.getValue().displayName + " (" + e.getKey() + ")";
                // role
                String role = e.getValue().role;
                // Допустим, хотим вывести "Alex (client-123),cat"
                // Т. е. key = displayLine, value = role
                simpleMap.put(displayLine, role);
            }
            gameWindow.updateLobbyPlayers(simpleMap);
        }
    }

    /**
     * Парсим строку лобби:
     * "HOST|client-123;PLAYERS|client-123|Alex,cat;client-456|Bob,mouse;..."
     */
    private Map<String, PlayerInfo> parseLobbyData(String content) {
        Map<String, PlayerInfo> result = new HashMap<>();

        String[] parts = content.split(";");
        // parts[0] = "HOST|client-123"
        // parts[1] = "PLAYERS|client-123|Alex,cat"
        // parts[2] = "client-456|Bob,mouse"
        // parts[3] = ""

        // Пропускаем hostLine, если нужно
        boolean playersSection = false;
        StringBuilder playersData = new StringBuilder();
        for (int i=1; i<parts.length; i++) {
            if (parts[i].startsWith("PLAYERS|")) {
                playersSection = true;
                String chunk = parts[i].substring("PLAYERS|".length());
                playersData.append(chunk).append(";");
            } else if (playersSection && !parts[i].isBlank()) {
                playersData.append(parts[i]).append(";");
            }
        }

        // теперь у нас "client-123|Alex,cat;client-456|Bob,mouse;"
        String[] entries = playersData.toString().split(";");
        for (String e : entries) {
            if (e.isBlank()) continue;
            // e = "client-123|Alex,cat"
            String[] arr = e.split("\\|");
            if (arr.length != 2) continue;
            String cId = arr[0];           // "client-123"
            String nameAndRole = arr[1];   // "Alex,cat"

            String[] nr = nameAndRole.split(",");
            if (nr.length!=2) continue;
            String pName = nr[0];         // "Alex"
            String pRole = nr[1];
            // "cat"

            result.put(cId, new PlayerInfo(pName, pRole));
        }
        return result;
    }

    /**
     * START_GAME -> игра началась
     * Ставим gameStarted=true и говорим окну переключиться (focus на gamePanel).
     */
    private void handleStartGame() {
        gameStarted = true;
        // Сообщаем панели
        if (gamePanel != null) {
            gamePanel.setGameStarted(true);
            gamePanel.requestFocusInWindow(); // фокус
        }
    }

    /**
     * RESET_LOBBY -> игра закончилась, нужно сбросить состояние
     */
    private void handleResetLobby() {
        gameStarted = false;
        myRole = "pending";
        if (gamePanel != null) {
            gamePanel.resetState();
        }
        if (gameWindow != null) {
            gameWindow.onResetLobby();
        }
    }

    /**
     * STATE -> обновляем GamePanel
     */
    private void parseAndUpdateState(String content) {
        boolean gOver = false;
        String winner = "none";
        int catX = GameState.WIDTH / 2; // По умолчанию кот в центре
        int catY = GameState.HEIGHT / 2; // По умолчанию кот в центре
        int catVelX = 0, catVelY = 0;
        var newMice = new ConcurrentHashMap<String, MouseView>(); // Используем ConcurrentHashMap
        var cheesePts = new ArrayList<Point>();
        var holePts = new ArrayList<Point>();

        String[] parts = content.split(";");
        for (String part : parts) {
            if (part.startsWith("GAMEOVER|")) {
                String val = part.substring("GAMEOVER|".length());
                String[] arr = val.split(",");
                if (arr.length == 2) {
                    gOver = Boolean.parseBoolean(arr[0]);
                    winner = arr[1];
                }
            } else if (part.startsWith("CAT|")) {
                String coords = part.substring("CAT|".length());
                String[] xy = coords.split(",");
                catX = Integer.parseInt(xy[0]);
                catY = Integer.parseInt(xy[1]);
                catVelX = Integer.parseInt(xy[2]);
                catVelY = Integer.parseInt(xy[3]);
            } else if (part.startsWith("MOUSE|")) {
                String data = part.substring("MOUSE|".length());
                String[] arr = data.split(",");
                if (arr.length == 9) {
                    String mid = arr[0];
                    int mx = Integer.parseInt(arr[1]);
                    int my = Integer.parseInt(arr[2]);
                    int mvx = Integer.parseInt(arr[3]);
                    int mvy = Integer.parseInt(arr[4]);
                    boolean alive = Boolean.parseBoolean(arr[5]);
                    boolean carrying = Boolean.parseBoolean(arr[6]);
                    int sc = Integer.parseInt(arr[7]);
                    boolean lastFacingLeft = Boolean.parseBoolean(arr[8]);
                    newMice.put(mid, new MouseView(mx, my, mvx, mvy, alive, carrying, sc, lastFacingLeft));
                }
            } else if (part.startsWith("CHEESE|")) {
                // Обработка всех сыров
                String cData = part.substring("CHEESE|".length());
                parse(cheesePts, cData);
            } else if (part.startsWith("HOLES|")) {
                // Обработка всех нор
                String hData = part.substring("HOLES|".length());
                parse(holePts, hData);
            }
        }

        // Обновляем состояние игры
        gamePanel.updateState(gOver, winner, catX, catY, catVelX, catVelY, newMice, cheesePts, holePts);
    }

    private void parse(ArrayList<Point> Pts, String Data) {
        String[] Coords = Data.split(","); // Разделяем координаты нор
        for (int i = 0; i < Coords.length; i += 2) {
            if (i + 1 >= Coords.length) break; // Проверяем, что есть две координаты
            int hx = Integer.parseInt(Coords[i]);
            int hy = Integer.parseInt(Coords[i + 1]);
            Pts.add(new Point(hx, hy));
        }
    }

    // =============== Методы для управления ===============

    /**
     * При движении (стрелки)
     */
    public void sendSetVelocity(int vx, int vy) {
        if (!gameStarted) return;
        if (!"cat".equals(myRole) && !"mouse".equals(myRole)) return;
        sendMessage(new Message(MessageType.SET_VELOCITY, vx + "," + vy));
    }

    /**
     * Хост кнопкой назначает роль
     */
    public void assignRole(String clientId, String role) {
        if (!isHost) return;
        sendMessage(new Message(MessageType.ASSIGN_ROLE, clientId + "," + role));
    }

    /**
     * Хост нажимает "Start Game"
     */
    public void startGame(int n) {
        if (!isHost) return;
        sendMessage(new Message(MessageType.START_GAME, String.valueOf(n)));
    }

    /**
     * Отключение
     */
    public void disconnect() {
        running.set(false);
        sendMessage(new Message(MessageType.DISCONNECT, ""));
        close();
    }

    private void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Отправляет запрос на кик игрока.
     */
    public void kickPlayer(String clientId) {
        if (!isHost) return; // Только хост может кикать игроков
        sendMessage(new Message(MessageType.KICK_PLAYER, clientId));
    }

    public void sendMessage(Message msg) {
        if (out == null) return;
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Доступ к playersMap, чтобы лобби окно могло проверить условия (>=2 и <=5 и т.д.)
     */
    public Map<String, PlayerInfo> getPlayersMap() {
        return playersMap;
    }

    public boolean isHost() {
        return isHost;
    }

    // =============== Вспомогательный класс для лобби ===============
    public static class PlayerInfo {
        public String displayName; // Alex, Bob, ...
        public String role;        // cat, mouse, host, pending

        public PlayerInfo(String name, String role) {
            this.displayName = name;
            this.role = role;
        }
    }
}
