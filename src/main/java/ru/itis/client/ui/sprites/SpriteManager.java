package ru.itis.client.ui.sprites;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpriteManager {
    private static SpriteManager instance;
    
    // Спрайты для анимации
    private Map<String, List<BufferedImage>> animations;
    private Map<String, Integer> currentFrames;
    private long lastFrameTime;
    private static final int FRAME_DELAY = 100; // миллисекунды между кадрами
    
    // Статичный фон
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
            // Загружаем статичный фон (1024x768)
            background = ImageIO.read(getClass().getResourceAsStream("/sprites/background/background.png"));
            
            // Загружаем спрайты кота
            BufferedImage catIdleSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/cat/Idle.png"));
            List<BufferedImage> catIdle = new ArrayList<>();
            if (catIdleSheet != null) {
                int frameWidth = catIdleSheet.getWidth() / 4; // 4 кадра для idle
                int frameHeight = catIdleSheet.getHeight();
                for (int i = 0; i < 4; i++) {
                    catIdle.add(catIdleSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
                }
            }
            animations.put("cat_idle", catIdle);
            
            // Бег вправо
            BufferedImage catRunRightSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/cat/RunRight.png"));
            List<BufferedImage> catRunRight = new ArrayList<>();
            if (catRunRightSheet != null) {
                int frameWidth = catRunRightSheet.getWidth() / 8; // 8 кадров для бега
                int frameHeight = catRunRightSheet.getHeight();
                for (int i = 0; i < 8; i++) {
                    catRunRight.add(catRunRightSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
                }
            }
            animations.put("cat_run_right", catRunRight);
            
            // Бег влево
            BufferedImage catRunLeftSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/cat/RunLeft.png"));
            List<BufferedImage> catRunLeft = new ArrayList<>();
            if (catRunLeftSheet != null) {
                int frameWidth = catRunLeftSheet.getWidth() / 8; // 8 кадров для бега
                int frameHeight = catRunLeftSheet.getHeight();
                for (int i = 0; i < 8; i++) {
                    catRunLeft.add(catRunLeftSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
                }
            }
            animations.put("cat_run_left", catRunLeft);
            
            // Загружаем спрайты мыши
            BufferedImage mouseIdleSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/Idle.png"));
            List<BufferedImage> mouseIdle = new ArrayList<>();
            if (mouseIdleSheet != null) {
                int frameWidth = mouseIdleSheet.getWidth() / 4; // 4 кадра для idle
                int frameHeight = mouseIdleSheet.getHeight();
                for (int i = 0; i < 4; i++) {
                    mouseIdle.add(mouseIdleSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
                }
            }
            animations.put("mouse_idle", mouseIdle);
            
            // Бег мыши вправо
            BufferedImage mouseRunRightSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RunRight.png"));
            List<BufferedImage> mouseRunRight = new ArrayList<>();
            if (mouseRunRightSheet != null) {
                int frameWidth = mouseRunRightSheet.getWidth() / 8; // 8 кадров для бега
                int frameHeight = mouseRunRightSheet.getHeight();
                for (int i = 0; i < 8; i++) {
                    mouseRunRight.add(mouseRunRightSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
                }
            }
            animations.put("mouse_run_right", mouseRunRight);
            
            // Бег мыши влево
            BufferedImage mouseRunLeftSheet = ImageIO.read(getClass().getResourceAsStream("/sprites/mouse/RunLeft.png"));
            List<BufferedImage> mouseRunLeft = new ArrayList<>();
            if (mouseRunLeftSheet != null) {
                int frameWidth = mouseRunLeftSheet.getWidth() / 8; // 8 кадров для бега
                int frameHeight = mouseRunLeftSheet.getHeight();
                for (int i = 0; i < 8; i++) {
                    mouseRunLeft.add(mouseRunLeftSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight));
                }
            }
            animations.put("mouse_run_left", mouseRunLeft);
            
            // Загружаем статичные спрайты
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
    
    public void drawBackground(Graphics g, int width, int height) {
        g.drawImage(background, 0, 0, width, height, null);
    }
    
    public void drawSprite(Graphics g, String type, int x, int y, int width, int height, double angle) {
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
    
    public void drawGradientBackground(Graphics g, int width, int height) {
        Graphics2D g2d = (Graphics2D) g;
        GradientPaint gradient = new GradientPaint(
            0, 0, new Color(135, 206, 235),
            0, height, new Color(65, 105, 225)
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);
    }
    
    public void drawSpriteWithShadow(Graphics g, String type, int x, int y, int width, int height, double angle) {
        // Сначала рисуем тень
        Graphics2D g2d = (Graphics2D) g;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        drawSprite(g, type, x + 3, y + 3, width, height, angle);
        
        // Затем рисуем сам спрайт
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        drawSprite(g, type, x, y, width, height, angle);
    }
    
    // Добавим метод для проверки загрузки спрайтов
    public boolean isLoaded(String type) {
        List<BufferedImage> frames = animations.get(type);
        return frames != null && !frames.isEmpty();
    }
} 