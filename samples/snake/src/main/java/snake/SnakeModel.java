package snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class SnakeModel {
    private final int columns;
    private final int rows;
    private final FoodSpawner foodSpawner;
    private final ScoreBoard scoreBoard;
    private final Deque<Point2i> body = new ArrayDeque<>();

    private Direction direction = Direction.RIGHT;
    private Direction queuedDirection = Direction.RIGHT;
    private Point2i food;
    private int score;
    private int ticks;
    private boolean gameOver;
    private boolean paused;

    public SnakeModel(int columns, int rows, long seed, ScoreBoard scoreBoard) {
        this.columns = columns;
        this.rows = rows;
        this.foodSpawner = new FoodSpawner(columns, rows, seed);
        this.scoreBoard = scoreBoard;
        reset();
    }

    public void reset() {
        body.clear();
        int startX = Math.max(GameConfig.INITIAL_LENGTH + 2, columns / 2);
        int startY = rows / 2;
        for (int i = 0; i < GameConfig.INITIAL_LENGTH; i++) {
            body.addLast(new Point2i(startX - i, startY));
        }
        direction = Direction.RIGHT;
        queuedDirection = Direction.RIGHT;
        food = foodSpawner.next(body);
        score = 0;
        ticks = 0;
        gameOver = false;
        paused = false;
    }

    public void queue(Direction next) {
        if (next != null && !next.opposite(direction)) {
            queuedDirection = next;
        }
    }

    public void togglePause() {
        if (!gameOver) {
            paused = !paused;
        }
    }

    public boolean tick() {
        if (paused || gameOver) {
            return false;
        }
        direction = queuedDirection;
        Point2i nextHead = head().move(direction);
        ticks++;
        if (nextHead.outside(columns, rows)) {
            die();
            return true;
        }

        Point2i tail = body.peekLast();
        boolean eating = nextHead.equals(food);
        if (contains(nextHead) && !(nextHead.equals(tail) && !eating)) {
            die();
            return true;
        }

        body.addFirst(nextHead);
        if (eating) {
            score += 10 + Math.min(90, ticks / 25);
            food = foodSpawner.next(body);
        } else {
            body.removeLast();
        }
        scoreBoard.submit(score);
        return true;
    }

    private void die() {
        gameOver = true;
        scoreBoard.submit(score);
    }

    public Point2i head() {
        return body.peekFirst();
    }

    public boolean contains(Point2i point) {
        return body.contains(point);
    }

    public List<Point2i> bodySnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(body));
    }

    public Point2i food() {
        return food;
    }

    public int score() {
        return score;
    }

    public boolean paused() {
        return paused;
    }

    public boolean gameOver() {
        return gameOver;
    }

    public String status() {
        return scoreBoard.status(score, paused, gameOver);
    }
}
