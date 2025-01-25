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
    private long gameTime = 0;
    private int totalScore = 0;
    private int catX, catY;
    private double catVelX = 0, catVelY = 0;
    private boolean catLastFacingLeft = false;
    private final Map<String, MouseView> miceMap = new ConcurrentHashMap<>();
    private final List<Point> cheesePoints = new CopyOnWriteArrayList<>();
    private final List<Point> holePoints = new CopyOnWriteArrayList<>();
    private final Set<Integer> pressedKeys = new HashSet<>();

    private boolean gameOver = false;
    private String winner = "никто";
    private boolean gameStarted = false;
    private SpriteManager spriteManager;

    private GameClient client;

    private Timer animationTimer;

    public GamePanel() {
        setFocusable(true);
        spriteManager = SpriteManager.getInstance();

        animationTimer = new Timer(16, e -> {
            spriteManager.updateAnimation();
            gameTime += 16;
            totalScore = miceMap.values().stream()
                    .mapToInt(mv -> mv.score)
                    .sum();
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

    public synchronized void updateState(boolean gOver, String winner,
                                         int catX, int catY,
                                         double catVelX, double catVelY,
                                         Map<String, MouseView> newMice,
                                         List<Point> newCheese,
                                         List<Point> newHoles) {
        this.gameOver = gOver;
        this.winner = winner;
        this.catX = catX;
        this.catY = catY;
        this.catVelX = catVelX;
        this.catVelY = catVelY;

        miceMap.clear();
        miceMap.putAll(newMice);

        cheesePoints.clear();
        cheesePoints.addAll(newCheese);

        holePoints.clear();
        holePoints.addAll(newHoles);

        for (var mv : miceMap.values()) {
            if (Math.abs(mv.velX) > 0.1 || Math.abs(mv.velY) > 0.1) {
                if (Math.abs(mv.velX) > 0.1) {
                    mv.setLastFacingLeft(mv.velX < 0);
                }
            }
        }
        repaint();
    }

    public synchronized void setGameStarted(boolean started) {
        this.gameStarted = started;
        repaint();
    }

    public synchronized void resetState() {
        catX = catY = 0;
        miceMap.clear();
        cheesePoints.clear();
        holePoints.clear();
        gameOver = false;
        winner = "никто";
        gameStarted = false;
        repaint();
    }

    private void recalcVelocity() {
        if (client == null || !gameStarted) return;

        int vx = 0, vy = 0;
        if (pressedKeys.contains(KeyEvent.VK_UP)) vy -= 3;
        if (pressedKeys.contains(KeyEvent.VK_DOWN)) vy += 3;
        if (pressedKeys.contains(KeyEvent.VK_LEFT)) vx -= 3;
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) vx += 3;

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

        spriteManager.drawBackground(g, w, h);
        
        for (Point hрPoint : holePoints) {
            int hx = (int) (hрPoint.x * scaleX);
            int hy = (int) (hрPoint.y * scaleY);
            spriteManager.drawSpriteWithShadow(g, "hole", hx - 24, hy - 24, 48, 48, 0);
        }
        
        for (Point c : cheesePoints) {
            int cx = (int) (c.x * scaleX);
            int cy = (int) (c.y * scaleY);
            spriteManager.drawSpriteWithShadow(g, "cheese", cx - 24, cy - 24, 48, 48, 0);
        }
        
        int drawCatX = (int) (catX * scaleX);
        int drawCatY = (int) (catY * scaleY);
        String catAnim;
        
        if (Math.abs(catVelX) > 0.1 || Math.abs(catVelY) > 0.1) {
            if (Math.abs(catVelX) > 0.1) {
                catAnim = catVelX > 0 ? "cat_run_right" : "cat_run_left";
                catLastFacingLeft = catVelX < 0;
            } else {
                catAnim = catLastFacingLeft ? "cat_run_left" : "cat_run_right";
            }
        } else {
            catAnim = catLastFacingLeft ? "LeftIdle" : "RightIdle";
        }
        spriteManager.drawSpriteWithShadow(g, catAnim, drawCatX - 40, drawCatY - 40, 80, 80, 0);
        
        for (var mv : miceMap.values()) {
            int mx = (int) (mv.x * scaleX);
            int my = (int) (mv.y * scaleY);
            String mouseAnim;
            
            if (Math.abs(mv.velX) > 0.1 || Math.abs(mv.velY) > 0.1) {
                if (Math.abs(mv.velX) > 0.1) {
                    mv.setLastFacingLeft(mv.velX < 0);
                }
                
                if (mv.isLastFacingLeft()) {
                    mouseAnim = mv.carryingCheese ? "mouse_run_left_cheese" : "mouse_run_left";
                } else {
                    mouseAnim = mv.carryingCheese ? "mouse_run_right_cheese" : "mouse_run_right";
                }
            } else {
                if (mv.isLastFacingLeft()) {
                    mouseAnim = mv.carryingCheese ? "left_idle_cheese" : "left_idle";
                } else {
                    mouseAnim = mv.carryingCheese ? "right_idle_cheese" : "right_idle";
                }
            }

            if (!mv.alive) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            }
            spriteManager.drawSpriteWithShadow(g, mouseAnim, mx - 24, my - 24, 48, 48, 0);
            if (!mv.alive) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
        }

        spriteManager.drawAnimatedScore(g, getWidth() - 100, 50, totalScore, gameTime);

        if (gameOver) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
            g2d.setColor(new Color(0, 0, 0, 128));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            String text = "Игра окончена! Победитель: " + winner;
            g2d.setFont(new Font("Arial", Font.BOLD, 32));
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);

            g2d.setColor(Color.WHITE);
            g2d.drawString(text, (getWidth() - textWidth) / 2, getHeight() / 2);
        }
    }
}
