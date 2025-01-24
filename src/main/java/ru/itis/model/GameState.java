package ru.itis.model;

import java.awt.Point;
import java.util.*;

/**
 * Логика:
 *  - Поле 800x600
 *  - Две тарелки сыра (бесконечные) в центре
 *  - 5 норок у границ
 *  - clamp со всех сторон
 *  - Радиусные проверки (подбор сыра, сдача, ловля)
 *  - reset() восстанавливает поле
 */
public class GameState {
    // Размеры спрайтов
    private static final int CAT_SIZE = 48;
    private static final int MOUSE_SIZE = 32;
    private static final int CHEESE_SIZE = 48;
    private static final int HOLE_SIZE = 24;  // Обновлен размер норки

    // Радиусы коллизий (уменьшены для более точного определения)
    private static final int CAT_CATCH_RADIUS = 20;      // Радиус ловли мыши
    private static final int MOUSE_PICKUP_RADIUS = 15;   // Радиус подбора сыра
    private static final int HOLE_ENTER_RADIUS = 12;     // Уменьшен радиус входа в нору

    private static final int TOTAL_CHEESE_TO_WIN = 3;

    public static final int WIDTH  = 800;
    public static final int HEIGHT = 600;

    private int catX, catY;
    private int catVx, catVy;

    private final Map<String, MouseInfo> miceMap = new HashMap<>();
    private final List<Point> cheeseList = new ArrayList<>();
    private final List<Point> holes      = new ArrayList<>();

    private boolean gameOver = false;
    private String winner    = null;

    public GameState() {
        initCheeseAndHoles();
    }

    /**
     * Создаём 2 точки сыра (центр±40) и 5 норок (четыре угла + центр нижней границы).
     */
    private void initCheeseAndHoles() {
        cheeseList.clear();
        int cx = WIDTH / 2;
        int cy = HEIGHT / 2;
        
        // Сыр теперь чуть дальше друг от друга
        cheeseList.add(new Point(cx - 60, cy));
        cheeseList.add(new Point(cx + 60, cy));

        holes.clear();
        // Норки теперь ближе к краям с учетом нового размера
        holes.add(new Point(WIDTH / 2, HOLE_SIZE + 5));             // верх
        holes.add(new Point(WIDTH / 2, HEIGHT - HOLE_SIZE - 5));    // низ
        holes.add(new Point(HOLE_SIZE + 5, HEIGHT / 2));           // лево
        holes.add(new Point(WIDTH - HOLE_SIZE - 5, HEIGHT / 2));   // право
    }

    /**
     * reset(): кот/мыши обнуляются, сыр/норки восстанавливаются.
     */
    public synchronized void reset() {
        catX = WIDTH / 2; // Кот в центре по X
        catY = HEIGHT / 2; // Кот в центре по Y
        catVx = catVy = 0;
        miceMap.clear(); // Очищаем список мышек
        gameOver = false;
        winner = null;

        initCheeseAndHoles(); // Восстанавливаем сыр и норы

        // Добавляем мышек рядом с норами
        Random rnd = new Random();
        List<Point> holes = getHoles();
        for (String mouseId : miceMap.keySet()) {
            Point hole = holes.get(rnd.nextInt(holes.size())); // Выбираем случайную нору
            int x = hole.x + rnd.nextInt(41) - 20; // Случайное смещение относительно норы
            int y = hole.y + rnd.nextInt(41) - 20; // Случайное смещение относительно норы

            // Ограничиваем координаты, чтобы мышь не выходила за пределы экрана
            x = Math.max(0, Math.min(WIDTH, x));
            y = Math.max(0, Math.min(HEIGHT, y));

            miceMap.get(mouseId).x = x;
            miceMap.get(mouseId).y = y;
        }
    }

    // =========== Установка позиций ===========

    public synchronized void setCatPosition(int x, int y) {
        catX = x;
        catY = y;
    }
    public synchronized void setCatVelocity(int vx, int vy) {
        catVx = vx;
        catVy = vy;
    }
    public synchronized void addMouse(String mouseId, int x, int y) {
        MouseInfo m = new MouseInfo();
        m.x = x;
        m.y = y;
        m.alive = true;
        miceMap.put(mouseId, m);
    }
    public synchronized void removeMouse(String mouseId) {
        miceMap.remove(mouseId);
    }
    public synchronized void setMouseVelocity(String mouseId, int vx, int vy) {
        MouseInfo mi = miceMap.get(mouseId);
        if (mi != null) {
            mi.vx = vx;
            mi.vy = vy;
        }
    }

