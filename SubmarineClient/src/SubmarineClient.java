package submarineclient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class SubmarineClient extends JFrame {
    private JTextArea textArea; // 서버 메시지를 표시할 텍스트 영역
    private JButton[][] buttons; // 게임 버튼 배열
    private Socket socket; // 서버와의 소켓 연결
    private PrintWriter out; // 서버로의 출력 스트림
    private BufferedReader in; // 서버로부터의 입력 스트림
    private ObjectOutputStream objectOut; // 객체 전송용 출력 스트림

    private String userName; // 사용자 이름
    private String ip; // 서버 IP 주소
    private String[][] mines; // 지뢰 위치 배열
    private static final int num_mine = 3; // 지뢰 수
    private static final int width = 9; // 맵 너비
    private Timer timer; // 선택 시간 타이머

    public SubmarineClient() {
        // GUI 구성 요소 초기화
        textArea = new JTextArea();
        textArea.setEditable(false); // 텍스트 영역은 수정 불가
        buttons = new JButton[width][width];

        // 그리드 레이아웃 설정
        JPanel gridPanel = new JPanel(new GridLayout(width, width));
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setPreferredSize(new Dimension(50, 50));
                buttons[i][j].addActionListener(new ButtonListener(i, j)); // 버튼에 리스너 추가
                gridPanel.add(buttons[i][j]);
            }
        }

        setLayout(new BorderLayout());
        add(new JScrollPane(textArea), BorderLayout.CENTER); // 텍스트 영역을 중앙에 추가
        add(gridPanel, BorderLayout.SOUTH); // 그리드 패널을 남쪽에 추가

        // 창 설정
        setTitle("MZ뢰찾기");
        setSize(600, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        // 사용자 이름 및 지뢰 위치 입력 받기
        userName = JOptionPane.showInputDialog(this, "Enter your username:");
        ip = JOptionPane.showInputDialog(this, "Enter IP:");
        mines = new String[num_mine][2];
        for (int i = 0; i < num_mine; i++) {
            String input = JOptionPane.showInputDialog(this, "Enter mine coordinates (x,y) for mine " + (i + 1) + ":");
            String[] temp = input.split(",");
            for (int j = 0; j < 2; j++)
                mines[i][j] = temp[j];
        }

        // 서버와 연결
        connectToServer();
    }

    // 서버와의 연결 설정 메서드
    private void connectToServer() {
        try {
            socket = new Socket(ip, 9999); // 서버 소켓에 연결
            out = new PrintWriter(socket.getOutputStream(), true); // 출력 스트림 설정
            in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 입력 스트림 설정
            objectOut = new ObjectOutputStream(socket.getOutputStream()); // 객체 출력 스트림 설정

            // 사용자 이름 및 지뢰 정보를 서버로 전송
            objectOut.writeObject(userName);
            objectOut.writeObject(mines);
            objectOut.flush();

            // 서버로부터의 메시지를 수신하는 스레드 시작
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String message;
                        while ((message = in.readLine()) != null) { // 서버로부터 메시지를 수신
                            if (message.startsWith("UPDATE:")) { // 업데이트 메시지 처리
                                handleUpdate(message.substring(7));
                            } else {
                                textArea.append(message + "\n"); // 일반 메시지 텍스트 영역에 추가
                                if (message.equals("Game Over") || message.contains("has died")) {
                                    disableAllButtons(); // 게임 종료 시 버튼 비활성화
                                    break; // 게임 종료 시 루프 탈출
                                }
                                if (message.contains("당신은 방장입니다")) {
                                    showDifficultySelection(); // 방장에게 난이도 선택 창 표시
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

    // 난이도 선택 창을 표시하는 메서드
    private void showDifficultySelection() {
        // 난이도 선택 창 생성
        JDialog dialog = new JDialog(this, "난이도 선택", true);
        dialog.setSize(600, 600);
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

    // 서버로부터의 업데이트 메시지를 처리하는 메서드
    private void handleUpdate(String update) {
        String[] parts = update.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        String value = parts[2];
        buttons[x][y].setText(value); // 버튼에 값 설정
        buttons[x][y].setEnabled(false); // 버튼 비활성화
    }

    // 서버로 좌표를 전송하는 메서드
    private void sendCoordinates(int x, int y) {
        out.println("MOVE:" + x + "," + y);
    }

    // 모든 버튼을 비활성화하는 메서드
    private void disableAllButtons() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                buttons[i][j].setEnabled(false);
            }
        }
    }

    // 버튼 클릭 리스너 클래스
    private class ButtonListener implements ActionListener {
        private int x, y;

        public ButtonListener(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void actionPerformed(ActionEvent e) {
            sendCoordinates(x, y); // 좌표 전송
        }
    }

    public static void main(String[] args) {
        new SubmarineClient(); // 클라이언트 실행
    }
}
