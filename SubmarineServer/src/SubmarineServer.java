// 서버 파일

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List; // 명확히 java.util.List를 임포트

public class SubmarineServer extends JFrame {
    public static int inPort = 9999; // 서버가 수신할 포트 번호
    public static Vector<Client> clients = new Vector<Client>(); // 연결된 클라이언트 목록
    public static int maxPlayer = 2; // 최대 플레이어 수
    public static int numPlayer = 0; // 현재 접속한 플레이어 수
    private int currentPlayerIndex = 0; // 현재 플레이어 인덱스를 저장
    public static int width; // 맵의 너비
    public static int num_trs; // 맵에 배치될 보물의 수
    public static int num_mine; // 각 플레이어가 배치할 지뢰의 수
    public static Map map; // 게임 맵 객체
    public static String selectedDifficulty = null; // 선택된 난이도

    public static java.util.Map<String, String> abilities = new HashMap<>(); // 능력 개수 풀
    public static java.util.Map<Client, List<String>> playerAbilities = new HashMap<>(); // 플레이어가 선택한 능력 저장

    private JTextArea player1LogArea;
    private JTextArea player2LogArea;
    private JLabel player1HpLabel;
    private JLabel player2HpLabel;

    public static void main(String[] args) throws Exception {
        new SubmarineServer().createServer(); // 서버 생성 및 실행
    }

    public SubmarineServer() {
        // GUI 초기화
        setTitle("Submarine Server");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 레이아웃 설정
        setLayout(new BorderLayout());

        // 상단 패널
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new GridLayout(1, 2));

        player1HpLabel = new JLabel("Player 1: ❤️ ❤️ ❤️");
        player2HpLabel = new JLabel("Player 2: ❤️ ❤️ ❤️");
        topPanel.add(player1HpLabel);
        topPanel.add(player2HpLabel);

        // 로그 영역
        JPanel logPanel = new JPanel();
        logPanel.setLayout(new GridLayout(1, 2));

        player1LogArea = new JTextArea();
        player1LogArea.setEditable(false);
        JScrollPane player1ScrollPane = new JScrollPane(player1LogArea);
        logPanel.add(player1ScrollPane);

        player2LogArea = new JTextArea();
        player2LogArea.setEditable(false);
        JScrollPane player2ScrollPane = new JScrollPane(player2LogArea);
        logPanel.add(player2ScrollPane);

        add(topPanel, BorderLayout.NORTH);
        add(logPanel, BorderLayout.CENTER);

