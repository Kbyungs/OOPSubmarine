import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

public class SubmarineClient extends JFrame {
    private JTextArea textArea; // 서버 메시지를 표시할 텍스트 영역
    private JButton abilityUseButton; // 능력 사용 버튼
    private JButton[][] buttons; // 게임 버튼 배열
    private Socket socket; // 서버와의 소켓 연결
    private PrintWriter out; // 서버로의 출력 스트림
    private BufferedReader in; // 서버로부터의 입력 스트림
    private ObjectOutputStream objectOut; // 객체 전송용 출력 스트림
    private Map map;

    private String userName; // 사용자 이름
    private String ip; // 서버 IP 주소
    private String[][] mines; // 지뢰 위치 배열
    private static final int num_mine = 3; // 지뢰 수
    private static final int width = 9; // 맵 너비
    private Timer timer; // 선택 시간 타이머
    private Timer aTimer; // 능력 선택 시간 타이머
    private int abilityUseCount = 1;
    private String abilitySave;
    
    private boolean myTurn = false;

    public SubmarineClient() {
        // GUI 구성 요소 초기화
        textArea = new JTextArea();
        textArea.setEditable(false); // 텍스트 영역은 수정 불가
        buttons = new JButton[width][width];
        abilityUseButton = new JButton("능력 발동");
        abilityUseButton.setPreferredSize(new Dimension(100, 600));
        
        abilityUseButton.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
                if (abilityUseCount != 0) {
                	abilityUseCount--;
                    System.out.println("능력 발동!");
                    performAbility();
                    out.println("USE_ABILITY:" + userName); // 서버로 능력 사용 메시지 전송
                } else {
                    System.out.println("능력을 모두 사용했습니다.");
                }
            }
        });	

        
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
        add(abilityUseButton, BorderLayout.EAST); // 채팅창 옆에 능력 사용 버튼 추가

        // 창 설정
        setTitle("MZ뢰찾기");
        setSize(600, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
        
        // 능력 선택
        

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
                            } else if (message.equals("your turn")) {
                                myTurn = true;
                                enableAllButtons();
                            } else if (message.equals("not your turn")) {
                                myTurn = false;
                                disableAllButtons();
                            } else {
                                textArea.append(message + "\n"); // 일반 메시지 텍스트 영역에 추가
                                if (message.equals("Game Over") || message.contains("has died")) {
                                    disableAllButtons(); // 게임 종료 시 버튼 비활성화
                                    break; // 게임 종료 시 루프 탈출
                                }
                                if (message.contains("당신은 방장입니다")) {
                                    showDifficultySelection(); // 방장에게 난이도 선택 창 표시
                                } else if (message.contains("능력을 선택해주세요")) { // 능력 선택 메시지 수신 시
                                    showAbilitySelection(); // 능력 선택 창 표시
                                }
                            }
                        }
                    }
                    catch (IOException e) {
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
    
    // 능력 선택창을 표시하는 메서드
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
        abilities.put("3", "거인");
        abilities.put("4", "폭발");
        return abilities;
    }
    
    public void performAbility() {
        switch (abilitySave) {
            case "1":
            	showExploreGUI();
                break;
            case "2":
                break;
            case "3":
                break;
            case "4":
                break;
            default:
                System.out.println(userName + " 님은 능력이 없습니다.");
                break;
        }
    }
    
    public void showExploreGUI() {
    	String input = JOptionPane.showInputDialog(this, "탐색을 원하는 좌표를 입력하세요" + ":");
        String[] temp = input.split(",");
        int x = Integer.parseInt(temp[0].trim());
        int y = Integer.parseInt(temp[1].trim());
        explore(x, y);
        temp[0] = null;
        temp[1] = null;
    }
    
    public void explore(int x, int y) {
    	boolean f = false;
    	for (int i = 0; i < num_mine; i++) {
    		if (Integer.parseInt(mines[i][0]) == x && Integer.parseInt(mines[i][1]) == y) {
    			f = true;
    		}
        }
    	
    	if (f) { 
            textArea.append("해당 위치는 지뢰가 있습니다\n");
            buttons[x][y].setBackground(Color.RED);
        } else {
            textArea.append("해당 위치는 지뢰가 없습니다\n");
        }    
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
        if (myTurn) out.println("MOVE:" + x + "," + y);
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
                buttons[i][j].setEnabled(true);
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
