// 클라이언트 파일

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.swing.Timer; // 명확히 javax.swing.Timer를 사용

public class SubmarineClient extends JFrame {
    private JTextArea textArea; // 서버 메시지를 표시할 텍스트 영역
    private JButton abilityUseButton; // 능력 사용 버튼
    private JButton[][] buttons; // 게임 버튼 배열
    private Socket socket; // 서버와의 소켓 연결
    private PrintWriter out; // 서버로의 출력 스트림
    private BufferedReader in; // 서버로부터의 입력 스트림
    private ObjectOutputStream objectOut; // 객체 전송용 출력 스트림
    private ObjectInputStream objectIn; // 객체 수신용 입력 스트림

    private String userName; // 사용자 이름
    private String ip; // 서버 IP 주소
    private String[][] mines; // 지뢰 위치 배열
    private static int num_mine; // 지뢰 수
    private static int width; // 맵 너비
    private Timer timer; // 선택 시간 타이머
    private Timer aTimer; // 능력 선택 시간 타이머
    private int abilityUseCount = 1;
    private String abilitySave;

    private boolean myTurn = false;
    private boolean isHost = false; // 방장 여부 확인

    private int treasuresFound = 0; // 찾은 보물의 개수를 저장
    private int minesHit = 0;     // 밟은 지뢰의 개수를 저장

    // 버튼의 활성화/비활성화 상태를 추적하기 위한 배열
    private boolean[][] buttonStates;

    private JLabel player1HpLabel; // Player 1의 HP를 표시할 라벨
    private JLabel player2HpLabel; // Player 2의 HP를 표시할 라벨
    private String player1Name; // Player 1의 이름
    private String player2Name; // Player 2의 이름

