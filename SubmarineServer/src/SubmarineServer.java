import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;

public class SubmarineServer {
    public static int inPort = 9999; // 서버가 수신할 포트 번호
    public static Vector<Client> clients = new Vector<Client>(); // 연결된 클라이언트 목록
    public static int maxPlayer = 2; // 최대 플레이어 수
    public static int numPlayer = 0; // 현재 접속한 플레이어 수
    private int currentPlayerIndex = 0; // 현재 플레이어 인덱스를 저장
    public static int width = 10; // 맵의 너비
    public static int num_trs = 10; // 맵에 배치될 보물의 수
    public static int num_mine = 3; // 각 플레이어가 배치할 지뢰의 수
    public static Map map; // 게임 맵 객체
    public static String selectedDifficulty = "Beginner"; // 기본 난이도

    public static java.util.Map<String, String> abilities = new HashMap<>(); // 능력 개수 풀
    public static java.util.Map<Client, List<String>> playerAbilities = new HashMap<>(); // 플레이어가 선택한 능력 저장

    public static void main(String[] args) throws Exception {
        new SubmarineServer().createServer(); // 서버 생성 및 실행
    }

    // 서버를 생성하고 클라이언트 연결을 처리하는 메서드
    public void createServer() throws Exception {
        System.out.println("Server start running ..");
        ServerSocket server = new ServerSocket(inPort); // 서버 소켓 생성

        numPlayer = 0;
        // 최대 플레이어 수만큼 클라이언트의 연결을 기다림
        while (numPlayer < maxPlayer) {
            Socket socket = server.accept(); // 클라이언트의 연결을 수락
            Client c = new Client(socket); // 클라이언트 객체 생성
            clients.add(c); // 클라이언트 리스트에 추가
            numPlayer++; // 현재 접속한 플레이어 수 증가
            // 웰컴 메시지 전송
            sendtoall(c.userName + " joined");
            c.send("welcome! " + c.userName);
            System.out.println("\n" + numPlayer + "/" + maxPlayer + " players join");
        }

        System.out.println("\n" + numPlayer + " players join");
        // 각 클라이언트의 턴을 설정하고 사용자 이름 출력
        for (Client c : clients) {
            c.turn = true;
            System.out.println("  - " + c.userName);
        }

        // 난이도 선택
        // 플레이어 1(방장), 플레이어 2에게 각각 메시지 전송
        clients.get(0).send("당신은 방장입니다. 잠시 후 게임 난이도를 선택해주세요");
        clients.get(1).send("방장이 게임 난이도를 설정하고 있습니다..");

        // 플레이어 1의 난이도 선택 과정 진행
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (selectedDifficulty.equals("Beginner")) {
                    clients.get(0).send("20초가 지났습니다. 기본 난이도 Beginner로 설정됩니다.");
                }
            }
        }, 20000); // 20초 후 실행

        // 능력 선택 메시지 전송
        for (Client c : clients) {
            c.send("능력을 선택해주세요.");
        }

        map = new Map(width, num_trs); // 맵 객체 생성

        // 각 클라이언트로부터 받은 지뢰를 맵에 배치
        for (Client c : clients) {
            for (int i = 0; i < num_mine; i++) {
                int x = Integer.parseInt(c.mines[i][0]);
                int y = Integer.parseInt(c.mines[i][1]);
                map.deployMine(x, y, c.userName.substring(0, 1));
            }
        }

        map.numbering(); // 맵 번호 지정
        map.printMap(map.mineMap); // 맵 출력

        sendtoall("Start Game"); // 모든 클라이언트에게 게임 시작 메시지 전송
        clients.get(currentPlayerIndex).turn = true;
        sendTurnMessage();

        // 게임 진행 루프
        while (true) {
            if (allTurn()) { // 모든 플레이어의 턴이 끝났는지 확인
                System.out.println();

                for (Client c : clients) {
                    if (!c.alive) { // 플레이어가 생존해 있는지 확인
                        continue;
                    }

                    int check = map.checkMine(c.x, c.y); // 플레이어 위치의 지뢰 체크
                    if (check >= 0) {
                        System.out.println(c.userName + " hit at (" + c.x + " , " + c.y + ")");
                        map.updateMap(c.x, c.y); // 맵 업데이트
                        c.hp -= 1; // 플레이어 HP 감소
                        sendtoall(c.userName + " HP: " + c.hp); // HP 정보 전송
                        if (c.hp <= 0) { // 플레이어가 사망했는지 확인
                            c.alive = false; // 사망 처리
                            sendtoall(c.userName + " has died.");
                            System.out.println(c.userName + " has died.");
                            sendtoall("Game Over");
                            System.out.println("Game Over");
                            return; // 게임 종료
                        }
                    } else {
                        System.out.println(c.userName + " miss at (" + c.x + " , " + c.y + ")");
                    }

                    c.send("" + check); // 클라이언트에게 결과 전송
                    c.turn = true; // 클라이언트 턴 설정
                }
            }
        }
    }

    // 모든 클라이언트에게 메시지를 전송하는 메서드
    public void sendtoall(String msg) {
        for (Client c : clients) {
            c.send(msg);
        }
    }

    // 선택된 난이도를 설정하고 모든 클라이언트에게 알리는 메서드
    public void setDifficulty(String difficulty) {
        selectedDifficulty = difficulty;
        sendtoall("난이도 선택이 완료되었습니다. 선택된 난이도는 " + selectedDifficulty + "입니다.");
    }

    public void sendTurnMessage() {
        Client currentPlayer = clients.get(currentPlayerIndex);
        sendtoall("[관리자] : "+ currentPlayer.userName + "의 차례입니다.");
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
        System.out.println(client.userName + " chose ability: " + ability);
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

    // 모든 클라이언트가 턴을 마쳤는지 확인하는 메서드
    public boolean allTurn() {
        int i = 0;
        for (Client c : clients) {
            if (!c.turn) {
                i++;
            }
        }
        return i == clients.size();
    }

    // 클라이언트 클래스
    class Client extends Thread {
        Socket socket;
        PrintWriter out = null;
        BufferedReader in = null;
        String userName = null;
        String[][] mines = null; // 클라이언트가 설정한 지뢰 위치
        int x, y; // 현재 클라이언트 위치
        int hp = 3; // 기본 HP 설정
        boolean alive = true; // 클라이언트 생존 여부
        public boolean turn = false; // 클라이언트 턴 여부

        public Client(Socket socket) throws Exception {
            initial(socket); // 초기 설정
            start(); // 쓰레드 시작
        }

        // 클라이언트 초기 설정 메서드
        public void initial(Socket socket) throws IOException, ClassNotFoundException {
            this.socket = socket;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            ObjectInputStream objectInput = new ObjectInputStream(socket.getInputStream());

            userName = (String) objectInput.readObject(); // 클라이언트의 사용자 이름 수신
            mines = (String[][]) objectInput.readObject(); // 클라이언트의 지뢰 위치 수신
            System.out.println(userName + " joins from " + socket.getInetAddress());
            System.out.println("수신한 지뢰 배열: ");
            for (int i = 0; i < mines.length; i++) {
                for (int j = 0; j < mines[i].length; j++) {
                    System.out.print(mines[i][j] + " ");
                }
                System.out.println();
            }
            send("wait for other player.."); // 대기 메시지 전송
        }

        @Override
        public void run() {
            String msg;
            try {
                while (true) {
                    msg = in.readLine(); // 클라이언트로부터 메시지 수신
                    if (msg.startsWith("MOVE:")) { // 이동 명령 처리
                        String[] parts = msg.substring(5).split(",");
                        x = Integer.parseInt(parts[0]);
                        y = Integer.parseInt(parts[1]);

                        int check = map.checkMine(x, y); // 지뢰 체크
                        String value;
                        if (check == 99) {
                            value = "99"; // 보물
                            updateHP(userName, 1); // 보물 찾으면 HP 증가
                        } else if (check == 98) {
                            value = "98"; // 지뢰
                            updateHP(userName, -1); // 지뢰 밟으면 HP 감소
                        } else {
                            value = String.valueOf(check);
                        }
                        map.updateMap(x, y); // 맵 업데이트
                        currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
                        sendTurnMessage();
                        sendtoall("UPDATE:" + x + "," + y + "," + value); // 업데이트 정보 전송
                    } else if (msg.startsWith("DIFFICULTY:")) { // 난이도 선택 메시지 처리
                        String difficulty = msg.substring(11); // 선택한 난이도를 추출
                        setDifficulty(difficulty); // 난이도 설정
                    } else if (msg.startsWith("ABILITY:")) { // 능력 선택 메시지 처리
                        String ability = msg.substring(8); // 선택한 능력 추출
                        savePlayerAbility(this, ability); // 클라이언트의 능력 저장
                        if (allPlayersChoseAbility()) { // 모든 플레이어가 능력을 선택했는지 확인
                            sendtoall("능력 선택이 완료되었습니다."); // 능력 선택 완료 메시지 전송
                        }
                    } else if (msg.startsWith("HEALING:")) {
                    	updateHP(userName, 1);
                    } else if (msg.startsWith("STEAL")) {
                    	if (turn == true) {
                    		turn = false;
                            currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
                            sendTurnMessage();
                    	} 
                    } else {
                        if (turn && alive) { // 턴과 생존 여부 확인
                            try {
                                String[] arr = msg.split(",");
                                if (arr.length == 2 && isNumeric(arr[0]) && isNumeric(arr[1])) { // 유효한 좌표인지 확인
                                    x = Integer.parseInt(arr[0]);
                                    y = Integer.parseInt(arr[1]);
                                    send("ok"); // 유효한 좌표 응답
                                    turn = false; // 턴 종료
                                } else {
                                    send("Invalid input. Please enter valid coordinates."); // 유효하지 않은 입력 응답
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid input: " + msg);
                                send("Invalid input. Please enter valid coordinates."); // 유효하지 않은 입력 응답
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 플레이어의 HP를 업데이트하는 메서드
        private void updateHP(String userName, int delta) {
            for (Client c : clients) {
                if (c.userName.equals(userName)) {
                    c.hp += delta;
                    sendtoall(c.userName + " HP: " + c.hp); // HP 정보 전송
                    if (c.hp <= 0) { // 플레이어가 사망했는지 확인
                        c.alive = false;
                        sendtoall(c.userName + " has died."); // 사망 정보 전송
                        sendtoall("Game Over"); // 게임 종료 정보 전송
                        System.out.println("Game Over");
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
    }
}
