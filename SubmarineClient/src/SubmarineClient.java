import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SubmarineClient extends JFrame {
    private JTextArea textArea;
    private JButton abilityUseButton;
    private JButton[][] buttons;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectOutputStream objectOut;
    private ObjectInputStream objectIn;

    private String userName;
    private String ip;
    private String[][] mines;
    private static int num_mine;
    private static int width;
    private Timer timer;
    private Timer aTimer;
    private int abilityUseCount = 1;
    private String abilitySave;

    private boolean myTurn = false;
    private boolean isHost = false;
    private boolean[][] buttonStates;

    private JLabel player1Label;
    private JLabel player2Label;

    public SubmarineClient() {
        setTitle("MZ뢰찾기");
        setSize(600, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        player1Label = new JLabel("Player 1: ");
        player2Label = new JLabel("Player 2: ");
        topPanel.add(player1Label);
        topPanel.add(player2Label);
        add(topPanel, BorderLayout.NORTH);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        abilityUseButton = new JButton("Use Ability");
        abilityUseButton.setPreferredSize(new Dimension(100, 50));
        abilityUseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (abilityUseCount != 0) {
                    abilityUseCount--;
                    performAbility();
                    abilityUseButton.setEnabled(false);
                    out.println("USE_ABILITY:" + userName);
                }
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(abilityUseButton, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        setVisible(true);

        userName = JOptionPane.showInputDialog(this, "Enter your username:");
        ip = JOptionPane.showInputDialog(this, "Enter IP:");

        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket(ip, 9999);
            objectOut = new ObjectOutputStream(socket.getOutputStream());
            objectOut.flush();
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            objectIn = new ObjectInputStream(socket.getInputStream());

            objectOut.writeObject(userName);
            objectOut.flush();

            new Thread(new Runnable() {
                public void run() {
                    try {
                        String message;
                        while ((message = in.readLine()) != null) {
                            handleServerMessage(message);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleServerMessage(String message) {
        if (message.startsWith("UPDATE:")) {
            handleUpdate(message.substring(7));
        } else if (message.equals("your turn")) {
            myTurn = true;
            enableAllButtons();
        } else if (message.equals("not your turn")) {
            myTurn = false;
            disableAllButtons();
        } else if (message.equals("you are the host")) {
            isHost = true;
            showDifficultySelection();
        } else if (message.startsWith("SETTINGS:")) {
            handleSettings(message.substring(9));
        } else if (message.equals("Start Game")) {
            appendText("Game is starting...\n");
            if (myTurn) {
                enableAllButtons();
            }
        } else if (message.contains("HP: ")) {
            updatePlayerLabels(message);
        } else {
            appendText(message + "\n");
            if (message.equals("Game Over") || message.contains("has died")) {
                disableAllButtons();
            }
            if (message.contains("능력을 선택해주세요")) {
                showAbilitySelection();
            }
        }
    }

    private void handleSettings(String settings) {
        String[] parts = settings.split(",");
        width = Integer.parseInt(parts[0]);
        num_mine = Integer.parseInt(parts[1]);

        buttons = new JButton[width][width];
        buttonStates = new boolean[width][width];
        JPanel gridPanel = new JPanel(new GridLayout(width, width));
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setPreferredSize(new Dimension(50, 50));
                buttons[i][j].addActionListener(new ButtonListener(i, j));
                gridPanel.add(buttons[i][j]);
                buttonStates[i][j] = false;
            }
        }
        add(gridPanel, BorderLayout.SOUTH);
        revalidate();
        repaint();

        mines = new String[num_mine][2];
        for (int i = 0; i < num_mine; i++) {
            String input = JOptionPane.showInputDialog(this, "Enter mine coordinates (x,y) for mine " + (i + 1) + ":");
            String[] temp = input.split(",");
            for (int j = 0; j < 2; j++)
                mines[i][j] = temp[j];
        }
        try {
            out.println("MINES:SET");
            objectOut.writeObject(mines);
            objectOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showDifficultySelection() {
        JDialog dialog = new JDialog(this, "Select Difficulty", true);
        dialog.setSize(300, 200);
        dialog.setLayout(new BorderLayout());

        JLabel label = new JLabel("Select Difficulty", JLabel.CENTER);
        dialog.add(label, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        JButton beginnerButton = new JButton("Beginner");
        JButton intermediateButton = new JButton("Intermediate");
        JButton expertButton = new JButton("Expert");

        ActionListener difficultyListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String difficulty = ((JButton) e.getSource()).getText();
                out.println("DIFFICULTY:" + difficulty);
                dialog.dispose();
                timer.stop();
            }
        };

        beginnerButton.addActionListener(difficultyListener);
        intermediateButton.addActionListener(difficultyListener);
        expertButton.addActionListener(difficultyListener);

        buttonPanel.add(beginnerButton);
        buttonPanel.add(intermediateButton);
        buttonPanel.add(expertButton);

        dialog.add(buttonPanel, BorderLayout.CENTER);

        JLabel timerLabel = new JLabel("If no selection is made in 20 seconds, default to Beginner", JLabel.CENTER);
        dialog.add(timerLabel, BorderLayout.SOUTH);

        timer = new Timer(20000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                out.println("DIFFICULTY:Beginner");
                dialog.dispose();
            }
        });
        timer.setRepeats(false);
        timer.start();

        dialog.setVisible(true);
    }

    public void showAbilitySelection() {
        JDialog dialog = new JDialog(this, "Select Ability", true);
        dialog.setSize(300, 400);
        dialog.setLayout(new BorderLayout());

        JLabel label = new JLabel("Select an Ability", JLabel.CENTER);
        dialog.add(label, BorderLayout.NORTH);

        Map<String, String> abilities = getAbilities();

        JPanel buttonPanel = new JPanel(new GridLayout(0, 2));

        for (String abilityKey : abilities.keySet()) {
            JButton abilityButton = new JButton(abilities.get(abilityKey));
            abilityButton.setActionCommand(abilityKey);
            abilityButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    String ability = e.getActionCommand();
                    out.println("ABILITY:" + ability);
                    abilitySave = ability;
                    dialog.dispose();
                    aTimer.stop();
                }
            });
            buttonPanel.add(abilityButton);
        }

        dialog.add(buttonPanel, BorderLayout.CENTER);

        JLabel aTimerLabel = new JLabel("If no selection is made in 30 seconds, a random ability will be assigned", JLabel.CENTER);
        dialog.add(aTimerLabel, BorderLayout.SOUTH);

        aTimer = new Timer(30000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Random random = new Random();
                int randomAbility = random.nextInt(4);
                out.println("ABILITY:" + randomAbility);
                dialog.dispose();
            }
        });
        aTimer.setRepeats(false);
        aTimer.start();

        dialog.setVisible(true);
    }

    private Map<String, String> getAbilities() {
        Map<String, String> abilities = new HashMap<>();
        abilities.put("1", "Explore");
        abilities.put("2", "Ignite");
        abilities.put("3", "Heal");
        abilities.put("4", "Explode");
        return abilities;
    }

    public void performAbility() {
        switch (abilitySave) {
            case "1":
                showExploreGUI();
                break;
            case "2":
                showIgniteGUI();
                break;
            case "3":
                healing();
                break;
            case "4":
                steal();
                break;
            default:
                appendText(userName + " has no abilities.\n");
                break;
        }
    }

    public void showExploreGUI() {
        String input = JOptionPane.showInputDialog(this, "Enter coordinates to explore (x,y):");
        String[] temp = input.split(",");
        int x = Integer.parseInt(temp[0].trim());
        int y = Integer.parseInt(temp[1].trim());
        explore(x, y);
    }

    public void explore(int x, int y) {
        boolean f = false;
        for (int i = 0; i < num_mine; i++) {
            if (Integer.parseInt(mines[i][0]) == x && Integer.parseInt(mines[i][1]) == y) {
                f = true;
            }
        }

        if (f) {
            appendText("There is a mine at (" + x + "," + y + ")\n");
            buttons[x][y].setBackground(Color.RED);
        } else {
            appendText("No mine at (" + x + "," + y + ")\n");
        }
    }

    public void showIgniteGUI() {
        String input = JOptionPane.showInputDialog(this, "Enter coordinates to ignite (x,y):");
        String[] temp = input.split(",");
        int x = Integer.parseInt(temp[0].trim());
        int y = Integer.parseInt(temp[1].trim());
        ignite(x, y);
    }

    public void ignite(int x, int y) {
        buttons[x][y].setBackground(Color.BLACK);
        buttons[x][y].setEnabled(false);
    }

    public void healing() {
        out.println("HEALING:" + "1");
        appendText("You have healed 1 HP!\n");
    }

    public void steal() {
        out.println("STEAL");
        appendText("You have stolen the opponent's turn!\n");
    }

    private void handleUpdate(String update) {
        String[] parts = update.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        String value = parts[2];

        if (value.equals("99")) buttons[x][y].setText("T");
        else if (value.equals("98")) buttons[x][y].setText("M");
        else buttons[x][y].setText(value);

        buttons[x][y].setEnabled(false);
        buttonStates[x][y] = true;
    }

    private void sendCoordinates(int x, int y) {
        if (myTurn && !buttonStates[x][y]) {
            out.println("MOVE:" + x + "," + y);
        }
    }

    private void disableAllButtons() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                buttons[i][j].setEnabled(false);
            }
        }
    }

    private void enableAllButtons() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                if (!buttonStates[i][j]) {
                    buttons[i][j].setEnabled(true);
                }
            }
        }
    }

    private class ButtonListener implements ActionListener {
        private int x, y;

        public ButtonListener(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void actionPerformed(ActionEvent e) {
            if (myTurn && !buttonStates[x][y]) {
                sendCoordinates(x, y);
                buttons[x][y].setEnabled(false);
                buttonStates[x][y] = true;
                myTurn = false;
            }
        }
    }

    private void appendText(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message);
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    private void updatePlayerLabels(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.contains(userName)) {
                player1Label.setText("Player: " + userName + " (HP: " + message.split("HP: ")[1] + ")");
            } else {
                player2Label.setText("Opponent (HP: " + message.split("HP: ")[1] + ")");
            }
        });
    }

    public static void main(String[] args) {
        new SubmarineClient();
    }
}