    public SubmarineClient() {
        // GUI 구성 요소 초기화
        textArea = new JTextArea();
        textArea.setEditable(false); // 텍스트 영역은 수정 불가
        abilityUseButton = new JButton("능력 발동");
        abilityUseButton.setPreferredSize(new Dimension(100, 600));

        abilityUseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (abilityUseCount != 0) {
                    abilityUseCount--;
                    System.out.println("능력 발동!");
                    performAbility();
                    abilityUseButton.setEnabled(false);
                    out.println("USE_ABILITY:" + userName); // 서버로 능력 사용 메시지 전송
                } else {
                    System.out.println("능력을 모두 사용했습니다.");
                }
            }
        });

        setLayout(new BorderLayout());
        add(new JScrollPane(textArea), BorderLayout.CENTER); // 텍스트 영역을 중앙에 추가
        add(abilityUseButton, BorderLayout.EAST); // 채팅창 옆에 능력 사용 버튼 추가

        // 상단 패널에 두 플레이어의 HP 표시 라벨 추가
        JPanel hpPanel = new JPanel(new GridLayout(1, 2));
        player1HpLabel = new JLabel("Player 1: ❤️ ❤️ ❤️");
        player2HpLabel = new JLabel("Player 2: ❤️ ❤️ ❤️");
        hpPanel.add(player1HpLabel);
        hpPanel.add(player2HpLabel);
        add(hpPanel, BorderLayout.NORTH);

        // 창 설정
        setTitle("MZ뢰찾기");
        setSize(600, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        // 사용자 이름 및 서버 IP 입력 받기
        userName = JOptionPane.showInputDialog(this, "Enter your username:");
        ip = JOptionPane.showInputDialog(this, "Enter IP:");

        // 서버와 연결
        connectToServer();
    }

    // 서버와의 연결 설정 메서드
    private void connectToServer() {
        try {
            System.out.println("Attempting to connect to the server at " + ip + ":" + 9999);
            socket = new Socket(ip, 9999); // 서버 소켓에 연결
            System.out.println("Connected to the server.");
            objectOut = new ObjectOutputStream(socket.getOutputStream()); // 객체 출력 스트림 설정
            objectOut.flush(); // flush() 추가
            System.out.println("ObjectOutputStream created");

            out = new PrintWriter(socket.getOutputStream(), true); // 출력 스트림 설정
            System.out.println("PrintWriter created");
            in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 입력 스트림 설정
            System.out.println("BufferedReader created");
            objectIn = new ObjectInputStream(socket.getInputStream()); // 객체 입력 스트림 설정
            System.out.println("ObjectInputStream created");

            // 사용자 이름 서버로 전송
            objectOut.writeObject(userName);
            objectOut.flush(); // flush() 추가
            System.out.println("Username sent to server");

            // 서버로부터의 메시지를 수신하는 스레드 시작
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String message;
                        while ((message = in.readLine()) != null) { // 서버로부터 메시지 수신
                            System.out.println("Received from server: " + message); // 디버그 메시지 추가
                            if (message.startsWith("UPDATE:")) { // 업데이트 메시지 처리
                                handleUpdate(message.substring(7));
                            } else if (message.equals("your turn")) {
                                myTurn = true;
                                enableAllButtons();
                            } else if (message.equals("not your turn")) {
                                myTurn = false;
                                disableAllButtons();
                            } else if (message.equals("you are the host")) {
                                isHost = true;
                                showDifficultySelection(); // 방장일 경우 난이도 선택
                            } else if (message.startsWith("SETTINGS:")) {
                                handleSettings(message.substring(9)); // 난이도 설정 후 초기화
                            } else if (message.equals("Start Game")) {
                                textArea.append("Game is starting...\n"); // 게임 시작 메시지 표시
                                textArea.setCaretPosition(textArea.getDocument().getLength()); // 스크롤 자동 아래로
                                if (myTurn) {
                                    enableAllButtons();
                                }
                            } else if (message.startsWith("HP:")) { // HP 정보 수신 시
                                updateHpDisplay(message.substring(3));
                            } else if (message.startsWith("Winner:")) { // 승리 메시지 수신 시
                                showGameResultDialog(message);
                                break; // 게임 종료 시 루프 탈출
                            } else if (message.startsWith("BUTTONBLACK:")) {
                                setButtonColorBlack(message.substring(12));
                            } else if (message.startsWith("MINECHECK:")) {
                                explore(message.substring(10));
                            } else {
                                textArea.append(message + "\n"); // 일반 메시지 텍스트 영역에 추가
                                textArea.setCaretPosition(textArea.getDocument().getLength()); // 스크롤 자동 아래로
                                if (message.equals("Game Over") || message.contains("has died")) {
                                    disableAllButtons(); // 게임 종료 시 버튼 비활성화
                                    showGameEndDialog(); // 게임 종료 대화 상자 표시
                                    break; // 게임 종료 시 루프 탈출
                                }
                                if (message.contains("능력을 선택해주세요")) { // 능력 선택 메시지 수신 시
                                    showAbilitySelection(); // 능력 선택 창 표시
                                }
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Error during server communication: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            System.err.println("Failed to connect to the server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 서버로부터의 설정 메시지를 처리하는 메서드
    private void handleSettings(String settings) {
        String[] parts = settings.split(",");
        width = Integer.parseInt(parts[0]);
        num_mine = Integer.parseInt(parts[1]);

        buttons = new JButton[width][width]; // 버튼 배열 초기화
        buttonStates = new boolean[width][width]; // 버튼 상태 배열 초기화
        JPanel gridPanel = new JPanel(new GridLayout(width, width));
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setPreferredSize(new Dimension(50, 50));
                buttons[i][j].addActionListener(new ButtonListener(i, j)); // 버튼에 리스너 추가
                gridPanel.add(buttons[i][j]);
                buttonStates[i][j] = false; // 초기 상태는 false
            }
        }
        add(gridPanel, BorderLayout.SOUTH); // 그리드 패널을 남쪽에 추가
        revalidate();
        repaint();

        // 지뢰 위치 입력 받기
        mines = new String[num_mine][2];
        for (int i = 0; i < num_mine; i++) {
            String input = JOptionPane.showInputDialog(this, "Enter mine coordinates (x,y) for mine " + (i + 1) + ":");
            String[] temp = input.split(",");
            for (int j = 0; j < 2; j++)
                mines[i][j] = temp[j];
        }
        try {
            out.println("MINES:SET");
            objectOut.writeObject(mines); // 지뢰 위치 서버로 전송
            objectOut.flush(); // flush() 추가
        } catch (IOException e) {
            System.err.println("Error during mine setting: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 난이도 선택 창을 표시하는 메서드
    private void showDifficultySelection() {
        // 난이도 선택 창 생성
        JDialog dialog = new JDialog(this, "난이도 선택", true);
        dialog.setSize(600, 200);
        dialog.setLayout(new BorderLayout());

        // 난이도 선택 메시지 라벨
        JLabel label = new JLabel("난이도를 선택해주세요", JLabel.CENTER);
        dialog.add(label, BorderLayout.NORTH);

        // 버튼 패널 생성 및 버튼 추가
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        JButton beginnerButton = new JButton("Beginner");
        JButton intermediateButton = new JButton("Intermediate");
        JButton expertButton = new JButton("Expert");

        // 버튼 리스너 설정
        ActionListener difficultyListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String difficulty = ((JButton) e.getSource()).getText();
                out.println("DIFFICULTY:" + difficulty); // 선택한 난이도를 서버로 전송
                dialog.dispose(); // 선택 후 대화 상자 닫기
                timer.stop(); // 타이머 중지
            }
        };

        beginnerButton.addActionListener(difficultyListener);
        intermediateButton.addActionListener(difficultyListener);
        expertButton.addActionListener(difficultyListener);

        buttonPanel.add(beginnerButton);
        buttonPanel.add(intermediateButton);
        buttonPanel.add(expertButton);

        dialog.add(buttonPanel, BorderLayout.CENTER);

        // 타이머 메시지 라벨
        JLabel timerLabel = new JLabel("20초간 선택하지 않을 경우 기본 난이도인 Beginner로 설정됩니다", JLabel.CENTER);
        dialog.add(timerLabel, BorderLayout.SOUTH);

        // 20초 후 기본 난이도로 설정
        timer = new Timer(20000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                out.println("DIFFICULTY:Beginner"); // 기본 난이도 전송
                dialog.dispose(); // 대화 상자 닫기
            }
        });
        timer.setRepeats(false); // 타이머 반복 설정 안함
        timer.start(); // 타이머 시작

        dialog.setVisible(true);
    }

    // 능력 선택 창을 표시하는 메서드
    public void showAbilitySelection() {
        // 능력 선택 창 생성
        JDialog dialog = new JDialog(this, "능력 선택", true);
        dialog.setSize(300, 400);
        dialog.setLayout(new BorderLayout());

        // 능력 선택 메시지 라벨
        JLabel label = new JLabel("능력 카드를 선택해주세요", JLabel.CENTER);
        dialog.add(label, BorderLayout.NORTH);

        // 능력 목록 가져오기
        Map<String, String> abilities = getAbilities();

        // 버튼 패널 생성 및 버튼 추가
        JPanel buttonPanel = new JPanel(new GridLayout(0, 2)); // 2열의 그리드 레이아웃

        for (String abilityKey : abilities.keySet()) {
            JButton abilityButton = new JButton(abilities.get(abilityKey));
            abilityButton.setActionCommand(abilityKey); // 버튼의 액션 커맨드로 능력 키 설정
            abilityButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    String ability = e.getActionCommand();
                    out.println("ABILITY:" + ability); // 선택한 능력을 서버로 전송
                    abilitySave = ability;
                    System.out.println(abilitySave);
                    dialog.dispose(); // 선택 후 대화 상자 닫기
                    aTimer.stop(); // 타이머 중지
                }
            });
            buttonPanel.add(abilityButton);
        }

        dialog.add(buttonPanel, BorderLayout.CENTER);

        // 타이머 메시지 라벨
        JLabel aTimerLabel = new JLabel("30초간 선택하지 않으면 무작위 능력이 부여됩니다", JLabel.CENTER);
        dialog.add(aTimerLabel, BorderLayout.SOUTH);

        // 30초 후 무작위 능력으로 설정
        aTimer = new Timer(30000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Random random = new Random();
                int randomAbility = random.nextInt(4);
                out.println("ABILITY:" + randomAbility); // 무작위 능력 전송
                dialog.dispose(); // 대화 상자 닫기
            }
        });
        aTimer.setRepeats(false); // 타이머 반복 설정 안함
        aTimer.start(); // 타이머 시작

        dialog.setVisible(true);
    }

    // 능력 목록을 반환하는 메서드
    private Map<String, String> getAbilities() {
        Map<String, String> abilities = new HashMap<>();
        abilities.put("1", "탐색");
        abilities.put("2", "발화");
        abilities.put("3", "회복");
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
            default:
                System.out.println(userName + " 님은 능력이 없습니다.");
                break;
        }
    }

    // 탐색 능력 입력창 띄우기
    public void showExploreGUI() {
        String input = JOptionPane.showInputDialog(this, "탐색을 원하는 좌표를 입력하세요" + ":");
        String[] temp = input.split(",");
//        int x = Integer.parseInt(temp[0].trim());
//        int y = Integer.parseInt(temp[1].trim());
//        explore(x, y);
        int x = Integer.parseInt(temp[0]);
        int y = Integer.parseInt(temp[1]);
        out.println("MINECHECK:" + x + "," + y);
        temp[0] = null;
        temp[1] = null;
    }

    // 탐색 능력 구현
    public void explore(String msg) {
        String[] parts = msg.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        int r = Integer.parseInt(parts[2]);
        if (r == 1) {
            textArea.append("해당 위치는 지뢰가 있습니다\n");
            buttons[x][y].setBackground(Color.RED);
        } else if (r == 0) {
            textArea.append("해당 위치는 지뢰가 없습니다\n");
        }
    }


    // 발화 능력 입력창 띄우기
    public void showIgniteGUI() {
        String input = JOptionPane.showInputDialog(this, "불태우기를 원하는 좌표를 입력하세요" + ":");
        String[] temp = input.split(",");
        int x = Integer.parseInt(temp[0].trim());
        int y = Integer.parseInt(temp[1].trim());
        ingite(x, y);
        temp[0] = null;
        temp[1] = null;
    }

    public void ingite(int x, int y) {
        out.println("BUTTONBLACK:" + x + "," + y);
    }

    // 회복 능력 구현
    public void healing() {
        out.println("HEALING:" + "1");
        textArea.append("체력을 1 회복했습니다!\n");
        textArea.setCaretPosition(textArea.getDocument().getLength()); // 스크롤 자동 아래로
    }

    // 서버로부터의 업데이트 메시지를 처리하는 메서드
    private void handleUpdate(String update) {
        String[] parts = update.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        String value = parts[2];

        if (value.equals("99")) buttons[x][y].setText("T");
        else if (value.equals("98")) buttons[x][y].setText("M");
        else buttons[x][y].setText(value); // 버튼에 값 설정

        buttons[x][y].setEnabled(false); // 버튼 비활성화
        buttonStates[x][y] = true; // 버튼 상태를 true로 설정
    }

    // 서버로 좌표를 전송하는 메서드
    private void sendCoordinates(int x, int y) {
        if (myTurn && !buttonStates[x][y]) { // 버튼이 활성화되어 있는 경우에만
            out.println("MOVE:" + x + "," + y);
        }
    }

    private void setButtonColorBlack(String msg) {
        String[] parts = msg.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        buttons[x][y].setBackground(Color.BLACK);
        buttons[x][y].setEnabled(false);
    }

    // 모든 버튼을 비활성화하는 메서드
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
                if (!buttonStates[i][j]) { // 버튼이 비활성화되어 있지 않은 경우에만
                    buttons[i][j].setEnabled(true);
                }
            }
        }
    }

    private void requestGameStatistics() {
        out.println("REQUEST_STATS");
        // 통계 정보를 요청한 후, 루프 외부에서 통계 정보를 수신하고 처리하는 부분을 추가합니다.
        new Thread(new Runnable() {
            public void run() {
                try {
                    String message;
                    while ((message = in.readLine()) != null) { // 서버로부터 메시지 수신
                        if (message.startsWith("STATS:")) {
                            showGameStatistics(message);
                            break; // 통계 정보를 수신한 후 루프를 종료합니다.
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error during server communication: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 게임 종료 시 대화 상자 표시
    private void showGameEndDialog() {
        int response = JOptionPane.showOptionDialog(
                this,
                "결과를 확인하려면 Yes, 종료를 원하면 No를 누르세요",
                "Game Over",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Yes", "No"},
                "Yes"
        );

        if (response == JOptionPane.YES_OPTION) {
            requestGameStatistics();
        } else {
            exitGame();
        }
    }

    private void exitGame() {
        try {
            // 서버에 종료 요청
            out.println("EXIT_GAME");

            // 소켓 및 스트림 닫기
            if (objectOut != null) objectOut.close();
            if (objectIn != null) objectIn.close();
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();

            // 프로그램 종료
            System.out.println("Game exited.");
            System.exit(0);
        } catch (IOException e) {
            System.err.println("Error while exiting the game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 게임 통계 표시
    private void showGameStatistics(String msg) {
        String stats = msg.substring(6);
        String[] parts = stats.split(",");
        treasuresFound = Integer.parseInt(parts[0]);
        minesHit = Integer.parseInt(parts[1]);

        String message = String.format(
                "Found Treasures: %d\nStepped Mines: %d",
                treasuresFound, minesHit
        );
        JOptionPane.showMessageDialog(this, message, "Game Statistics", JOptionPane.INFORMATION_MESSAGE);
    }

    // 버튼 클릭 리스너 클래스
    private class ButtonListener implements ActionListener {
        private int x, y;

        public ButtonListener(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void actionPerformed(ActionEvent e) {
            if (myTurn && !buttonStates[x][y]) { // 버튼이 활성화되어 있는 경우에만
                sendCoordinates(x, y); // 좌표 전송
                buttons[x][y].setEnabled(false); // 클릭된 버튼 비활성화
                buttonStates[x][y] = true; // 버튼 상태를 true로 설정
                myTurn = false; // 턴 종료
            }
        }
    }

    // HP 표시를 업데이트하는 메서드
    private void updateHpDisplay(String message) {
        String[] parts = message.split(",");
        String player1Name = parts[0];
        int player1Hp = Integer.parseInt(parts[1].trim());
        String player2Name = parts[2];
        int player2Hp = Integer.parseInt(parts[3].trim());

        player1HpLabel.setText(player1Name + " : " + "❤️ ".repeat(Math.max(0, player1Hp)));
        player2HpLabel.setText(player2Name + " : " + "❤️ ".repeat(Math.max(0, player2Hp)));
    }

    // 게임 결과를 표시하는 다이얼로그
    private void showGameResultDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Game Result", JOptionPane.INFORMATION_MESSAGE);
        showGameEndDialog();
    }

    public static void main(String[] args) {
        new SubmarineClient(); // 클라이언트 실행
    }
}
