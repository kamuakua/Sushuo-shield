package snake;

import java.awt.Color;
import java.awt.Font;

public final class Theme {
    public final Color background = new Color(13, 18, 25);
    public final Color grid = new Color(27, 35, 48);
    public final Color snakeHead = new Color(120, 255, 170);
    public final Color snakeBody = new Color(52, 205, 120);
    public final Color food = new Color(255, 94, 94);
    public final Color text = new Color(228, 238, 255);
    public final Font hudFont = new Font(Font.MONOSPACED, Font.BOLD, 15);

    public static Theme dark() {
        return new Theme();
    }
}
