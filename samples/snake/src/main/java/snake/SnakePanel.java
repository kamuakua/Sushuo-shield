package snake;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public final class SnakePanel extends JPanel {
    private final SnakeModel model;
    private final Theme theme;
    private final Timer timer;

    public SnakePanel(SnakeModel model, Theme theme) {
        this.model = model;
        this.theme = theme;
        this.timer = new Timer(GameConfig.TICK_MILLIS, event -> {
            model.tick();
            repaint();
        });
        setPreferredSize(new Dimension(GameConfig.PANEL_WIDTH, GameConfig.PANEL_HEIGHT + 32));
        setFocusable(true);
        setDoubleBuffered(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                handleKey(event.getKeyCode());
            }
        });
    }

    public void start() {
        timer.start();
        requestFocusInWindow();
    }

    public void stop() {
        timer.stop();
    }

    private void handleKey(int key) {
        switch (key) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> model.queue(Direction.UP);
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> model.queue(Direction.DOWN);
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> model.queue(Direction.LEFT);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> model.queue(Direction.RIGHT);
            case KeyEvent.VK_SPACE -> model.togglePause();
            case KeyEvent.VK_R -> model.reset();
            default -> {
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBackground(g);
            drawFood(g, model.food());
            drawSnake(g, model.bodySnapshot());
            drawHud(g);
        } finally {
            g.dispose();
        }
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(theme.background);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(theme.grid);
        for (int x = 0; x <= GameConfig.COLUMNS; x++) {
            int px = x * GameConfig.CELL_SIZE;
            g.drawLine(px, 0, px, GameConfig.PANEL_HEIGHT);
        }
        for (int y = 0; y <= GameConfig.ROWS; y++) {
            int py = y * GameConfig.CELL_SIZE;
            g.drawLine(0, py, GameConfig.PANEL_WIDTH, py);
        }
    }

    private void drawFood(Graphics2D g, Point2i food) {
        int pad = 5;
        g.setColor(theme.food);
        g.fillOval(food.x() * GameConfig.CELL_SIZE + pad,
                food.y() * GameConfig.CELL_SIZE + pad,
                GameConfig.CELL_SIZE - pad * 2,
                GameConfig.CELL_SIZE - pad * 2);
    }

    private void drawSnake(Graphics2D g, List<Point2i> body) {
        for (int i = body.size() - 1; i >= 0; i--) {
            Point2i point = body.get(i);
            g.setColor(i == 0 ? theme.snakeHead : theme.snakeBody);
            int pad = i == 0 ? 3 : 4;
            g.fillRoundRect(point.x() * GameConfig.CELL_SIZE + pad,
                    point.y() * GameConfig.CELL_SIZE + pad,
                    GameConfig.CELL_SIZE - pad * 2,
                    GameConfig.CELL_SIZE - pad * 2,
                    10,
                    10);
        }
    }

    private void drawHud(Graphics2D g) {
        g.setFont(theme.hudFont);
        g.setColor(theme.text);
        g.drawString(model.status(), 12, GameConfig.PANEL_HEIGHT + 22);
    }
}
