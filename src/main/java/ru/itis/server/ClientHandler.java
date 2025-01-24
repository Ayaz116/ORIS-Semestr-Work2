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
    private String playerName; // имя игрока

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
                            handleStartGame();
                            break;
                        case KICK_PLAYER:
                            handleKickPlayer(msg.getContent());
                            break;
                        default:
                            // Игнорируем остальные
                    }

                    // Проверяем gameOver
                    if (gameState.isGameOver() && server.isGameStarted()) {
                        broadcastState(); // финальное состояние
                        try {
                            Thread.sleep(2000); // Пауза перед рестартом
                        } catch (InterruptedException e) {
                            // Игнорируем прерывание
                        }
                        server.resetLobby(); // Рестарт лобби
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

        // Находим клиента для кика
        ClientHandler target = null;
        for (ClientHandler ch : server.getClients()) {
            if (ch.getClientId().equals(clientId)) {
                target = ch;
                break;
            }
        }

        if (target != null) {
            // Удаляем мышку кикнутого игрока из GameState
            server.getGameState().removeMouse(clientId);

            // Отправляем сообщение кикнутому игроку о его отключении
            target.sendMessage(new Message(MessageType.DISCONNECT, "You have been kicked from the game."));

            // Закрываем соединение с клиентом
            target.close();
            server.getClients().remove(target);
            System.out.println("[Server] Kicked player: " + target.getPlayerName());

            // Обновляем лобби
            server.broadcastLobbyUpdate();
        }
    }

    /**
     * CONNECT: content = "host|Alex" или "pending|Bob"
     */
    private void handleConnect(String content) {
        // content = "host|Alex" или "pending|Bob"
        String[] arr = content.split("\\|");
        if (arr.length != 2) return;
        String connectType = arr[0];
        playerName = arr[1];

        if (server.isLobbyMode()) {
            if (server.getHostId() == null && "host".equals(connectType)) {
                // Хост => кот в центре
                server.setHostId(clientId);
                role = "cat";
                gameState.setCatPosition(GameState.WIDTH / 2, GameState.HEIGHT / 2); // Кот в центре
                System.out.println("[Server] " + clientId + " is HOST+CAT in center");
                sendMessage(new Message(MessageType.ASSIGN_ROLE, "cat"));
            } else {
                // Мышь => появиться у одной из 5 нор
                role = "mouse";
                List<Point> holes = gameState.getHoles();
                Random rnd = new Random();
                Point hole = holes.get(rnd.nextInt(holes.size())); // Выбираем случайную нору
                int x = hole.x + rnd.nextInt(41) - 20; // Случайное смещение относительно норы
                int y = hole.y + rnd.nextInt(41) - 20; // Случайное смещение относительно норы

                // Ограничиваем координаты, чтобы мышь не выходила за пределы экрана
                x = Math.max(0, Math.min(GameState.WIDTH, x));
                y = Math.max(0, Math.min(GameState.HEIGHT, y));

                gameState.addMouse(clientId, x, y);
                System.out.println("[Server] " + clientId + " joined as mouse near hole=" + hole + " => (" + x + "," + y + ")");
                sendMessage(new Message(MessageType.ASSIGN_ROLE, "mouse"));
            }
            server.broadcastLobbyUpdate();
        }
    }

    /**
     * DISCONNECT
     */
    private void handleDisconnect() {
        System.out.println("[Server] " + clientId + " = " + playerName + " disconnected");
        close();
    }

    /**
     * MOVE
     */

    private void handleSetVelocity(String msg){
        // content = "vx,vy"
        String[] arr = msg.split(",");
        int vx = Integer.parseInt(arr[0]);
        int vy = Integer.parseInt(arr[1]);
        if ("cat".equals(role)) {
            gameState.setCatVelocity(vx,vy);
        } else if ("mouse".equals(role)) {
            gameState.setMouseVelocity(clientId, vx, vy);
        }
    }

    /**
     * ASSIGN_ROLE: content = "client-XYZ,cat" или "client-XYZ,mouse".
     * Если назначаем "cat", старого кота делаем "mouse".
     */
    private void handleAssignRole(String content) {
        // content = "client-XYZ,cat" или "client-XYZ,mouse"
        if (!clientId.equals(server.getHostId())) {
            System.out.println("[Server] Non-host tried to assign roles!");
            return;
        }
        String[] arr = content.split(",");
        if (arr.length != 2) return;
        String targetId = arr[0];  // Кому назначаем
        String newRole  = arr[1];  // "cat" / "mouse"

        // Узнаем текущее состояние target'а
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
        // Если пытаемся повторно назначить ту же роль (например, коту снова "cat"):
        if (oldRole.equals(newRole)) {
            System.out.println("[Server] handleAssignRole: same role, no changes.");
            // всё, выходим
            return;
        }

        // -------------------------------------
        // 1) Если новый кот => убрать старого кота, сделать его mouse
        // -------------------------------------
        if ("cat".equals(newRole)) {
            // Найдём, кто сейчас кот
            for (ClientHandler ch2 : server.getClients()) {
                if ("cat".equals(ch2.getRole())) {
                    // Если это не тот же, тогда переводим его в "mouse"
                    if (!ch2.getClientId().equals(targetId)) {
                        ch2.setRole("mouse");
                        // Добавляем в miceMap
                        int x = (int)(Math.random() * 300 + 50);
                        int y = (int)(Math.random() * 300 + 50);
                        gameState.addMouse(ch2.getClientId(), x, y);
                    }
                }
            }

            // Удаляем target'а из miceMap (если он был мышью)
            gameState.removeMouse(targetId);

            // Назначаем новую роль
            targetHandler.setRole("cat");
            gameState.setCatPosition(50, 50); // reset cat coords

        } else if ("mouse".equals(newRole)) {
            // -------------------------------------
            // 2) Новый "mouse"
            // -------------------------------------
            // Если target был котом, убираем кота:
            if ("cat".equals(oldRole)) {
                // просто setRole ниже, а position кота сбросим, если надо
                // (необязательно, catX/catY можно оставить или обнулить
                //  но обычно не трогаем, т.к. catX не играет роли, если нет кота)
            }

            // Добавляем в miceMap
            int x = (int)(Math.random() * 300 + 50);
            int y = (int)(Math.random() * 300 + 50);
            gameState.addMouse(targetId, x, y);

            // Назначаем новую роль
            targetHandler.setRole("mouse");
        }

        // -------------------------------------
        // Отправим конкретному клиенту новое значение
        // -------------------------------------
        targetHandler.sendMessage(new Message(MessageType.ASSIGN_ROLE, newRole));

        // -------------------------------------
        // Лобби обновляем (все видят новую роль)
        // -------------------------------------
        server.broadcastLobbyUpdate();
    }



    /**
     * START_GAME
     */
    private void handleStartGame() {
        if (!clientId.equals(server.getHostId())) return;
        // Проверяем, есть ли минимум 1 кот и 1 мышь
        if (!hasAtLeastOneCatAndMouse()) {
            System.out.println("[Server] Not enough roles to start!");
            return;
        }
        server.setGameStarted(true);
        server.broadcast(new Message(MessageType.START_GAME, "start"));
        server.getGameState().setCatPosition(GameState.WIDTH / 2, GameState.HEIGHT / 2);
        GameState gameState = server.getGameState();
        Random rnd = new Random();
        List<Point> holes = gameState.getHoles();
        for (ClientHandler ch : server.getClients()) {
            if ("mouse".equals(ch.getRole())) {
                Point hole = holes.get(rnd.nextInt(holes.size())); // Выбираем случайную нору
                int x = hole.x + rnd.nextInt(41) - 20; // Случайное смещение относительно норы
                int y = hole.y + rnd.nextInt(41) - 20; // Случайное смещение относительно норы

                // Ограничиваем координаты, чтобы мышь не выходила за пределы экрана
                x = Math.max(0, Math.min(GameState.WIDTH, x));
                y = Math.max(0, Math.min(GameState.HEIGHT, y));

                gameState.addMouse(ch.getClientId(), x, y);
            }
        }

        broadcastState(); // рассылаем текущее состояние
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

    /**
     * Рассылаем STATE
     */
    private void broadcastState() {
        server.broadcast(createStateMessage());
    }

    /**
     * Создаём строку STATE
     */
    private Message createStateMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("GAMEOVER|").append(gameState.isGameOver()).append(",")
                .append(gameState.getWinner() == null ? "none" : gameState.getWinner());

        sb.append(";CAT|").append(gameState.getCatX()).append(",").append(gameState.getCatY());

        var mice = gameState.getAllMice();
        for (var e : mice.entrySet()) {
            String mid = e.getKey();
            GameState.MouseInfo mi = e.getValue();
            sb.append(";MOUSE|").append(mid).append(",")
                    .append(mi.x).append(",").append(mi.y).append(",")
                    .append(mi.alive).append(",")
                    .append(mi.carryingCheese).append(",")
                    .append(mi.carriedCheeseCount);
        }

        var cheese = gameState.getCheeseList();
        sb.append(";CHEESE|");
        for (int i = 0; i < cheese.size(); i++) {
            var c = cheese.get(i);
            sb.append(c.x).append(",").append(c.y);
            if (i < cheese.size() - 1) sb.append(","); // Разделяем координаты сыров запятыми
        }

        var holes = gameState.getHoles();
        sb.append(";HOLES|");
        for (int i = 0; i < holes.size(); i++) {
            var h = holes.get(i);
            sb.append(h.x).append(",").append(h.y);
            if (i < holes.size() - 1) sb.append(","); // Разделяем координаты нор запятыми
        }

        return new Message(MessageType.STATE, sb.toString());
    }

    // ============ Геттеры / Сеттеры ============

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
            if (in != null)  in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
