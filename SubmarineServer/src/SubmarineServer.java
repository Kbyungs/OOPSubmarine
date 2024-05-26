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
                map.displayMap[x][y] = "s";
            }
        }

        map.numbering();
        map.printMap(map.mineMap);

        sendtoall("Start Game");

        while (true) {
            if (allTurn()) {
                System.out.println();

                for (Client c : clients) {
                    int check = map.checkMine(c.x, c.y);
//                    if (check >= 0) {
//                        System.out.println(c.userName + " hit at (" + c.x + " , " + c.y + ")");
//                        map.updateMap(c.x, c.y);
//                    } else
//                        System.out.println(c.userName + " miss at (" + c.x + " , " + c.y + ")");

                    if (check == 99) {  // 보물발견
                        System.out.println(c.userName + " find treasure at (" + c.x + ", " + c.y + ")");
                        if (c.hp < 3) c.hp++;
                        System.out.println(c.userName + "'s hp : " + c.hp);
                    } else if (check == 98) {// 지뢰밟음
                        if (!map.mineMap[c.x][c.y].equals(c.userName.substring(0,1))) { //본인지뢰는 pass
                            System.out.println(c.userName + " hit the bomb at (" + c.x + ", " + c.y + ")");
                            c.hp--;
                        }
                        System.out.println(c.userName + "'s hp : " + c.hp);
                    } else { //아무것도 못찾음
                        System.out.println(c.userName + " miss at (" + c.x + " , " + c.y + ")");
                    }
                    map.updateMap(c.x,c.y);
                    c.send("" + check);
                    c.turn = true;
                }
            }
        }
    }

    public void sendtoall(String msg) {
        for (Client c : clients)
            c.send(msg);
    }

    public boolean allTurn() {
        int i = 0;
        for (Client c : clients)
            if (!c.turn)
                i++;
        return i == clients.size();
    }

    class Client extends Thread {
        Socket socket;
        PrintWriter out = null;
        BufferedReader in = null;
        String userName = null;
        String[][] mines = null;
        int x, y;
        int hp = 3;
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
            System.out.println(userName + " joins from  " + socket.getInetAddress());
            System.out.println("수신한 지뢰 배열: ");
            for (int i = 0; i < mines.length; i++) {
                for (int j = 0; j < mines[i].length; j++) {
                    System.out.print(mines[i][j] + " ");
                }
                System.out.println();
            }
            send("Wait for other player..");
        }

        @Override
        public void run() {
            String msg;

            try {
                while (true) {
                    msg = in.readLine();
                    if (turn) {
                        String[] arr = msg.split(",");
                        x = Integer.parseInt(arr[0]);
                        y = Integer.parseInt(arr[1]);
                        send("ok");
                        turn = false;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void send(String msg) {
            out.println(msg);
        }
    }
}
