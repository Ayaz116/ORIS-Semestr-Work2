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
    private final String playerName;

    private final Map<String, PlayerInfo> playersMap = new HashMap<>();

    private ConnectionWindow connectionWindow;

    public GameClient(String host, int port, GamePanel panel, boolean isHost, String playerName, ConnectionWindow connectionWindow) {
        this.host = host;
        this.port = port;
        this.gamePanel = panel;
        this.isHost = isHost;
        this.playerName = playerName;
        this.connectionWindow = connectionWindow;
    }

    public void setGameWindow(GameWindow gameWindow) {
        this.gameWindow = gameWindow;
    }

    public void connect(String content) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());
            running.set(true);
            sendMessage(new Message(MessageType.CONNECT, content));
            listenerThread = new Thread(this::listenServer);
            listenerThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
                    case DISCONNECT:
                        handleDisconnect(msg.getContent());
                        break;
                    default:
                }
            } catch (IOException | ClassNotFoundException e) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                System.out.println("Server disconnected or error: " + errorMessage);
                handleDisconnect("Connection lost: " + errorMessage); // Закрываем окно при разрыве соединения
                break;
            }
        }
    }


    private void handleDisconnect(String message) {
        System.out.println("[Client] Disconnected: " + message);
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, message, "Disconnected", JOptionPane.INFORMATION_MESSAGE);
            if (gameWindow != null) {
                gameWindow.dispose();
            }
            if (connectionWindow != null) {
                connectionWindow.showConnectionWindow();
            }
        });
    }

    private void handleLobbyUpdate(String content) {
        System.out.println("[Client] LOBBY_UPDATE -> " + content);
        Map<String, PlayerInfo> newMap = parseLobbyData(content);
        playersMap.clear();
        playersMap.putAll(newMap);

        if (gameWindow != null) {
            Map<String, String> simpleMap = new HashMap<>();
            for (var e : playersMap.entrySet()) {
                String displayLine = e.getValue().displayName + " (" + e.getKey() + ")";
                String role = e.getValue().role;
                simpleMap.put(displayLine, role);
            }
            gameWindow.updateLobbyPlayers(simpleMap);
        }
    }

    private Map<String, PlayerInfo> parseLobbyData(String content) {
        Map<String, PlayerInfo> result = new HashMap<>();

        String[] parts = content.split(";");
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

        String[] entries = playersData.toString().split(";");
        for (String e : entries) {
            if (e.isBlank()) continue;
            String[] arr = e.split("\\|");
            if (arr.length != 2) continue;
            String cId = arr[0];
            String nameAndRole = arr[1];

            String[] nr = nameAndRole.split(",");
            if (nr.length!=2) continue;
            String pName = nr[0];
            String pRole = nr[1];

            result.put(cId, new PlayerInfo(pName, pRole));
        }
        return result;
    }


    private void handleStartGame() {
        gameStarted = true;
        if (gamePanel != null) {
            gamePanel.setGameStarted(true);
            gamePanel.requestFocusInWindow();
        }
    }

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

    private void parseAndUpdateState(String content) {
        boolean gOver = false;
        String winner = "none";
        int catX = GameState.WIDTH / 2;
        int catY = GameState.HEIGHT / 2;
        int catVelX = 0, catVelY = 0;
        var newMice = new ConcurrentHashMap<String, MouseView>();
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

        gamePanel.updateState(gOver, winner, catX, catY, catVelX, catVelY, newMice, cheesePts, holePts);
    }

    private void parse(ArrayList<Point> Pts, String Data) {
        String[] Coords = Data.split(",");
        for (int i = 0; i < Coords.length; i += 2) {
            if (i + 1 >= Coords.length) break;
            int hx = Integer.parseInt(Coords[i]);
            int hy = Integer.parseInt(Coords[i + 1]);
            Pts.add(new Point(hx, hy));
        }
    }

    public void sendSetVelocity(int vx, int vy) {
        if (!gameStarted) return;
        if (!"cat".equals(myRole) && !"mouse".equals(myRole)) return;
        sendMessage(new Message(MessageType.SET_VELOCITY, vx + "," + vy));
    }

    public void assignRole(String clientId, String role) {
        if (!isHost) return;
        sendMessage(new Message(MessageType.ASSIGN_ROLE, clientId + "," + role));
    }

    public void startGame(int n) {
        if (!isHost) return;
        sendMessage(new Message(MessageType.START_GAME, String.valueOf(n)));
    }

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

    public Map<String, PlayerInfo> getPlayersMap() {
        return playersMap;
    }

    public boolean isHost() {
        return isHost;
    }

    public static class PlayerInfo {
        public String displayName;
        public String role;

        public PlayerInfo(String name, String role) {
            this.displayName = name;
            this.role = role;
        }
    }
}
