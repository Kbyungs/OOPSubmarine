import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class SubmarineClient extends JFrame {
    private JTextArea textArea;
    private JButton[][] buttons;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectOutputStream objectOut;

    private String userName;
    private String[][] mines;
    private static int num_mine;
    private static int width;
    private boolean myTurn = false;

    public SubmarineClient() {
        // 사용자 이름 및 IP 주소 입력 받기
        userName = JOptionPane.showInputDialog(this, "Enter your username:");
        String serverIP = JOptionPane.showInputDialog(this, "Enter server IP address:");
        String difficulty = JOptionPane.showInputDialog(this, "Enter difficulty (easy, medium, hard):");

        if (difficulty.equalsIgnoreCase("easy")) {
            num_mine = 5;
            width = 5;
        } else if (difficulty.equalsIgnoreCase("medium")) {
            num_mine = 10;
            width = 9;
        } else if (difficulty.equalsIgnoreCase("hard")) {
            num_mine = 15;
            width = 12;
        } else {
            num_mine = 10;
            width = 9;
        }

        // GUI 구성 요소 초기화
        textArea = new JTextArea();
        textArea.setEditable(false);
        buttons = new JButton[width][width];

        // 레이아웃 설정
        JPanel gridPanel = new JPanel(new GridLayout(width, width));
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setPreferredSize(new Dimension(50, 50));
                buttons[i][j].addActionListener(new ButtonListener(i, j));
                gridPanel.add(buttons[i][j]);
            }
        }

        setLayout(new BorderLayout());
        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(gridPanel, BorderLayout.SOUTH);

        // 창 설정
        setTitle("Submarine Client");
        setSize(600, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        mines = new String[num_mine][2];
        for (int i = 0; i < num_mine; i++) {
            String input = JOptionPane.showInputDialog(this, "Enter mine coordinates (x,y) for mine " + (i + 1) + ":");
            String[] temp = input.split(",");
            for (int j = 0; j < 2; j++)
                mines[i][j] = temp[j];
        }

        // 서버와 연결
        connectToServer(serverIP);
    }

    private void connectToServer(String serverIP) {
        try {
            socket = new Socket(serverIP, 9999);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            objectOut = new ObjectOutputStream(socket.getOutputStream());

            // 사용자 이름 및 지뢰 정보를 서버로 전송
            objectOut.writeObject(userName);
            objectOut.writeObject(mines);
            objectOut.flush();

            // 서버 메시지 수신 스레드 시작
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String message;
                        while ((message = in.readLine()) != null) {
                            if (message.startsWith("UPDATE:")) {
                                handleUpdate(message.substring(7));
                            } else if (message.equals("Your turn")) {
                                myTurn = true;
                                enableAllButtons();
                            } else if (message.equals("Not your turn")) {
                                myTurn = false;
                                disableAllButtons();
                            } else {
                                textArea.append(message + "\n");
                                if (message.equals("Game Over") || message.contains("has died")) {
                                    disableAllButtons(); // 게임 종료 시 버튼 비활성화
                                    break; // 게임 종료 시 루프 탈출
                                }
                            }
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

    private void handleUpdate(String update) {
        String[] parts = update.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        String value = parts[2];
        buttons[x][y].setText(value);
        buttons[x][y].setEnabled(false);
    }

    private void sendCoordinates(int x, int y) {
        if (myTurn) {
            out.println("MOVE:" + x + "," + y);
        }
    }

    private void enableAllButtons() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                if (buttons[i][j].getText().equals("")) {
                    buttons[i][j].setEnabled(true);
                }
            }
        }
    }

    private void disableAllButtons() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                buttons[i][j].setEnabled(false);
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
            sendCoordinates(x, y);
        }
    }

    public static void main(String[] args) {
        new SubmarineClient();
    }
}
