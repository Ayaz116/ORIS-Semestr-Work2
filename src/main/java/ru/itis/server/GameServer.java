package ru.itis.server;

import ru.itis.model.GameState;
import ru.itis.protocol.Message;
import ru.itis.protocol.MessageType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameServer {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running;

    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final GameState gameState = new GameState();

    private final boolean isLobbyMode;
    private volatile boolean gameStarted = false;
    private volatile String hostId = null;
    private ScheduledExecutorService exec;

    public GameServer(int port, boolean isLobbyMode, String hostId) {
        this.port = port;
        this.isLobbyMode = isLobbyMode;
        this.hostId = hostId;
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started on port " + port);

            exec = Executors.newSingleThreadScheduledExecutor();
            exec.scheduleAtFixedRate(() -> {
                if (!gameStarted) return;
                gameState.updatePositions();
                broadcastState();
            }, 0, 16, TimeUnit.MILLISECONDS);

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("Accepted connection: " + socket);
                    ClientHandler handler = new ClientHandler(socket, this, gameState);
                    handler.start();
                    clients.add(handler);
                } catch (IOException e) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (exec != null) exec.shutdown();
            for (ClientHandler ch : clients) ch.close();
        } catch (IOException e) {}
    }

    public void broadcast(Message msg) {
        for (ClientHandler ch : clients) {
            ch.sendMessage(msg);
        }
    }

    private void broadcastState() {
        String content = createStateMessage();
        broadcast(new Message(MessageType.STATE, content));
    }

    private String createStateMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("GAMEOVER|").append(gameState.isGameOver()).append(",")
                .append(gameState.getWinner() == null ? "none" : gameState.getWinner());

        sb.append(";CAT|").append(gameState.getCatX()).append(",").append(gameState.getCatY()).append(",").append(gameState.getCatVelX()).append(",").append(gameState.getCatVelY());

        var mice = gameState.getAllMice();
        for (var e : mice.entrySet()) {
            String mid = e.getKey();
            GameState.MouseInfo mi = e.getValue();
            sb.append(";MOUSE|").append(mid).append(",")
                    .append(mi.x).append(",").append(mi.y).append(",")
                    .append(mi.vx).append(",").append(mi.vy).append(",")
                    .append(mi.alive).append(",")
                    .append(mi.carryingCheese).append(",")
                    .append(mi.carriedCheeseCount).append(",")
                    .append(mi.lastFacingLeft);
        }

        var cheese = gameState.getCheeseList();
        sb.append(";CHEESE|");
        for (int i = 0; i < cheese.size(); i++) {
            var c = cheese.get(i);
            sb.append(c.x).append(",").append(c.y);
            if (i < cheese.size() - 1) sb.append(",");
        }

        var holes = gameState.getHoles();
        sb.append(";HOLES|");
        for (int i = 0; i < holes.size(); i++) {
            var h = holes.get(i);
            sb.append(h.x).append(",").append(h.y);
            if (i < holes.size() - 1) sb.append(",");
        }

        return sb.toString();
    }

    public CopyOnWriteArrayList<ClientHandler> getClients() {
        return clients;
    }
    public GameState getGameState() {
        return gameState;
    }

    public boolean isLobbyMode() { return isLobbyMode; }
    public boolean isGameStarted() { return gameStarted; }
    public void setGameStarted(boolean val) { gameStarted=val; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId=hostId; }

    public void resetLobby() {
        gameStarted=false;
        gameState.reset();
        for (ClientHandler ch : clients) {
            if (!"host".equals(ch.getRole())) {
                ch.setRole("pending");
            }
        }
        broadcast(new Message(MessageType.RESET_LOBBY, "BackToLobby"));
        broadcastLobbyUpdate();
    }

    public void broadcastLobbyUpdate() {
        StringBuilder sb = new StringBuilder();
        sb.append("HOST|").append(hostId == null ? "none" : hostId).append(";PLAYERS|");

        for (ClientHandler ch : clients) {
            sb.append(ch.getClientId())
                    .append("|")
                    .append(ch.getPlayerName())
                    .append(",")
                    .append(ch.getRole())
                    .append(";");
        }
        String data = sb.toString();
        System.out.println("[Server] broadcastLobbyUpdate: " + data);
        broadcast(new Message(MessageType.LOBBY_UPDATE, data));
    }
}