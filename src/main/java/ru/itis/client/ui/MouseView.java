package ru.itis.client.ui;

public class MouseView {
    public int x, y;
    public boolean alive;
    public boolean carryingCheese;
    public int score;

    public MouseView(int x, int y, boolean alive, boolean carryingCheese, int score) {
        this.x = x;
        this.y = y;
        this.alive = alive;
        this.carryingCheese = carryingCheese;
        this.score = score;
    }
}
