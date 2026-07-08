package snake;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public final class SnakeGame {
    private SnakeGame() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Sushuo Snake requires a graphical desktop.");
            return;
        }
        SwingUtilities.invokeLater(() -> new GameFrame().launch());
    }
}
