// enjoy submarine detection game
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;


public class SubmarineServer {
    public static int inPort = 9999;
    public static Vector<Client> clients = new Vector<Client>();
    public static int maxPlayer=2;
    public static int numPlayer=0;
    public static int width=10;
    public static int num_mine=10;
    public static Map map;


    public static void main(String[] args) throws Exception {
        new SubmarineServer().createServer();
    }


    public void createServer() throws Exception {
        System.out.println("Server start running ..");
        ServerSocket server = new ServerSocket(inPort);

        numPlayer=0;
        while (numPlayer<maxPlayer) {
            Socket socket = server.accept();
            Client c = new Client(socket);
            clients.add(c);
            numPlayer++;
        }
        System.out.println("\n"+numPlayer+" players join");
        for(Client c:clients) {
            c.turn = true;
            System.out.println("  - "+c.userName);
        }

        map = new Map(width, num_mine);
        sendtoall("Start Game");

        while(true) {
            if (allTurn()) {
                System.out.println();

                for(Client c : clients) {
                    int check=map.checkMine(c.x, c.y);
                    if (check>=0) {
                        System.out.println(c.userName + " hit at (" + c.x+" , "+c.y+")");
                        map.updateMap(c.x, c.y);
                    }
                    else
                        System.out.println(c.userName + " miss at (" + c.x+" , "+c.y+")");

                    c.send(""+check);
                    c.turn=true;
                }

            }
        }

    }


    public void sendtoall(String msg) {
        for(Client c : clients)
            c.send(msg);
    }


    public boolean allTurn() {
        int i=0;
        for(Client c:clients)
            if (c.turn == false)
                i++;

        if (i==clients.size()) return true;
        else return false;
    }


    class Client extends Thread {
        Socket socket;
        PrintWriter out = null;
        BufferedReader in = null;
        Map map;
        String userName = null;
        int x, y;
        public boolean turn=false;


        public Client(Socket socket) throws Exception {
            initial(socket);
            start();
        }


        public void initial(Socket socket) throws IOException {
            this.socket = socket;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            userName = in.readLine();
            System.out.println(userName+" joins from  "+socket.getInetAddress());
            send("Wait for other player..");
        }


        @Override
        public void run() {
            String msg;

            try {
                while(true) {
                    msg = in.readLine();
                    if (turn) {
                        String[] arr = msg.split(",");
                        x = Integer.parseInt(arr[0]);
                        y = Integer.parseInt(arr[1]);
                        send("ok");
                        turn=false;
                    }

                }
            }
            catch (IOException e) { }
        }


        public void send(String msg) {
            out.println(msg);
        }

    }


}
