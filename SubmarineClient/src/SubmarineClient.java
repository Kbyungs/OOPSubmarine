import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class SubmarineClient extends JFrame {
    private JTextArea textArea;
    private JTextField xCoordField;
    private JTextField yCoordField;
    private JButton sendButton;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectOutputStream objectOut;

    private String userName;
    private String[][] mines;
    private static final int num_mine = 3;
    private static final int width = 9;

    public SubmarineClient() {
        // GUI 구성 요소 초기화
        textArea = new JTextArea();
        textArea.setEditable(false);
        xCoordField = new JTextField(5);
        yCoordField = new JTextField(5);
        sendButton = new JButton("Send");

        // 레이아웃 설정
        JPanel panel = new JPanel();
        panel.add(new JLabel("X:"));
        panel.add(xCoordField);
        panel.add(new JLabel("Y:"));
        panel.add(yCoordField);
        panel.add(sendButton);

        setLayout(new BorderLayout());
        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(panel, BorderLayout.SOUTH);

        // 이벤트 핸들러 설정
        sendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendCoordinates();
            }
        });
        xCoordField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendCoordinates();
            }
        });
        yCoordField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendCoordinates();
            }
        });

        // 창 설정
        setTitle("Submarine Client GUI");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        // 사용자 이름 및 지뢰 입력 받기
        userName = JOptionPane.showInputDialog(this, "Enter your username:");
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

    private void sendCoordinates() {
        try {
            int x = Integer.parseInt(xCoordField.getText());
            int y = Integer.parseInt(yCoordField.getText());
            if ((x < 0) || (x >= width) || (y < 0) || (y >= width)) {
                textArea.append("Invalid coordinates. Try again.\n");
                return;
            }
            out.println(x + "," + y);
            xCoordField.setText("");
            yCoordField.setText("");
        } catch (NumberFormatException e) {
            textArea.append("Invalid input. Please enter valid coordinates.\n");
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 9999); // 변경 필요: 올바른 IP 및 포트 설정
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            objectOut = new ObjectOutputStream(socket.getOutputStream());

            // 사용자 이름 및 지뢰 정보를 서버로 전송
            objectOut.writeObject(userName);
            objectOut.writeObject(mines);
            objectOut.flush();

            // 서버 메시지 수신 스레드 시작
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String message;
                        while ((message = in.readLine()) != null) {
                            textArea.append(message + "\n");
                            if (message.equals("Game Over") || message.contains("has died")) {
                                break; // 게임 종료 시 루프 탈출
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

    public static void main(String[] args) {
        new SubmarineClient();
    }
}
