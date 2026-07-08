package snake;

import java.util.Objects;

public final class Point2i {
    private final int x;
    private final int y;

    public Point2i(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Point2i move(Direction direction) {
        return new Point2i(x + direction.dx(), y + direction.dy());
    }

    public boolean outside(int columns, int rows) {
        return x < 0 || y < 0 || x >= columns || y >= rows;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Point2i point)) {
            return false;
        }
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
