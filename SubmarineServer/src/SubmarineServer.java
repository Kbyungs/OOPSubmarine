import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class SubmarineServer extends JFrame {
    public static int inPort = 9999;
    public static Vector<Client> clients = new Vector<>();
    public static int maxPlayer = 2;
    public static int numPlayer = 0;
    private int currentPlayerIndex = 0;
    public static int width;
    public static int num_trs;
    public static int num_mine;
    public static Map map;
    public static String selectedDifficulty = null;

    public static java.util.Map<String, String> abilities = new HashMap<>();
    public static java.util.Map<Client, java.util.List<String>> playerAbilities = new HashMap<>();

    private JTextArea logArea;
    private JScrollPane logScrollPane;
    private JLabel player1Label;
    private JLabel player2Label;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            try {
                new SubmarineServer().createServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public SubmarineServer() {
        setTitle("Submarine Server");
        setSize(600, 400);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); // x 버튼을 눌렀을 때 종료되도록 설정
        setLayout(new BorderLayout());

        // 상단 패널에 플레이어 정보를 표시하는 레이블 추가
        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        player1Label = new JLabel("Player 1: ");
        player2Label = new JLabel("Player 2: ");
        topPanel.add(player1Label);
        topPanel.add(player2Label);
        add(topPanel, BorderLayout.NORTH);

        // 게임 이벤트를 로그로 표시하는 텍스트 영역 추가
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(logScrollPane, BorderLayout.CENTER);

        setVisible(true);
    }

    public void createServer() throws Exception {
        appendLog("서버 시작 중...");
        ServerSocket server = new ServerSocket(inPort);

        numPlayer = 0;
        while (numPlayer < maxPlayer) {
            try {
                appendLog("클라이언트 연결 대기 중...");
                Socket socket = server.accept();
                appendLog(socket.getInetAddress() + "에서 클라이언트 연결됨");

                try {
                    Client c = new Client(socket);
                    clients.add(c);
                    numPlayer++;
                    sendtoall(c.userName + " joined");
                    c.send("welcome! " + c.userName);
                    appendLog(numPlayer + "/" + maxPlayer + " players join");
                    updatePlayerLabels();
                } catch (IOException | ClassNotFoundException e) {
                    appendLog("클라이언트 초기화 중 오류 발생: " + e.getMessage());
                    e.printStackTrace();
                    socket.close();
                }
            } catch (IOException e) {
                appendLog("클라이언트 연결 실패: " + e.getMessage());
            }
        }

        appendLog(numPlayer + " players join");
        for (Client c : clients) {
            c.turn = true;
            appendLog("  - " + c.userName);
        }

        clients.get(0).send("you are the host");
        clients.get(1).send("waiting for the host to select difficulty..");

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                if (selectedDifficulty == null) {
                    setDifficulty("Beginner");
                    sendSettingsToClients();
                    promptMinesPlacement();
                }
            }
        }, 20000);

        synchronized (this) {
            while (selectedDifficulty == null) {
                wait();
            }
        }

        for (Client c : clients) {
            c.send("능력을 선택해주세요.");
        }

        synchronized (this) {
            while (!allPlayersChoseAbility()) {
                wait();
            }
        }

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

        map.numbering();
        map.printMap(map.mineMap);

        sendtoall("Start Game");
        appendLog("Game has started.");
        clients.get(currentPlayerIndex).turn = true;
        sendTurnMessage();

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
                        currentPlayer.wait();
                    }
                }

                int x = currentPlayer.x;
                int y = currentPlayer.y;

                int check = map.checkMine(x, y);
                map.printMap(map.mineMap);

                if (check == 99) {
                    sendtoall(currentPlayer.userName + " 보물 발견!");
                    if (currentPlayer.hp >= 3) {
                        sendtoall(currentPlayer.userName + " 의 체력이 최대입니다");
                    } else {
                        currentPlayer.hp += 1;
                    }
                } else if (check == 98) {
                    if (!map.mineMap[x][y].equals(currentPlayer.userName.substring(0, 1))) {
                        sendtoall(currentPlayer.userName + " 는 상대방이 숨겨둔 지뢰를 밟았습니다");
                        currentPlayer.hp -= 1;
                    } else {
                        sendtoall(currentPlayer.userName + " 는 본인이 숨긴 지뢰를 밟았습니다");
                    }
                }
                updatePlayerLabels();
                if (currentPlayer.hp <= 0) {
                    currentPlayer.alive = false;
                    sendtoall(currentPlayer.userName + " has died.");
                    appendLog(currentPlayer.userName + " has died.");
                    sendtoall("Game Over");
                    appendLog("Game Over");
                    return;
                }

                sendUpdateToAllClients(x, y, check);

                currentPlayer.turn = false;
                currentPlayer.x = -1;
                currentPlayer.y = -1;
                currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
                clients.get(currentPlayerIndex).turn = true;
                sendTurnMessage();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendUpdateToAllClients(int x, int y, int value) {
        String message = "UPDATE:" + x + "," + y + "," + value;
        sendtoall(message);
    }

    public void setDifficulty(String difficulty) {
        selectedDifficulty = difficulty;
        sendtoall("난이도 선택이 완료되었습니다. 선택된 난이도는 " + selectedDifficulty + "입니다.");
        synchronized (this) {
            notifyAll();
        }
    }

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
        map = new Map(width, num_trs);
    }

    private void sendSettingsToClients() {
        String settings = "SETTINGS:" + width + "," + num_mine;
        for (Client c : clients) {
            c.send(settings);
        }
    }

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

    private boolean allMinesReceived() {
        for (Client c : clients) {
            if (c.mines == null || c.mines.length != num_mine) {
                return false;
            }
        }
        return true;
    }

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

    public boolean allPlayersChoseAbility() {
        for (Client client : clients) {
            if (!playerAbilities.containsKey(client) || playerAbilities.get(client).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public class Client extends Thread {
        Socket socket;
        PrintWriter out = null;
        BufferedReader in = null;
        ObjectOutputStream objectOutput = null;
        ObjectInputStream objectInput = null;
        String userName = null;
        String[][] mines = null;
        int x = -1, y = -1;
        int hp = 3;
        boolean alive = true;
        boolean firstTime = true;
        public boolean turn = false;

        public Client(Socket socket) throws IOException, ClassNotFoundException {
            this.socket = socket;
            try {
                objectOutput = new ObjectOutputStream(socket.getOutputStream());
                objectOutput.flush();
                objectInput = new ObjectInputStream(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                userName = (String) objectInput.readObject();
                appendLog("Received username: " + userName);
                appendLog(userName + " joins from " + socket.getInetAddress());
                send("wait for other player..");
            } catch (IOException | ClassNotFoundException e) {
                appendLog("Error during client initialization: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }

            start();
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
                    appendLog("Received from " + userName + ": " + msg);
                    if (msg.startsWith("MOVE:")) {
                        String[] parts = msg.substring(5).split(",");
                        if (parts.length == 2 && isNumeric(parts[0]) && isNumeric(parts[1])) {
                            x = Integer.parseInt(parts[0]);
                            y = Integer.parseInt(parts[1]);
                            synchronized (this) {
                                this.notifyAll();
                            }
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
                            appendLog("둘다 지뢰 보냈음");
                            synchronized (SubmarineServer.this) {
                                SubmarineServer.this.notifyAll();
                            }
                        }
                    } else if (msg.startsWith("HEALING:")) {
                        updateHP(userName, 1);
                    } else if (msg.startsWith("STEAL:")) {
                        if (turn == true) {
                            turn = false;
                            currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
                            sendTurnMessage();
                        }
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
                                        this.notifyAll();
                                    }
                                }
                            } catch (NumberFormatException e) {
                                appendLog("Invalid input: " + msg);
                            }
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                appendLog("Error during client communication: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void updateHP(String userName, int delta) {
            for (Client c : clients) {
                if (c.userName.equals(userName)) {
                    c.hp += delta;
                    sendtoall(c.userName + " HP: " + c.hp);
                    updatePlayerLabels();
                    if (c.hp <= 0) {
                        c.alive = false;
                        sendtoall(c.userName + " has died.");
                        sendtoall("Game Over");
                        appendLog("Game Over");
                        break;
                    }
                }
            }
        }

        public void send(String msg) {
            out.println(msg);
        }

        private boolean isNumeric(String str) {
            return str != null && str.matches("\\d+");
        }
    }

    // 로그를 추가하는 메소드
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // 플레이어 정보를 업데이트하는 메소드
    private void updatePlayerLabels() {
        SwingUtilities.invokeLater(() -> {
            if (clients.size() > 0) {
                Client player1 = clients.get(0);
                player1Label.setText(player1.userName + ": " + getHeartString(player1.hp));
            }
            if (clients.size() > 1) {
                Client player2 = clients.get(1);
                player2Label.setText(player2.userName + ": " + getHeartString(player2.hp));
            }
        });
    }

    // 하트 모양으로 HP를 표시하는 메소드
    private String getHeartString(int hp) {
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < hp; i++) {
            hearts.append("❤️");
        }
        return hearts.toString();
    }
}
