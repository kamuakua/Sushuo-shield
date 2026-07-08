package snake;

public final class ScoreBoard {
    private int best;

    public void submit(int score) {
        if (score > best) {
            best = score;
        }
    }

    public int best() {
        return best;
    }

    public String status(int score, boolean paused, boolean gameOver) {
        String state = gameOver ? "GAME OVER" : (paused ? "PAUSED" : "RUNNING");
        return "Score: " + score + "   Best: " + best + "   " + state
                + "   Space: pause/resume   R: restart";
    }
}
