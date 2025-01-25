package ru.itis.model;

import java.awt.Point;
import java.util.*;

public class GameState {
    private static final int HOLE_SIZE = 32;

    private static final int CAT_CATCH_RADIUS = 25;
    private static final int MOUSE_PICKUP_RADIUS = 20;
    private static final int HOLE_ENTER_RADIUS = 25;

    private static final double CAT_SPEED_MULTIPLIER = 1.5;
    private static final double MOUSE_SPEED_MULTIPLIER = 1.6;

    public static int totalCheeseToWin = 3;

    public static final int WIDTH = 1040;
    public static final int HEIGHT = 780;

    private int catX, catY;
    private int catVx, catVy;

    private final Map<String, MouseInfo> miceMap = new HashMap<>();
    private final List<Point> cheeseList = new ArrayList<>();
    private final List<Point> holes = new ArrayList<>();

    private boolean gameOver = false;
    private String winner = null;

    public GameState() {
        initCheeseAndHoles();
    }

    private void initCheeseAndHoles() {
        cheeseList.clear();
        int cx = WIDTH / 2;
        int cy = HEIGHT / 2;
        
        cheeseList.add(new Point(cx - 60, cy));
        cheeseList.add(new Point(cx + 60, cy));

        holes.clear();
        holes.add(new Point(WIDTH / 2, HOLE_SIZE + 5));
        holes.add(new Point(WIDTH / 2, HEIGHT - HOLE_SIZE - 5));
        holes.add(new Point(HOLE_SIZE + 5, HEIGHT / 2));
        holes.add(new Point(WIDTH - HOLE_SIZE - 5, HEIGHT / 2));
    }

    public synchronized void reset() {
        catX = WIDTH / 2;
        catY = HEIGHT / 2;
        catVx = catVy = 0;
        miceMap.clear();
        gameOver = false;
        winner = null;
        initCheeseAndHoles();
        Random rnd = new Random();
        List<Point> holes = getHoles();
        for (String mouseId : miceMap.keySet()) {
            Point hole = holes.get(rnd.nextInt(holes.size()));
            int x = hole.x + rnd.nextInt(41) - 20;
            int y = hole.y + rnd.nextInt(41) - 20;
            x = Math.max(0, Math.min(WIDTH, x));
            y = Math.max(0, Math.min(HEIGHT, y));

            miceMap.get(mouseId).x = x;
            miceMap.get(mouseId).y = y;
        }
    }

    public synchronized void setTotalCheeseToWin(int total) {
        totalCheeseToWin = total;
    }

    public synchronized void setCatPosition(int x, int y) {
        catX = x;
        catY = y;
    }
    public synchronized void setCatVelocity(int vx, int vy) {
        catVx = (int)(vx * CAT_SPEED_MULTIPLIER);
        catVy = (int)(vy * CAT_SPEED_MULTIPLIER);
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
            mi.vx = (int)(vx * MOUSE_SPEED_MULTIPLIER);
            mi.vy = (int)(vy * MOUSE_SPEED_MULTIPLIER);
        }
    }

    public synchronized void updatePositions() {
        catX += catVx;
        catY += catVy;
        clampCat();
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


    private void checkIfMiceWin() {
        int totalDelivered = 0;
        for (var mi : miceMap.values()) {
            totalDelivered += mi.carriedCheeseCount;
        }
        if (totalDelivered >= totalCheeseToWin) {
            gameOver = true;
            winner = "mice";
            System.out.println("Mice win by delivering enough cheese!");
        }
    }


    private void checkCatCatchesMice() {
        for (var e : miceMap.entrySet()) {
            MouseInfo m = e.getValue();
            if (!m.alive) continue;

            if (Math.abs(m.vx) > 0.1) {
                m.lastFacingLeft = m.vx < 0;
            }

            double d = dist(catX, catY, m.x, m.y);
            if (d <= CAT_CATCH_RADIUS) {
                m.alive = false;
                m.carryingCheese = false;
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

    public static class MouseInfo {
        public int x, y;
        public int vx, vy;
        public boolean alive;
        public boolean carryingCheese;
        public int carriedCheeseCount;
        public boolean lastFacingLeft;
    }

    public synchronized boolean isGameOver() { return gameOver; }
    public synchronized String getWinner() { return winner; }
    public synchronized int getCatX() { return catX; }
    public synchronized int getCatY() { return catY; }
    public synchronized int getCatVelX() { return catVx; }
    public synchronized int getCatVelY() { return catVy; }
    public synchronized Map<String, MouseInfo> getAllMice() { return new HashMap<>(miceMap); }
    public synchronized List<Point> getCheeseList() { return new ArrayList<>(cheeseList); }
    public synchronized List<Point> getHoles() { return new ArrayList<>(holes); }
}
