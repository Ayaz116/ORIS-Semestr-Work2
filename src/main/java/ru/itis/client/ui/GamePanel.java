package ru.itis.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import ru.itis.client.GameClient;
import ru.itis.model.GameState;

public class GamePanel extends JPanel {
    private int catX, catY;
    private final Map<String, MouseView> miceMap = new ConcurrentHashMap<>();
    private final List<Point> cheesePoints = new ArrayList<>();
    private final List<Point> holePoints = new ArrayList<>();
    private final Set<Integer> pressedKeys = new HashSet<>();

    private boolean gameOver = false;
    private String winner = "none";

    // --- Новый флаг ---
    private boolean gameStarted = false; // Пока false — не рисуем кота/мышей

    private GameClient client;

    public GamePanel() {
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                pressedKeys.add(e.getKeyCode());
                recalcVelocity();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                pressedKeys.remove(e.getKeyCode());
                recalcVelocity();
            }
        });
    }

    public void setClient(GameClient client) {
        this.client = client;
    }

    /**
     * Вызывается при получении STATE от сервера.
     */
    public synchronized void updateState(boolean gOver, String winner,
                                         int catX, int catY,
                                         Map<String, MouseView> newMice,
                                         List<Point> newCheese,
                                         List<Point> newHoles) {
        this.gameOver = gOver;
        this.winner = winner;
        this.catX = catX;
        this.catY = catY;

        miceMap.clear();
        miceMap.putAll(newMice);

        cheesePoints.clear();
        cheesePoints.addAll(newCheese);

        holePoints.clear();
        holePoints.addAll(newHoles);

        repaint(); // Запускаем перерисовку
    }

    /**
     * Когда сервер присылает START_GAME, клиент вызывает этот метод,
     * чтобы локально выставить флаг, разрешающий отрисовку кота/мышей.
     */
    public synchronized void setGameStarted(boolean started) {
        this.gameStarted = started;
        repaint();
    }

    /**
     * Сброс (например, при RESET_LOBBY) — снова выставляем false,
     * чтобы не рисовать кота/мышей.
     */
    public synchronized void resetState() {
        catX = catY = 0;
        miceMap.clear();
        cheesePoints.clear();
        holePoints.clear();
        gameOver = false;
        winner = "none";
        gameStarted = false;
        repaint();
    }

    private void recalcVelocity() {
        if (client == null) return;
        if (!gameStarted) return;

        int vx = 0, vy = 0;
        if (pressedKeys.contains(KeyEvent.VK_UP)) vy -= 2;
        if (pressedKeys.contains(KeyEvent.VK_DOWN)) vy += 2;
        if (pressedKeys.contains(KeyEvent.VK_LEFT)) vx -= 2;
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) vx += 2;

        // Шлём SET_VELOCITY
        client.sendSetVelocity(vx, vy);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (!gameStarted) {
            g.drawString("Waiting for start...", 50, 50);
            return;
        }

        int w = getWidth();
        int h = getHeight();
        float scaleX = (float) w / 800.0f;
        float scaleY = (float) h / 600.0f;

        // Кот
        int drawCatX = (int) (catX * scaleX);
        int drawCatY = (int) (catY * scaleY);
        int catSize = (int) Math.max(10, 20 * scaleX); // подстраховка
        g.setColor(Color.RED);
        g.fillOval(drawCatX, drawCatY, catSize, catSize);

        // Мыши
        for (var mv : miceMap.values()) {
            int mx = (int) (mv.x * scaleX);
            int my = (int) (mv.y * scaleY);
            int size = (int) Math.max(8, 15 * scaleX);
            if (!mv.alive) {
                g.setColor(Color.GRAY);
            } else {
                g.setColor(mv.carryingCheese ? Color.ORANGE : Color.BLUE);
            }
            g.fillOval(mx, my, size, size);
            g.setColor(Color.BLACK);
            g.drawString("score=" + mv.score, mx, my - 5);
        }

        // Сыр (2)
        g.setColor(Color.YELLOW);
        for (Point c : cheesePoints) {
            int cx = (int) (c.x * scaleX);
            int cy = (int) (c.y * scaleY);
            int s = (int) Math.max(6, 10 * scaleX);
            g.fillRect(cx, cy, s, s);
        }

        // Норы (5)
        g.setColor(Color.BLACK);
        for (Point hh : holePoints) {
            int hx = (int) (hh.x * scaleX);
            int hy = (int) (hh.y * scaleY);
            int s = (int) Math.max(10, 15 * scaleX);
            g.fillOval(hx, hy, s, s);
        }

        if (gameOver) {
            g.setColor(Color.MAGENTA);
            g.setFont(new Font("Arial", Font.BOLD, 32));
            g.drawString("Game Over! Winner: " + winner, 200, 300);
        }
    }
}
