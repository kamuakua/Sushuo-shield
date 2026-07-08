package snake;

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class FoodSpawner {
    private final int columns;
    private final int rows;
    private final Random random;

    public FoodSpawner(int columns, int rows, long seed) {
        this.columns = columns;
        this.rows = rows;
        this.random = new Random(seed);
    }

    public Point2i next(Collection<Point2i> forbidden) {
        Set<Point2i> occupied = new HashSet<>(forbidden);
        int capacity = columns * rows;
        if (occupied.size() >= capacity) {
            return new Point2i(0, 0);
        }
        for (int attempt = 0; attempt < capacity * 2; attempt++) {
            Point2i candidate = new Point2i(random.nextInt(columns), random.nextInt(rows));
            if (!occupied.contains(candidate)) {
                return candidate;
            }
        }
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                Point2i candidate = new Point2i(x, y);
                if (!occupied.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return new Point2i(0, 0);
    }
}