    // =========== Движение/обновление ===========
    public synchronized void updatePositions() {
        // Кот
        catX += catVx;
        catY += catVy;
        clampCat();

        // Мыши
        for (MouseInfo mi : miceMap.values()) {
            if (!mi.alive) continue;
            mi.x += mi.vx;
            mi.y += mi.vy;
            clampMouse(mi);

            pickUpCheeseIfPossible(mi);
            dropCheeseIfInHole(mi);
        }

        checkCatCatchesMice();
        checkIfMiceWin();
    }

    private void clampCat() {
        if (catX < 0) catX = 0;
        if (catX > WIDTH) catX = WIDTH;
        if (catY < 0) catY = 0;
        if (catY > HEIGHT) catY = HEIGHT;
    }
    private void clampMouse(MouseInfo mi) {
        if (mi.x < 0) mi.x = 0;
        if (mi.x > WIDTH) mi.x = WIDTH;
        if (mi.y < 0) mi.y = 0;
        if (mi.y > HEIGHT) mi.y = HEIGHT;
    }

    // =========== Подбор сыра ===========
    private void pickUpCheeseIfPossible(MouseInfo mi) {
        if (mi.carryingCheese) return;
        for (Point c : cheeseList) {
            double d = dist(mi.x, mi.y, c.x, c.y);
            if (d <= MOUSE_PICKUP_RADIUS) {
                mi.carryingCheese = true;
                System.out.println("Mouse picked cheese at " + c);
                break;
            }
        }
    }

    // =========== Сдача сыра в нору ===========
    private void dropCheeseIfInHole(MouseInfo mi) {
        if (!mi.carryingCheese) return;
        for (Point h : holes) {
            double d = dist(mi.x, mi.y, h.x, h.y);
            if (d <= HOLE_ENTER_RADIUS) {
                mi.carryingCheese = false;
                mi.carriedCheeseCount++;
                System.out.println("Mouse delivered cheese => totalDelivered="+mi.carriedCheeseCount);
                break;
            }
        }
    }

    // =========== Проверка победы мышей ===========
    private void checkIfMiceWin() {
        int totalDelivered = 0;
        for (var mi : miceMap.values()) {
            totalDelivered += mi.carriedCheeseCount;
        }
        if (totalDelivered >= TOTAL_CHEESE_TO_WIN) {
            gameOver = true;
            winner = "mice";
            System.out.println("Mice win by delivering enough cheese!");
        }
    }

    // =========== Кот ловит мышей ===========
    private void checkCatCatchesMice() {
        for (var e : miceMap.entrySet()) {
            MouseInfo m = e.getValue();
            if (!m.alive) continue;
            double d = dist(catX, catY, m.x, m.y);
            if (d <= CAT_CATCH_RADIUS) {
                m.alive = false;
                m.carryingCheese = false;
                System.out.println("Cat caught mouse " + e.getKey());
            }
        }
        boolean anyAlive = false;
        for (var mi : miceMap.values()) {
            if (mi.alive) {
                anyAlive = true;
                break;
            }
        }
        if (!anyAlive) {
            gameOver = true;
            winner = "cat";
            System.out.println("Cat wins! All mice are caught.");
        }
    }

    private double dist(int x1, int y1, int x2, int y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    // =========== Геттеры ===========
    public synchronized boolean isGameOver() { return gameOver; }
    public synchronized String getWinner() { return winner; }

    public synchronized int getCatX() { return catX; }
    public synchronized int getCatY() { return catY; }
    public synchronized int getCatVelX() { return catVx; }
    public synchronized int getCatVelY() { return catVy; }
    public synchronized Map<String, MouseInfo> getAllMice() {
        return new HashMap<>(miceMap);
    }
    public synchronized List<Point> getCheeseList() {
        return new ArrayList<>(cheeseList);
    }
    public synchronized List<Point> getHoles() {
        return new ArrayList<>(holes);
    }

    // =========== MouseInfo ===========
    public static class MouseInfo {
        public int x, y;
        public int vx, vy;
        public boolean alive;
        public boolean carryingCheese;
        public int carriedCheeseCount;
    }
}
