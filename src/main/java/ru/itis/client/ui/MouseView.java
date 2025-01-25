package ru.itis.client.ui;

import java.util.concurrent.atomic.AtomicBoolean;

public class MouseView {
    public int x, y;
    public boolean alive;
    public boolean carryingCheese;
    public int score;
    public int velX, velY;
    private AtomicBoolean lastFacingLeft = new AtomicBoolean(false);

    public MouseView(int x, int y, int velX, int velY, boolean alive, boolean carryingCheese, int score, boolean lastFacingLeft) {
        this.x = x;
        this.y = y;
        this.velX = velX;
        this.velY = velY;
        this.alive = alive;
        this.carryingCheese = carryingCheese;
        this.score = score;
        this.lastFacingLeft.set(lastFacingLeft);
    }

    public boolean isLastFacingLeft() {
        return lastFacingLeft.get();
    }

    public void setLastFacingLeft(boolean value) {
        lastFacingLeft.set(value);
    }
}

