import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Random;

/**
 * Classic Snake Game implemented in Java using Swing.
 * Features:
 * - Simple, intuitive menu navigation using arrow keys and Enter.
 * - Smooth snake movement with natural growth when eating food.
 * - High score persistence through a small local file.
 * - Pause and game-over screens with minimal, clear controls.
 * - Animated title screen with a decorative snake for a bit of flair.
 * Author: Nikki Conlon
 */
public class SnakeGame extends JPanel implements ActionListener {

    // Grid and speed settings
    private static final int TILE_SIZE = 25;                  // Each cell in the grid is 25px
    private static final int GRID_WIDTH = 20;                 // Total cells horizontally
    private static final int GRID_HEIGHT = 20;                // Total cells vertically
    private static final int BASE_SPEED = 120;                // Initial delay between frames (ms)
    private static final String HIGHSCORE_FILE = "highscore.txt"; // Simple file for saving high score

    // Direction + game state enums
    private enum Direction { UP, DOWN, LEFT, RIGHT }
    private enum State { MENU, RUNNING, PAUSED, GAME_OVER, VIEW_HIGH_SCORE }

    // Core game state
    private ArrayList<Point> snake;  // Holds all snake segments (head is at index 0)
    private Point food;              // Current food location
    private Direction direction;     // Current movement direction
    private State state;             // Which screen/game state we’re in
    private int score, highScore;    // Current score + persistent high score

    private Timer timer;             // Controls game update rate
    private final Random random = new Random();

    // Menu options + currently selected indices
    private final String[] mainMenuOptions = { "Start Game", "View High Score", "Exit" };
    private int selectedMenuOption;

    private final String[] pauseMenuOptions = { "Resume", "Restart", "Exit" };
    private int selectedPauseOption;

    // Title animation state
    private float titlePulse = 0f;
    private boolean pulseIncreasing = true;

    // Decorative snake for the main menu
    private ArrayList<Point> menuSnake;
    private boolean menuSnakeMovingRight = true;