        setVisible(true);
    }

    // 서버를 생성하고 클라이언트 연결을 처리하는 메서드
    public void createServer() throws Exception {
        appendLog("Server start running ..");
        ServerSocket server = new ServerSocket(inPort); // 서버 소켓 생성

        numPlayer = 0;
        // 최대 플레이어 수만큼 클라이언트의 연결을 기다림
        while (numPlayer < maxPlayer) {
            try {
                appendLog("Waiting for a client to connect...");
                Socket socket = server.accept(); // 클라이언트의 연결을 수락
                appendLog("Client connected from " + socket.getInetAddress());

                try {
                    Client c = new Client(socket); // 클라이언트 객체 생성
                    clients.add(c); // 클라이언트 리스트에 추가
                    numPlayer++; // 현재 접속한 플레이어 수 증가
                    // 웰컴 메시지 전송
                    sendtoall(c.userName + " joined");
                    c.send("welcome! " + c.userName);
                    appendLog("\n" + numPlayer + "/" + maxPlayer + " players join");
                } catch (IOException | ClassNotFoundException e) {
                    appendLog("Error during client initialization: ");
                    e.printStackTrace();  // 스택 트레이스 출력
                    socket.close();
                }
            } catch (IOException e) {
                appendLog("Failed to connect to client: " + e.getMessage());
            }
        }

        appendLog("\n" + numPlayer + " players join");
        // 각 클라이언트의 턴을 설정하고 사용자 이름 출력
        for (Client c : clients) {
            c.turn = true;
            appendLog("  - " + c.userName);
        }

        // 난이도 선택
        clients.get(0).send("you are the host"); // 방장에게 난이도 선택 메시지 전송
        clients.get(1).send("waiting for the host to select difficulty..");

        player1HpLabel.setText(clients.get(0).userName + " : ❤️ ❤️ ❤️");
        player2HpLabel.setText(clients.get(1).userName + " : ❤️ ❤️ ❤️");

        // 플레이어 1의 난이도 선택 과정 진행
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                if (selectedDifficulty == null) {
                    setDifficulty("Beginner");
                    sendSettingsToClients();
                    promptMinesPlacement();
                }
            }
        }, 20000); // 20초 후 실행

        // 서버가 난이도를 설정한 후 클라이언트에게 전송
        synchronized (this) {
            while (selectedDifficulty == null) {
                wait();
            }
        }

        // 능력 선택 메시지 전송
        for (Client c : clients) {
            c.send("능력을 선택해주세요.");
        }

        // 능력 선택 완료 대기
        synchronized (this) {
            while (!allPlayersChoseAbility()) {
                wait();
            }
        }

        // 각 클라이언트로부터 받은 지뢰를 맵에 배치
        synchronized (this) {
            while (!allMinesReceived()) {
                wait();
            }
        }

        for (Client c : clients) {
            for (int i = 0; i < num_mine; i++) {
                int x = Integer.parseInt(c.mines[i][0]);
                int y = Integer.parseInt(c.mines[i][1]);
                map.deployMine(x, y, c.userName.substring(0, 1));
            }
        }
        map.deployTrs();

        map.numbering(); // 맵 번호 지정
        map.printMap(map.mineMap); // 맵 출력

        sendtoall("Start Game"); // 모든 클라이언트에게 게임 시작 메시지 전송
        appendLog("Game has started.");
        clients.get(currentPlayerIndex).turn = true;
        sendTurnMessage();

        // 게임 진행 루프
        while (true) {
            try {
                synchronized (this) {
                    while (!clients.get(currentPlayerIndex).turn) {
                        wait();
                    }
                }
                Client currentPlayer = clients.get(currentPlayerIndex);

                synchronized (currentPlayer) {
                    while (currentPlayer.x == -1 || currentPlayer.y == -1) {
                        currentPlayer.wait(); // x, y 값이 업데이트 될 때까지 대기
                    }
                }

                int x = currentPlayer.x;
                int y = currentPlayer.y;

                int check = map.checkMine(x, y); // 지뢰 체크

                map.printMap(map.mineMap);

                String result = "";
                if (check == 99) {
                    result = currentPlayer.userName + " 보물 발견!";
                    currentPlayer.treasuresFound += 1;
                    if (currentPlayer.hp >= 3) {
                        sendtoall(currentPlayer.userName + "의 체력이 최대입니다");
                    } else {
                        currentPlayer.hp += 1;
                    }
                } else if (check == 98) {
                    if (!map.mineMap[x][y].equals(currentPlayer.userName.substring(0, 1))) {
                        result = currentPlayer.userName + " 는 상대방이 숨겨둔 지뢰를 밟았습니다";
                        currentPlayer.hp -= 1;
                        currentPlayer.minesHit += 1;
                    } else {
                        result = currentPlayer.userName + " 는 본인이 숨긴 지뢰를 밟았습니다";
                    }
                } else {
                    result = currentPlayer.userName + "는 아무것도 찾지 못했습니다.";
                }

                sendtoall(result); // 결과 정보 전송
                appendLog(result); // 로그에 결과 추가
                sendHpUpdateToClients(); // HP 정보 전송
                if (currentPlayer.hp <= 0) { // 플레이어가 사망했는지 확인
                    currentPlayer.alive = false; // 사망 처리
                    sendtoall(currentPlayer.userName + " has died.");
                    appendLog(currentPlayer.userName + " has died.");
                    determineGameOutcome();
                    return; // 게임 종료
                }

                if (allTreasuresFound()) { // 모든 보물이 발견되었는지 확인
                    determineGameOutcome();
                    return; // 게임 종료
                }

                sendUpdateToAllClients(x, y, check); // 모든 클라이언트에게 업데이트된 좌표와 값을 전송

                updateHpDisplay(); // HP 표시 업데이트

                currentPlayer.turn = false; // 현재 플레이어의 턴 종료
                currentPlayer.x = -1; // x 값을 초기화
                currentPlayer.y = -1; // y 값을 초기화
                currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
                clients.get(currentPlayerIndex).turn = true;
                sendTurnMessage();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 모든 클라이언트에게 업데이트된 좌표와 값을 전송하는 메서드
    private void sendUpdateToAllClients(int x, int y, int value) {
        String message = "UPDATE:" + x + "," + y + "," + value;
        sendtoall(message);
    }

    // 선택된 난이도를 설정하고 모든 클라이언트에게 알리는 메서드
    public void setDifficulty(String difficulty) {
        selectedDifficulty = difficulty;
        sendtoall("난이도 선택이 완료되었습니다. 선택된 난이도는 " + selectedDifficulty + "입니다.");
        synchronized (this) {
            notifyAll();
        }
    }

    // 게임 설정 값을 설정하는 메서드
    private void setGameSettings(String difficulty) {
        if (difficulty.equals("Beginner")) {
            width = 5;
            num_mine = 3;
            num_trs = 5;
        } else if (difficulty.equals("Intermediate")) {
            width = 7;
            num_mine = 5;
            num_trs = 7;
        } else {
            width = 9;
            num_mine = 8;
            num_trs = 10;
        }
        map = new Map(width, num_trs); // SubmarineMap으로 수정
    }

    // 클라이언트들에게 설정 값을 전송하는 메서드
    private void sendSettingsToClients() {
        String settings = "SETTINGS:" + width + "," + num_mine;
        for (Client c : clients) {
            c.send(settings);
        }
    }

    // 지뢰 위치 입력을 클라이언트들에게 요청하는 메서드
    private void promptMinesPlacement() {
        for (Client c : clients) {
            c.send("Please set your mine positions.");
        }

        synchronized (this) {
            while (!allMinesReceived()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 모든 클라이언트로부터 지뢰 위치를 받았는지 확인하는 메서드
    private boolean allMinesReceived() {
        for (Client c : clients) {
            if (c.mines == null || c.mines.length != num_mine) {
                return false;
            }
        }
        return true;
    }

    // 모든 클라이언트에게 메시지를 전송하는 메서드
    public void sendtoall(String msg) {
        for (Client c : clients) {
            c.send(msg);
        }
    }

    public void sendTurnMessage() {
        Client currentPlayer = clients.get(currentPlayerIndex);
        sendtoall("[관리자] : " + currentPlayer.userName + "의 차례입니다.");
        currentPlayer.send("your turn");
        for (Client c : clients) {
            if (!c.userName.equals(currentPlayer.userName)) c.send("not your turn");
        }
    }

    // 클라이언트의 능력을 저장하는 메서드
    public void savePlayerAbility(Client client, String ability) {
        if (!playerAbilities.containsKey(client)) {
            playerAbilities.put(client, new ArrayList<>());
        }
        playerAbilities.get(client).add(ability);
        appendLog(client.userName + " chose ability: " + ability);
        synchronized (this) {
            notifyAll();
        }
    }

    // 모든 플레이어가 능력을 선택했는지 확인하는 메서드
    public boolean allPlayersChoseAbility() {
        for (Client client : clients) {
            if (!playerAbilities.containsKey(client) || playerAbilities.get(client).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 두 플레이어의 HP를 클라이언트들에게 전송하는 메서드
    public void sendHpUpdateToClients() {
        String hpMessage = "HP:" + clients.get(0).userName + "," + clients.get(0).hp + "," + clients.get(1).userName + "," + clients.get(1).hp;
        sendtoall(hpMessage);
    }

    // HP 표시를 업데이트하는 메서드
    public void updateHpDisplay() {
        String player1Hp = clients.get(0).userName + " : " + "❤️ ".repeat(Math.max(0, clients.get(0).hp));
        String player2Hp = clients.get(1).userName + " : " + "❤️ ".repeat(Math.max(0, clients.get(1).hp));

        player1HpLabel.setText(player1Hp);
        player2HpLabel.setText(player2Hp);
    }

    // 로그를 추가하는 메서드
    public void appendLog(String message) {
        if (clients.size() > 0 && clients.get(0) != null) {
            player1LogArea.append(message + "\n");
            player1LogArea.setCaretPosition(player1LogArea.getDocument().getLength());
        }
        if (clients.size() > 1 && clients.get(1) != null) {
            player2LogArea.append(message + "\n");
            player2LogArea.setCaretPosition(player2LogArea.getDocument().getLength());
        }
    }

    // 모든 보물이 발견되었는지 확인하는 메서드
    private boolean allTreasuresFound() {
        int totalTreasuresFound = 0;
        for (Client client : clients) {
            totalTreasuresFound += client.treasuresFound;
        }
        return totalTreasuresFound >= num_trs;
    }

    // 게임 결과를 결정하고 전송하는 메서드
    private void determineGameOutcome() {
        String winner = "";
        String loser = "";

        if (clients.get(0).hp <= 0) {
            winner = clients.get(1).userName;
            loser = clients.get(0).userName;
        } else if (clients.get(1).hp <= 0) {
            winner = clients.get(0).userName;
            loser = clients.get(1).userName;
        } else {
            if (clients.get(0).treasuresFound > clients.get(1).treasuresFound) {
                winner = clients.get(0).userName;
                loser = clients.get(1).userName;
            } else if (clients.get(1).treasuresFound > clients.get(0).treasuresFound) {
                winner = clients.get(1).userName;
                loser = clients.get(0).userName;
            } else {
                sendtoall("Game ended in a draw!");
                appendLog("Game ended in a draw!");
                return;
            }
        }

        sendtoall("Winner: " + winner + ", Loser: " + loser);
        appendLog("Winner: " + winner + ", Loser: " + loser);
        sendtoall("Game Over");
        appendLog("Game Over");
    }

    // 클라이언트 클래스
    public class Client extends Thread {
        Socket socket;
        PrintWriter out = null;
        BufferedReader in = null;
        ObjectOutputStream objectOutput = null;
        ObjectInputStream objectInput = null;
        String userName = null;
        String[][] mines = null; // 클라이언트가 설정한 지뢰 위치
        int x = -1, y = -1; // 초기값을 -1로 설정
        int hp = 3; // 기본 HP 설정
        boolean alive = true; // 클라이언트 생존 여부
        boolean firstTime = true;
        public boolean turn = false; // 클라이언트 턴 여부

        int treasuresFound = 0; // 찾은 보물 개수
        int minesHit = 0; // 밟은 지뢰 개수

        public Client(Socket socket) throws IOException, ClassNotFoundException {
            this.socket = socket;
            // 클라이언트 초기 설정
            try {
                objectOutput = new ObjectOutputStream(socket.getOutputStream());
                objectOutput.flush(); // flush() 추가
                objectInput = new ObjectInputStream(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                userName = (String) objectInput.readObject();
                appendLog("Received username: " + userName); // 예외 발생 가능 부분 디버그 추가
                appendLog(userName + " joins from " + socket.getInetAddress());
                send("wait for other player..");
            } catch (IOException | ClassNotFoundException e) {
                appendLog("Error during client initialization: " + e.getMessage());
                e.printStackTrace();  // 스택 트레이스 출력
                throw e;
            }

            start(); // 쓰레드 시작
        }

        @Override
        public void run() {
            String msg;
            try {
                while (true) {
                    msg = in.readLine();
                    if (msg == null) {
                        appendLog(userName + " disconnected.");
                        break;
                    }
                    appendLog("Received from " + userName + ": " + msg); // 디버그 메시지 추가
                    if (msg.startsWith("MOVE:")) {
                        String[] parts = msg.substring(5).split(",");
                        if (parts.length == 2 && isNumeric(parts[0]) && isNumeric(parts[1])) {
                            x = Integer.parseInt(parts[0]);
                            y = Integer.parseInt(parts[1]);
                            synchronized (this) {
                                this.notifyAll(); // 클라이언트가 x, y 값을 업데이트한 후 알림
                            }
                        } else {
                            send("Invalid input. Please enter valid coordinates.");
                        }
                    } else if (msg.startsWith("DIFFICULTY:")) {
                        String difficulty = msg.substring(11);
                        setDifficulty(difficulty);
                        setGameSettings(difficulty);
                        sendSettingsToClients();
                    } else if (msg.startsWith("ABILITY:")) {
                        String ability = msg.substring(8);
                        savePlayerAbility(this, ability);
                        if (allPlayersChoseAbility()) {
                            sendtoall("능력 선택이 완료되었습니다.");
                            synchronized (SubmarineServer.this) {
                                SubmarineServer.this.notifyAll();
                            }
                        }
                    } else if (msg.equals("MINES:SET")) {
                        mines = (String[][]) objectInput.readObject();
                        appendLog(userName + " has set mines: " + Arrays.deepToString(mines));
                        if (allMinesReceived()) {
                            appendLog("모든 플레이어가 지뢰를 설정하였습니다.");
                            map.deployTrs();
                            synchronized (SubmarineServer.this) {
                                SubmarineServer.this.notifyAll();
                            }
                        }
                    } else if (msg.startsWith("HEALING:")) {
                        updateHP(userName, 1);
                    } else if (msg.startsWith("STEAL:")) {
                        if (turn) {
                            turn = false;
                            currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
                            sendTurnMessage();
                        }
                    } else if (msg.equals("REQUEST_STATS")) {
                        sendGameStatistics();
                    } else if (msg.startsWith("MINECHECK:")) {
                        sendMineCheckResult(msg.substring(10));
                    } else if (msg.startsWith("BUTTONBLACK:")) {
                        sendButtonColorBlack(msg.substring(12));
                    } else {
                        if (turn && alive) {
                            try {
                                String[] arr = msg.split(",");
                                if (arr.length == 2 && isNumeric(arr[0]) && isNumeric(arr[1])) {
                                    x = Integer.parseInt(arr[0]);
                                    y = Integer.parseInt(arr[1]);
                                    send("ok");
                                    turn = false;
                                    synchronized (this) {
                                        this.notifyAll(); // 클라이언트가 x, y 값을 업데이트한 후 알림
                                    }
                                } else {
                                    send("Invalid input. Please enter valid coordinates.");
                                }
                            } catch (NumberFormatException e) {
                                appendLog("Invalid input: " + msg);
                                send("Invalid input. Please enter valid coordinates.");
                            }
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                appendLog("Error during client communication: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 플레이어의 HP를 업데이트하는 메서드
        private void updateHP(String userName, int delta) {
            for (Client c : clients) {
                if (c.userName.equals(userName)) {
                    c.hp += delta;
                    sendHpUpdateToClients(); // HP 정보 전송
                    if (c.hp <= 0) { // 플레이어가 사망했는지 확인
                        c.alive = false;
                        sendtoall(c.userName + " has died."); // 사망 정보 전송
                        sendtoall("Game Over"); // 게임 종료 정보 전송
                        appendLog("Game Over");
                        determineGameOutcome();
                        break;
                    }
                }
            }
        }

        // 클라이언트에게 메시지를 전송하는 메서드
        public void send(String msg) {
            out.println(msg);
        }

        // 문자열이 숫자인지 확인하는 메서드
        private boolean isNumeric(String str) {
            return str != null && str.matches("\\d+");
        }

        private void sendMineCheckResult(String msg) {
            boolean f = false;
            String parts[] = msg.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            for (Client c : clients) {
                if (map.mineMap[x][y].equals(c.userName.substring(0, 1))) {
                    f = true;
                    break;
                }
            }

            for (Client c : clients) {
                if (c.userName.equals(userName)) {
                    String result = "MINECHECK:" + x + "," + y + "," + (f ? "1" : "0");
                    c.send("MINECHECK:" + x + "," + y + "," + (f ? "1" : "0"));
                    System.out.println("Sending exploration result to " + c.userName + ": " + result);
                } else {
                    System.out.println("Error");
                }
            }
        }

        private void sendButtonColorBlack(String msg) {
            String parts[] = msg.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            for (Client c : clients) {
                c.send("BUTTONBLACK:" + x + "," + y);
            }
        }

        private void sendGameStatistics() {
            for (Client c : clients) {
                String stats = c.treasuresFound + "," + c.minesHit;
                c.send("STATS:" + stats);
            }
        }
    }
}
