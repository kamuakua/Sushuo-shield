package snake;

public final class SimulationHarness {
    private SimulationHarness() {
    }

    public static String runSmokeTest() {
        ScoreBoard board = new ScoreBoard();
        SnakeModel model = new SnakeModel(GameConfig.COLUMNS, GameConfig.ROWS, 0x5A17C0DEL, board);
        for (int i = 0; i < 24 && !model.gameOver(); i++) {
            if (i == 5) {
                model.queue(Direction.DOWN);
            } else if (i == 10) {
                model.queue(Direction.LEFT);
            } else if (i == 15) {
                model.queue(Direction.UP);
            } else if (i == 20) {
                model.queue(Direction.RIGHT);
            }
            model.tick();
        }
        if (model.head() == null) {
            throw new IllegalStateException("missing head");
        }
        return "SNAKE_OK score=" + model.score()
                + " best=" + board.best()
                + " size=" + model.bodySnapshot().size()
                + " over=" + model.gameOver();
    }
}