    /**
     * Constructor: sets up the game window, input handling, menu animation,
     * and loads any existing high score.
     */
    public SnakeGame() {
        setPreferredSize(new Dimension(GRID_WIDTH * TILE_SIZE, GRID_HEIGHT * TILE_SIZE));
        setBackground(Color.BLACK);
        setFocusable(true);

        // Keyboard listener handles input, but logic varies by game state
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                requestFocusInWindow();
                switch (state) {
                    case MENU -> handleMenuInput(e);
                    case VIEW_HIGH_SCORE -> {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER)
                            state = State.MENU;
                    }
                    case RUNNING -> handleRunningInput(e);
                    case PAUSED -> handlePauseMenuInput(e);
                    case GAME_OVER -> handleGameOverInput(e);
                }
                repaint();
            }
        });

        loadHighScore();         // Try to load high score (or start fresh)
        state = State.MENU;      // Start on the menu screen
        initMenuSnake();         // Create the decorative snake for the menu

        // Timer for menu animations (title pulsing + snake movement)
        new Timer(150, _ -> {
            if (state == State.MENU) moveMenuSnake();
            animateTitlePulse();
            repaint();
        }).start();

        // Ensures the panel actually receives keyboard focus
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    // Input handling for different states

    private void handleMenuInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP, KeyEvent.VK_LEFT ->
                    selectedMenuOption = (selectedMenuOption - 1 + mainMenuOptions.length) % mainMenuOptions.length;
            case KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT ->
                    selectedMenuOption = (selectedMenuOption + 1) % mainMenuOptions.length;
            case KeyEvent.VK_ENTER -> executeMenuOption();
        }
    }

    private void executeMenuOption() {
        switch (selectedMenuOption) {
            case 0 -> startGame();
            case 1 -> state = State.VIEW_HIGH_SCORE;
            case 2 -> System.exit(0);
        }
    }

    private void handleRunningInput(KeyEvent e) {
        // Prevent reversing into yourself
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP ->    { if (direction != Direction.DOWN)  direction = Direction.UP; }
            case KeyEvent.VK_DOWN ->  { if (direction != Direction.UP)    direction = Direction.DOWN; }
            case KeyEvent.VK_LEFT ->  { if (direction != Direction.RIGHT) direction = Direction.LEFT; }
            case KeyEvent.VK_RIGHT -> { if (direction != Direction.LEFT)  direction = Direction.RIGHT; }
            case KeyEvent.VK_ESCAPE -> state = State.PAUSED;
        }
    }

    private void handlePauseMenuInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP, KeyEvent.VK_LEFT ->
                    selectedPauseOption = (selectedPauseOption - 1 + pauseMenuOptions.length) % pauseMenuOptions.length;
            case KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT ->
                    selectedPauseOption = (selectedPauseOption + 1) % pauseMenuOptions.length;
            case KeyEvent.VK_ENTER -> executePauseMenuOption();
            case KeyEvent.VK_ESCAPE -> state = State.RUNNING;
        }
    }

    private void executePauseMenuOption() {
        switch (selectedPauseOption) {
            case 0 -> state = State.RUNNING;
            case 1 -> startGame();  // Restart
            case 2 -> System.exit(0);
        }
    }

    private void handleGameOverInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> state = State.MENU;
            case KeyEvent.VK_ENTER -> startGame();
        }
    }

    //  Core gameplay logic

    /**
     * Initializes (or resets) the snake, score, food, and movement timer.
     */
    private void startGame() {
        snake = new ArrayList<>();
        snake.add(new Point(GRID_WIDTH / 2, GRID_HEIGHT / 2));  // Start at center
        direction = Direction.RIGHT;
        score = 0;
        selectedPauseOption = 0;
        spawnFood();

        if (timer != null) timer.stop();
        timer = new Timer(BASE_SPEED, this);
        timer.start();
        state = State.RUNNING;
    }

    /**
     * Places food randomly, making sure it doesn’t overlap the snake.
     */
    private void spawnFood() {
        while (true) {
            Point newFood = new Point(random.nextInt(GRID_WIDTH), random.nextInt(GRID_HEIGHT));
            if (snake.stream().noneMatch(s -> s.equals(newFood))) {
                food = newFood;
                break;
            }
        }
    }

    /**
     * Moves the snake forward by adding a new head in the direction of travel,
     * and removing the tail (unless we’re growing from eating food).
     */
    private void moveSnake() {
        Point head = snake.getFirst();
        Point newHead = switch (direction) {
            case UP    -> new Point(head.x, head.y - 1);
            case DOWN  -> new Point(head.x, head.y + 1);
            case LEFT  -> new Point(head.x - 1, head.y);
            case RIGHT -> new Point(head.x + 1, head.y);
        };
        snake.addFirst(newHead);
        snake.removeLast();
    }

    /**
     * Ends the game if the snake hits a wall or its own body.
     */
    private void checkCollisions() {
        Point head = snake.getFirst();
        boolean hitWall = head.x < 0 || head.x >= GRID_WIDTH || head.y < 0 || head.y >= GRID_HEIGHT;
        boolean hitSelf = snake.subList(1, snake.size()).contains(head);
        if (hitWall || hitSelf) {
            gameOver();
        }
    }

    /**
     * Checks if the snake has eaten the food. If yes:
     * - Increase score
     * - Grow the snake by adding an extra tail segment
     * - Slightly increase speed
     * - Spawn new food
     */
    private void checkFood() {
        if (snake.getFirst().equals(food)) {
            score += 10;
            snake.add(new Point(snake.getLast()));  // Grow
            timer.setDelay(Math.max(50, BASE_SPEED - score / 10 * 2)); // Gradual speed-up
            spawnFood();
        }
    }

    /**
     * Switches to GAME_OVER state and updates high score.
     */
    private void gameOver() {
        state = State.GAME_OVER;
        timer.stop();
        if (score > highScore) {
            highScore = score;
            saveHighScore();
        }
    }

    // Drawing + UI

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        switch (state) {
            case MENU -> drawMainMenu(g);
            case VIEW_HIGH_SCORE -> drawHighScoreScreen(g);
            case RUNNING -> drawGame(g);
            case PAUSED -> { drawGame(g); drawPauseMenu(g); }
            case GAME_OVER -> drawGameOver(g);
        }
    }

    private void drawGame(Graphics g) {
        drawFood(g);
        drawSnake(g);
        drawScore(g);
    }

    private void drawFood(Graphics g) {
        g.setColor(Color.RED);
        g.fillOval(food.x * TILE_SIZE, food.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
    }

    private void drawSnake(Graphics g) {
        g.setColor(new Color(0, 200, 0));
        for (Point p : snake) {
            g.fillRect(p.x * TILE_SIZE, p.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }
    }

    private void drawScore(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        String scoreText = "Score: " + score;
        String highScoreText = "High Score: " + highScore;

        int padding = 40;
        int totalWidth = g.getFontMetrics().stringWidth(scoreText) + padding + g.getFontMetrics().stringWidth(highScoreText);
        int x = (getWidth() - totalWidth) / 2;
        int y = 30;

        g.drawString(scoreText, x, y);
        g.drawString(highScoreText, x + g.getFontMetrics().stringWidth(scoreText) + padding, y);
    }

    private void drawPauseMenu(Graphics g) {
        g.setColor(new Color(0, 0, 0, 180)); // Semi-transparent overlay
        g.fillRect(0, 0, getWidth(), getHeight());
        drawCenteredText(g, "PAUSED", Color.YELLOW, 36, getHeight() / 2 - 80);

        g.setFont(new Font("Arial", Font.PLAIN, 22));
        int y = getHeight() / 2 - 20;
        for (int i = 0; i < pauseMenuOptions.length; i++) {
            Color color = (i == selectedPauseOption) ? Color.YELLOW : Color.LIGHT_GRAY;
            drawCenteredText(g,
                    (i == selectedPauseOption ? "> " : "") + pauseMenuOptions[i] + (i == selectedPauseOption ? " <" : ""),
                    color, 22, y + i * 40);
        }
    }

    private void drawGameOver(Graphics g) {
        drawSnake(g);
        drawFood(g);

        int top = 50;
        drawCenteredText(g, "GAME OVER", Color.RED, 36, top);
        drawCenteredText(g, "Score: " + score, Color.WHITE, 20, top + 40);
        drawCenteredText(g, "High Score: " + highScore, Color.YELLOW, 20, top + 70);
        drawCenteredText(g, "Press Enter to Restart", Color.GRAY, 18, top + 120);
        drawCenteredText(g, "Press Esc to Menu", Color.GRAY, 18, top + 150);
    }

    private void drawHighScoreScreen(Graphics g) {
        int centerY = getHeight() / 2 - 40;
        drawCenteredText(g, "HIGH SCORE", Color.YELLOW, 36, centerY);
        drawCenteredText(g, "Your Best: " + highScore, Color.WHITE, 26, centerY + 40);
        drawCenteredText(g, "Press Enter or Esc to return", Color.GRAY, 18, centerY + 100);
    }

    private void drawMainMenu(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        int titleY = getHeight() / 6;
        g.setFont(new Font("Monospaced", Font.BOLD, 48));
        g.setColor(new Color(0, 200, 0, (int) (255 * titlePulse))); // Green pulsing
        drawCenteredText(g, "SNAKE GAME", g.getColor(), 48, titleY);

        // Menu options
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        int menuStartY = titleY + 80;
        for (int i = 0; i < mainMenuOptions.length; i++) {
            // 🔥 Change highlight color to Yellow instead of Blue
            Color color = (i == selectedMenuOption) ? Color.YELLOW : Color.LIGHT_GRAY;
            drawCenteredText(
                    g,
                    (i == selectedMenuOption ? "> " : "") + mainMenuOptions[i] + (i == selectedMenuOption ? " <" : ""),
                    color, 28,
                    menuStartY + i * 50
            );
        }

        //  Place hint below last menu option
        int hintBase = menuStartY + mainMenuOptions.length * 50 + 10;
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        drawCenteredText(g, "USE ARROW KEYS TO NAVIGATE", Color.GRAY, 16, hintBase);
        drawCenteredText(g, "PRESS ENTER TO SELECT", Color.GRAY, 16, hintBase + 20);

        // Draw decorative snake near bottom of menu
        g.setColor(new Color(0, 255, 0));
        for (Point p : menuSnake) {
            g.fillRect(p.x * TILE_SIZE, p.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }
    }


    private void drawCenteredText(Graphics g, String text, Color color, int fontSize, int y) {
        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        g.setColor(color);
        int textWidth = g.getFontMetrics().stringWidth(text);
        int x = (getWidth() - textWidth) / 2;
        g.drawString(text, x, y);
    }

    // Title pulse animation for the main menu
    private void animateTitlePulse() {
        if (pulseIncreasing) {
            titlePulse += 0.05f;
            if (titlePulse >= 1f) pulseIncreasing = false;
        } else {
            titlePulse -= 0.05f;
            if (titlePulse <= 0.3f) pulseIncreasing = true;
        }
    }

    // Decorative menu snake animation
    private void initMenuSnake() {
        menuSnake = new ArrayList<>();
        int startX = 0;
        int y = GRID_HEIGHT - 2;
        for (int i = 0; i < 6; i++) {
            menuSnake.add(new Point(startX + i, y));
        }
        menuSnakeMovingRight = true;
    }

    private void moveMenuSnake() {
        if (menuSnakeMovingRight) {
            Point tail = menuSnake.removeFirst();
            Point head = menuSnake.getLast();
            if (head.x + 1 >= GRID_WIDTH) {
                menuSnakeMovingRight = false;
                menuSnake.add(tail);
            } else {
                menuSnake.add(new Point(head.x + 1, tail.y));
            }
        } else {
            Point head = menuSnake.getFirst();
            Point tail = menuSnake.removeLast();
            if (head.x - 1 < 0) {
                menuSnakeMovingRight = true;
                menuSnake.addFirst(tail);
            } else {
                menuSnake.addFirst(new Point(head.x - 1, tail.y));
            }
        }
    }

    // High score handling

    private void loadHighScore() {
        try (BufferedReader reader = new BufferedReader(new FileReader(HIGHSCORE_FILE))) {
            highScore = Integer.parseInt(reader.readLine());
        } catch (IOException | NumberFormatException e) {
            highScore = 0; // If file missing or corrupted, reset to 0
        }
    }

    private void saveHighScore() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(HIGHSCORE_FILE))) {
            writer.write(Integer.toString(highScore));
        } catch (IOException e) {
            System.err.println("Failed to save high score.");
        }
    }

    // Timer tick updates the snake movement
    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == State.RUNNING) {
            moveSnake();
            checkCollisions();
            checkFood();
            repaint();
        }
    }

    // Entry point
    public static void main(String[] args) {
        JFrame frame = new JFrame("Snake Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        SnakeGame gamePanel = new SnakeGame();
        frame.add(gamePanel);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
