package snake;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class GameFrame extends JFrame {
    private final SnakePanel panel;

    public GameFrame() {
        super("Sushuo Snake");
        ScoreBoard scoreBoard = new ScoreBoard();
        SnakeModel model = new SnakeModel(GameConfig.COLUMNS, GameConfig.ROWS, System.nanoTime(), scoreBoard);
        panel = new SnakePanel(model, Theme.dark());
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                panel.stop();
            }
        });
    }

    public void launch() {
        setVisible(true);
        panel.start();
    }
}
