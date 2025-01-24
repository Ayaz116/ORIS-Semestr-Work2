package ru.itis.client.ui;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import ru.itis.client.GameClient;
import ru.itis.model.GameState;
import ru.itis.client.ui.sprites.SpriteManager;

public class GamePanel extends JPanel {
    private int catX, catY;
    private double catVelX = 0, catVelY = 0;
    private boolean lastFacingLeft = false; // Запоминаем последнее направление
    private final Map<String, MouseView> miceMap = new ConcurrentHashMap<>();
    private final List<Point> cheesePoints = new CopyOnWriteArrayList<>();
    private final List<Point> holePoints = new CopyOnWriteArrayList<>();
    private final Set<Integer> pressedKeys = new HashSet<>();

    private boolean gameOver = false;
    private String winner = "none";
    private boolean gameStarted = false; // Флаг начала игры
    private SpriteManager spriteManager;

    private GameClient client;
    private long lastUpdateTime;

    private Timer animationTimer;

    public GamePanel() {
        setFocusable(true);
        spriteManager = SpriteManager.getInstance();
        
        // Добавляем таймер для анимации
        animationTimer = new Timer(16, e -> {
            spriteManager.updateAnimation();
            repaint();
        });
        animationTimer.start();

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
            spriteManager.drawBackground(g, getWidth(), getHeight());
            return;
        }
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        int w = getWidth();
        int h = getHeight();
        float scaleX = (float) w / GameState.WIDTH;
        float scaleY = (float) h / GameState.HEIGHT;
        
        // Рисуем фон
        spriteManager.drawBackground(g, w, h);
        
        // Рисуем норы (32x32)
        for (Point hрPoint : holePoints) {
            int hx = (int) (hрPoint.x * scaleX);
            int hy = (int) (hрPoint.y * scaleY);
            spriteManager.drawSprite(g, "hole", hx - 16, hy - 16, 32, 32, 0);
        }
        
        // Рисуем сыр (32x32)
        for (Point c : cheesePoints) {
            int cx = (int) (c.x * scaleX);
            int cy = (int) (c.y * scaleY);
            spriteManager.drawSprite(g, "cheese", cx - 16, cy - 16, 32, 32, 0);
        }
        
        // Рисуем кота (64x64)
        int drawCatX = (int) (catX * scaleX);
        int drawCatY = (int) (catY * scaleY);
        String catAnim;
        
        if (Math.abs(catVelX) > 0.1 || Math.abs(catVelY) > 0.1) {
            if (Math.abs(catVelX) > 0.1) {
                catAnim = catVelX > 0 ? "cat_run_right" : "cat_run_left";
                lastFacingLeft = catVelX < 0;
            } else {
                catAnim = lastFacingLeft ? "cat_run_left" : "cat_run_right";
            }
        } else {
            catAnim = "cat_idle";
        }
        spriteManager.drawSprite(g, catAnim, drawCatX - 32, drawCatY - 32, 64, 64, 0);
        
        // Рисуем мышей (32x32)
        for (var mv : miceMap.values()) {
            int mx = (int) (mv.x * scaleX);
            int my = (int) (mv.y * scaleY);
            String mouseAnim;
            
            if (Math.abs(mv.velX) > 0.1 || Math.abs(mv.velY) > 0.1) {
                if (Math.abs(mv.velX) > 0.1) {
                    mouseAnim = mv.velX > 0 ? "mouse_run_right" : "mouse_run_left";
                } else {
                    mouseAnim = mv.lastFacingLeft ? "mouse_run_left" : "mouse_run_right";
                }
            } else {
                mouseAnim = "mouse_idle";
            }
            
            if (!mv.alive) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            }
            spriteManager.drawSprite(g, mouseAnim, mx - 16, my - 16, 32, 32, 0);
            if (!mv.alive) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
            
            // Отображаем счет над мышкой
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            String scoreText = "score=" + mv.score;
            g.drawString(scoreText, mx - fm.stringWidth(scoreText)/2, my - 20);
        }

        // Добавляем эффект свечения для Game Over
        if (gameOver) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
            g2d.setColor(new Color(0, 0, 0, 128));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            String text = "Game Over! Winner: " + winner;
            g2d.setFont(new Font("Arial", Font.BOLD, 32));
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);

            g2d.setColor(Color.WHITE);
            g2d.drawString(text, (getWidth() - textWidth) / 2, getHeight() / 2);
        }
    }
}
