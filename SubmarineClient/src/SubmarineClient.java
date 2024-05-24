import java.io.*;
import java.net.*;
import java.util.*;

class SubmarineClient {
    static int inPort = 9999;
    static String address = "192.168.0.6";
    static String userName = "Alice";
    static int num_trs = 10;
    static int num_mine = 3;

    static String[][] mines;
    static int width = 9;

    public static void main(String[] args) {
        int score = 0;
        String msg;
        Scanner sc = new Scanner(System.in);

        System.out.print("Please enter IP: ");
        address = sc.nextLine(); // IP 주소 입력

        System.out.print("Please enter UserName: ");
        userName = sc.nextLine(); // username 입력

        mines = new String[num_mine][2];
        System.out.println("Let's plant a Mine!");
        System.out.println("ex) \"2,3\" without space only comma");
        for (int i = 0; i < num_mine; i++) { // 지뢰 매설
            String[] temp = sc.nextLine().split(",");
            for (int j = 0; j < 2; j++)
                mines[i][j] = temp[j];
        }

        System.out.println("입력완료");

        try (Socket socket = new Socket(address, inPort)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Welcome!");
            out.writeObject(userName);
            out.writeObject(mines); // 2D 배열 전송
            out.flush();

            msg = in.readLine(); // wait message
            System.out.println(msg);
            msg = in.readLine(); // start message
            System.out.println(msg);

            while (score <= num_trs) {
                msg = guess(in, out);

                if (msg.equalsIgnoreCase("ok")) {
                    msg = in.readLine();
                    int result = Integer.parseInt(msg);
                    if (result >= 0) {
                        score++;
                        System.out.println("hit , score = " + score);
                    } else
                        System.out.println("miss , score = " + score);
                }
            }
            in.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String guess(BufferedReader in, ObjectOutputStream out) throws IOException {
        Scanner scan = new Scanner(System.in);

        System.out.print("\nEnter x coordinate: ");
        int x = scan.nextInt();
        while ((x < 0) || (x >= width)) {
            System.out.println("Invalid x, enter a new x coordinate");
            x = scan.nextInt();
        }
        System.out.print("Enter y coordinate: ");
        int y = scan.nextInt();
        while ((y < 0) || (y >= width)) {
            System.out.println("Invalid y, enter a new y coordinate");
            y = scan.nextInt();
        }

        System.out.println("wait for turn");
        out.writeObject(new int[]{x, y}); // 좌표 전송
        out.flush();
        String msg = in.readLine();

        return msg;
    }
}
