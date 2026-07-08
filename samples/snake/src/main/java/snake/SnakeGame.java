package snake;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public final class SnakeGame {
    private SnakeGame() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            System.out.println(SimulationHarness.runSmokeTest());
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println(SimulationHarness.runSmokeTest());
            return;
        }
        SwingUtilities.invokeLater(() -> new GameFrame().launch());
    }
}
