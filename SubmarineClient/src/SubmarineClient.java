import java.io.*;
import java.net.*;
import java.util.Vector;

public class SubmarineServer {
    public static int inPort = 9999;
    public static Vector<Client> clients = new Vector<Client>();
    public static int maxPlayer = 2;
    public static int numPlayer = 0;
    public static int width = 10;
    public static int num_trs = 10;
    public static int num_mine = 3;
    public static Map map;

    public static void main(String[] args) throws Exception {
        new SubmarineServer().createServer();
    }

    public void createServer() throws Exception {
        System.out.println("Server start running ..");
        ServerSocket server = new ServerSocket(inPort);

        numPlayer = 0;
        while (numPlayer < maxPlayer) {
            Socket socket = server.accept();
            Client c = new Client(socket);
            clients.add(c);
            numPlayer++;
        }
        System.out.println("\n" + numPlayer + " players join");
        for (Client c : clients) {
            c.turn = true;
            System.out.println("  - " + c.userName);
        }

        map = new Map(width, num_trs);

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


        while (true) {
            if (allTurn()) {
                System.out.println();

                for (Client c : clients) {
                    if (!c.alive) {
                        continue;
                    }

                    int check = map.checkMine(c.x, c.y);
                    if (check >= 0) {
                        System.out.println(c.userName + " hit at (" + c.x + " , " + c.y + ")");
                        map.updateMap(c.x, c.y);
                        c.hp -= 1; // HP 감소
                        sendtoall(c.userName + " HP: " + c.hp); // HP 전송
                        if (c.hp <= 0) {
                            c.alive = false; // 클라이언트 사망 처리
                            sendtoall(c.userName + " has died.");
                            System.out.println(c.userName + " has died.");
                            sendtoall("Game Over");
                            System.out.println("Game Over");
                            return; // 게임 종료
                        }
                    } else {
                        System.out.println(c.userName + " miss at (" + c.x + " , " + c.y + ")");
                    }

                    c.send("" + check);
                    c.turn = true;
                }
            }
        }
    }

    public void sendtoall(String msg) {
        for (Client c : clients) {
            c.send(msg);
        }
    }

    public boolean allTurn() {
        int i = 0;
        for (Client c : clients) {
            if (!c.turn) {
                i++;
            }
        }
        return i == clients.size();
    }

    class Client extends Thread {
        Socket socket;
        PrintWriter out = null;
        BufferedReader in = null;
        String userName = null;
        String[][] mines = null;
        int x, y;
        int hp = 3; // 기본 HP 설정
        boolean alive = true; // 클라이언트 생존 여부
        public boolean turn = false;

        public Client(Socket socket) throws Exception {
            initial(socket);
            start();
        }

        public void initial(Socket socket) throws IOException, ClassNotFoundException {
            this.socket = socket;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            ObjectInputStream objectInput = new ObjectInputStream(socket.getInputStream());

            userName = (String) objectInput.readObject();
            mines = (String[][]) objectInput.readObject(); // 2D 배열 수신
            System.out.println(userName + " joins from " + socket.getInetAddress());
            System.out.println("수신한 지뢰 배열: ");
            for (int i = 0; i < mines.length; i++) {
                for (int j = 0; j < mines[i].length; j++) {
                    System.out.print(mines[i][j] + " ");
                }
                System.out.println();
            }
            send("wait for other player..");
        }

        @Override
        public void run() {
            String msg;
            try {
                while (true) {
                    msg = in.readLine();
                    if (msg.startsWith("MOVE:")) {
                        String[] parts = msg.substring(5).split(",");
                        x = Integer.parseInt(parts[0]);
                        y = Integer.parseInt(parts[1]);

                        int check = map.checkMine(x, y);
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
                        map.updateMap(x, y);
                        sendtoall("UPDATE:" + x + "," + y + "," + value);
                    } else {
                        if (turn && alive) {
                            try {
                                String[] arr = msg.split(",");
                                if (arr.length == 2 && isNumeric(arr[0]) && isNumeric(arr[1])) {
                                    x = Integer.parseInt(arr[0]);
                                    y = Integer.parseInt(arr[1]);
                                    send("ok");
                                    turn = false;
                                } else {
                                    send("Invalid input. Please enter valid coordinates.");
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid input: " + msg);
                                send("Invalid input. Please enter valid coordinates.");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void updateHP(String userName, int delta) {
            for (Client c : clients) {
                if (c.userName.equals(userName)) {
                    c.hp += delta;
                    sendtoall(c.userName + " HP: " + c.hp);
                    if (c.hp <= 0) {
                        c.alive = false;
                        sendtoall(c.userName + " has died.");
                        sendtoall("Game Over");
                        System.out.println("Game Over");
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
}
