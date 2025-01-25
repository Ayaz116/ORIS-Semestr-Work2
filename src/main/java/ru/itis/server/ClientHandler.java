package ru.itis.server;

import ru.itis.model.GameState;
import ru.itis.protocol.Message;
import ru.itis.protocol.MessageType;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.List;

public class ClientHandler extends Thread {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final GameServer server;
    private final GameState gameState;

    private String clientId;
    private String role = "pending";
    private String playerName;

    public ClientHandler(Socket socket, GameServer server, GameState gameState) {
        this.socket = socket;
        this.server = server;
        this.gameState = gameState;
        this.clientId = "client-" + System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (!isInterrupted()) {
                try {
                    Message msg = (Message) in.readObject();
                    if (msg == null) break;

                    switch (msg.getType()) {
                        case CONNECT:
                            handleConnect(msg.getContent());
                            break;
                        case DISCONNECT:
                            handleDisconnect();
                            return;
                        case SET_VELOCITY:
                            handleSetVelocity(msg.getContent());
                            break;
                        case ASSIGN_ROLE:
                            handleAssignRole(msg.getContent());
                            break;
                        case START_GAME:
                            handleStartGame(msg.getContent());
                            break;
                        case KICK_PLAYER:
                            handleKickPlayer(msg.getContent());
                            break;
                        default:
                    }

                    if (gameState.isGameOver() && server.isGameStarted()) {
                        broadcastState();
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                        }
                        server.resetLobby();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("[Server] Error processing message from client " + clientId + ": " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("[Server] Client " + clientId + " disconnected/error: " + e.getMessage());
        } finally {
            server.getClients().remove(this);
            close();
            if (!server.isGameStarted()) {
                server.broadcastLobbyUpdate();
            }
        }
    }

    private void handleKickPlayer(String clientId) {
        if (!this.clientId.equals(server.getHostId())) {
            System.out.println("[Server] Non-host tried to kick a player!");
            return;
        }

        ClientHandler target = null;
        for (ClientHandler ch : server.getClients()) {
            if (ch.getClientId().equals(clientId)) {
                target = ch;
                break;
            }
        }

        if (target != null) {
            server.getGameState().removeMouse(clientId);
            target.sendMessage(new Message(MessageType.DISCONNECT, "You have been kicked from the game."));
            target.close();
            server.getClients().remove(target);
            System.out.println("[Server] Kicked player: " + target.getPlayerName());
            server.broadcastLobbyUpdate();
        }
    }

    private void handleConnect(String content) {
        // content = "host|Alex" или "pending|Bob"
        String[] arr = content.split("\\|");
        if (arr.length != 2) return;
        String connectType = arr[0];
        playerName = arr[1];

        if (server.isLobbyMode()) {
            if (server.getHostId() == null && "host".equals(connectType)) {
                server.setHostId(clientId);
                role = "cat";
                gameState.setCatPosition(GameState.WIDTH / 2, GameState.HEIGHT / 2); // Кот в центре
                System.out.println("[Server] " + clientId + " is HOST+CAT in center");
                sendMessage(new Message(MessageType.ASSIGN_ROLE, "cat"));
            } else {
                role = "mouse";
                List<Point> holes = gameState.getHoles();
                Random rnd = new Random();
                Point hole = holes.get(rnd.nextInt(holes.size()));
                int x = hole.x + rnd.nextInt(41) - 20;
                int y = hole.y + rnd.nextInt(41) - 20;
                x = Math.max(0, Math.min(GameState.WIDTH, x));
                y = Math.max(0, Math.min(GameState.HEIGHT, y));

                gameState.addMouse(clientId, x, y);
                System.out.println("[Server] " + clientId + " joined as mouse near hole=" + hole + " => (" + x + "," + y + ")");
                sendMessage(new Message(MessageType.ASSIGN_ROLE, "mouse"));
            }
            server.broadcastLobbyUpdate();
        }
    }

    private void handleDisconnect() {
        System.out.println("[Server] " + clientId + " = " + playerName + " disconnected");
        close();
    }

    private void handleSetVelocity(String msg) {
        String[] arr = msg.split(",");
        int vx = Integer.parseInt(arr[0]);
        int vy = Integer.parseInt(arr[1]);
        if ("cat".equals(role)) {
            gameState.setCatVelocity(vx, vy);
        } else if ("mouse".equals(role)) {
            gameState.setMouseVelocity(clientId, vx, vy);
        }
    }

    private void handleAssignRole(String content) {
        if (!clientId.equals(server.getHostId())) {
            System.out.println("[Server] Non-host tried to assign roles!");
            return;
        }
        String[] arr = content.split(",");
        if (arr.length != 2) return;
        String targetId = arr[0];
        String newRole = arr[1];
        ClientHandler targetHandler = null;
        for (ClientHandler ch : server.getClients()) {
            if (ch.getClientId().equals(targetId)) {
                targetHandler = ch;
                break;
            }
        }
        if (targetHandler == null) {
            System.out.println("[Server] handleAssignRole: no such clientId " + targetId);
            return;
        }

        String oldRole = targetHandler.getRole();
        if (oldRole.equals(newRole)) {
            System.out.println("[Server] handleAssignRole: same role, no changes.");
            return;
        }
        if ("cat".equals(newRole)) {
            for (ClientHandler ch2 : server.getClients()) {
                if ("cat".equals(ch2.getRole())) {
                    if (!ch2.getClientId().equals(targetId)) {
                        ch2.setRole("mouse");
                        int x = (int) (Math.random() * 300 + 50);
                        int y = (int) (Math.random() * 300 + 50);
                        gameState.addMouse(ch2.getClientId(), x, y);
                    }
                }
            }
            gameState.removeMouse(targetId);

            targetHandler.setRole("cat");
            gameState.setCatPosition(50, 50);

        } else if ("mouse".equals(newRole)) {
            if ("cat".equals(oldRole)) {
            }
            int x = (int) (Math.random() * 300 + 50);
            int y = (int) (Math.random() * 300 + 50);
            gameState.addMouse(targetId, x, y);

            targetHandler.setRole("mouse");
        }
        targetHandler.sendMessage(new Message(MessageType.ASSIGN_ROLE, newRole));
        server.broadcastLobbyUpdate();
    }

    private void handleStartGame(String content) {
        if (!clientId.equals(server.getHostId())) return;
        if (!hasAtLeastOneCatAndMouse()) {
            System.out.println("[Server] Not enough roles to start!");
            return;
        }
        server.setGameStarted(true);
        server.broadcast(new Message(MessageType.START_GAME, "start"));
        server.getGameState().setCatPosition(GameState.WIDTH / 2, GameState.HEIGHT / 2);
        GameState gameState = server.getGameState();
        gameState.setTotalCheeseToWin(Integer.parseInt(content));
        Random rnd = new Random();
        List<Point> holes = gameState.getHoles();
        for (ClientHandler ch : server.getClients()) {
            if ("mouse".equals(ch.getRole())) {
                Point hole = holes.get(rnd.nextInt(holes.size()));
                int x = hole.x + rnd.nextInt(41) - 20;
                int y = hole.y + rnd.nextInt(41) - 20;
                x = Math.max(0, Math.min(GameState.WIDTH, x));
                y = Math.max(0, Math.min(GameState.HEIGHT, y));

                gameState.addMouse(ch.getClientId(), x, y);
            }
        }

        broadcastState();
    }

    private boolean hasAtLeastOneCatAndMouse() {
        boolean catFound = false, mouseFound = false;
        for (ClientHandler ch : server.getClients()) {
            if ("cat".equals(ch.getRole())) {
                catFound = true;
            } else if ("mouse".equals(ch.getRole())) {
                mouseFound = true;
            }
        }
        return catFound && mouseFound;
    }

    private void broadcastState() {
        server.broadcast(createStateMessage());
    }

    private Message createStateMessage() {
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

        return new Message(MessageType.STATE, sb.toString());
    }

    public String getClientId() {
        return clientId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String r) {
        this.role = r;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void sendMessage(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            interrupt();
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
