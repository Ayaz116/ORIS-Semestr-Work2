package ru.itis.client.ui.sprites;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpriteManager {
    private static SpriteManager instance;
    
    private Map<String, List<BufferedImage>> animations;
    private Map<String, Integer> currentFrames;
    private long lastFrameTime;
    private static final int FRAME_DELAY = 100; // миллисекунды между кадрами
    
    private BufferedImage background;
    
    private SpriteManager() {
        animations = new HashMap<>();
        currentFrames = new HashMap<>();
        loadSprites();
    }
    
    public static SpriteManager getInstance() {
        if (instance == null) {
            instance = new SpriteManager();
        }
        return instance;
    }
    
    private void loadSprites() {
        try {
            background = ImageIO.read(getClass().getResourceAsStream("/sprites/background/background.png"));
            
            BufferedImage catLeftIdleSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/cat/LeftIdle.png"));
            animations.put("LeftIdle", drawIdleSprites(catLeftIdleSheet));

            BufferedImage catRightIdleSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/cat/RightIdle.png"));
            animations.put("RightIdle", drawIdleSprites(catRightIdleSheet));
            
            BufferedImage catRunRightSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/cat/RunRight.png"));
            animations.put("cat_run_right", drawWalkSprites(catRunRightSheet));
            
            BufferedImage catRunLeftSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/cat/RunLeft.png"));
            animations.put("cat_run_left", drawWalkSprites(catRunLeftSheet));
            
            BufferedImage mouseLeftIdleSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/LeftIdle.png"));
            animations.put("left_idle", drawIdleSprites(mouseLeftIdleSheet));

            BufferedImage mouseRightIdleSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RigthIdle.png"));
            animations.put("right_idle", drawIdleSprites(mouseRightIdleSheet));

            BufferedImage mouseRunRightSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RunRight.png"));
            animations.put("mouse_run_right", drawIdleSprites(mouseRunRightSheet));
            
            BufferedImage mouseRunLeftSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RunLeft.png"));
            animations.put("mouse_run_left", drawIdleSprites(mouseRunLeftSheet));
            
            BufferedImage mouseLeftIdleCheeseSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/LeftIdleCheese.png"));
            animations.put("left_idle_cheese", drawIdleSprites(mouseLeftIdleCheeseSheet));

            BufferedImage mouseRightIdleCheeseSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RigthIdleCheese.png"));
            animations.put("right_idle_cheese", drawIdleSprites(mouseRightIdleCheeseSheet));

            BufferedImage mouseRunRightCheeseSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RunRightCheese.png"));
            animations.put("mouse_run_right_cheese", drawIdleSprites(mouseRunRightCheeseSheet));
            
            BufferedImage mouseRunLeftCheeseSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RunLeftCheese.png"));
            animations.put("mouse_run_left_cheese", drawIdleSprites(mouseRunLeftCheeseSheet));

            BufferedImage cheeseImg = ImageIO.read(getClass().getResourceAsStream("/sprites/items/cheese.png"));
            if (cheeseImg != null) {
                animations.put("cheese", List.of(cheeseImg));
            }
            
            BufferedImage holeImg = ImageIO.read(getClass().getResourceAsStream("/sprites/items/hole.png"));
            if (holeImg != null) {
                animations.put("hole", List.of(holeImg));
            }
            
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
            System.err.println("Error loading sprites: " + e.getMessage());
        }
    }

    private List<BufferedImage> drawWalkSprites(BufferedImage RunSheet) {
        List<BufferedImage> Run = new ArrayList<>();
        if (RunSheet != null) {
            int frameWidth = RunSheet.getWidth() / 6; // 6 кадров для бега
            int frameHeight = RunSheet.getHeight();
            for (int i = 0; i < 6; i++) {
                Run.add(RunSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
            }
        }
        return Run;
    }

    private List<BufferedImage> drawIdleSprites(BufferedImage RunSheet) {
        List<BufferedImage> Run = new ArrayList<>();
        if (RunSheet != null) {
            int frameWidth = RunSheet.getWidth() / 4; // 4 кадра для бега
            int frameHeight = RunSheet.getHeight();
            for (int i = 0; i < 4; i++) {
                Run.add(RunSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
            }
        }
        return Run;
    }

    public void updateAnimation() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime > FRAME_DELAY) {
            // Обновляем все анимации
            for (String key : animations.keySet()) {
                int currentFrame = currentFrames.getOrDefault(key, 0);
                List<BufferedImage> frames = animations.get(key);
                if (frames != null && !frames.isEmpty()) {
                    currentFrame = (currentFrame + 1) % frames.size();
                    currentFrames.put(key, currentFrame);
                }
            }
            lastFrameTime = currentTime;
        }
    }

    public void drawAnimatedScore(Graphics g, int x, int y, int score, long gameTime) {
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double pulse = 0.7 + 0.3 * Math.abs(Math.sin(gameTime / 500.0));

        int width = 120;
        int height = 40;

        GradientPaint gradient = new GradientPaint(
                x, y, new Color(139, 69, 19, 200),    // Saddle brown
                x, y + height, new Color(101, 67, 33, 200)  // Dark brown
        );

        g2d.setPaint(gradient);
        g2d.fill(new RoundRectangle2D.Float(x - width/2, y - height/2, width, height, 15, 15));

        g2d.setStroke(new BasicStroke(2f));
        g2d.setColor(new Color(218, 165, 32, (int)(100 * pulse))); // Golden rod
        g2d.draw(new RoundRectangle2D.Float(x - width/2, y - height/2, width, height, 15, 15));

        Font scoreFont = new Font("Arial", Font.BOLD, (int)(24 * pulse));
        g2d.setFont(scoreFont);

        String scoreText = "Счёт: " + score;
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(scoreText);

        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawString(scoreText, x - textWidth/2 + 2, y + fm.getAscent()/2 + 2);

        g2d.setColor(new Color(255, 223, 0)); // Gold
        g2d.drawString(scoreText, x - textWidth/2, y + fm.getAscent()/2);
    }

    public void drawBackground(Graphics g, int width, int height) {
        g.drawImage(background, 0, 0, width, height, null);
    }
    
    public void drawIdleSprites(Graphics g, String type, int x, int y, int width, int height, double angle) {
        List<BufferedImage> frames = animations.get(type);
        if (frames != null && !frames.isEmpty()) {
            int currentFrame = currentFrames.getOrDefault(type, 0);
            if (currentFrame < frames.size()) {
                BufferedImage sprite = frames.get(currentFrame);
                Graphics2D g2d = (Graphics2D) g;
                g2d.drawImage(sprite, x, y, width, height, null);
            }
        }
    }
    
    public void drawSpriteWithShadow(Graphics g, String type, int x, int y, int width, int height, double angle) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        drawIdleSprites(g, type, x + 3, y + 3, width, height, angle);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        drawIdleSprites(g, type, x, y, width, height, angle);
    }

    public boolean isLoaded(String type) {
        List<BufferedImage> frames = animations.get(type);
        return frames != null && !frames.isEmpty();
    }
} 