package snake;

public final class GameConfig {
    public static final int COLUMNS = 28;
    public static final int ROWS = 22;
    public static final int CELL_SIZE = 24;
    public static final int INITIAL_LENGTH = 5;
    public static final int TICK_MILLIS = 86;
    public static final int PANEL_WIDTH = COLUMNS * CELL_SIZE;
    public static final int PANEL_HEIGHT = ROWS * CELL_SIZE;

    private GameConfig() {
    }
}
