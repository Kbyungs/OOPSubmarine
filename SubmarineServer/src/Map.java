package submarineserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Map {
    int width; // 맵의 너비
    int num_trs; // 보물의 수
    String [][] mineMap; // 지뢰 맵
    String [][] displayMap; // 표시 맵
    HashMap<Integer, Integer> trsPosition; // 보물의 위치 저장

    // 생성자: 맵 초기화
    public Map(int width, int num_trs) {
        this.width = width;
        this.num_trs = num_trs;

        // 맵 생성
        System.out.println("Create  "+ width+" X "+ width + "  map");
        mineMap = new String [width][width];
        displayMap = new String [width][width];
        for (int i=0; i<width*width; i++) {
            mineMap[i/width][i%width] = "0"; // 모든 위치를 "0"으로 초기화
            displayMap[i/width][i%width] = "0"; // 모든 위치를 "0"으로 초기화
        }

        // 보물 위치 생성
        System.out.println("Create  "+num_trs+"  mines");
        Random r = new Random();
        trsPosition = new HashMap<>();
        for (int i = 0; i < num_trs; i++) {
            int position = r.nextInt(width * width);
            while (trsPosition.containsValue(position)) // 중복되지 않도록 확인
                position = r.nextInt(width * width);
            trsPosition.put(i, position);
        }

        // 보물 배치
        System.out.println("mine positions");
        for (int i = 0; i < num_trs; i++) {
            int x = trsPosition.get(i) / width;
            int y = trsPosition.get(i) % width;
            System.out.println(x+", "+y);
            mineMap[x][y] = "T"; // 보물 위치를 "T"로 설정
            displayMap[x][y] = "s"; // 보물 있는 위치 표시
        }
    }

    // 특정 위치에 지뢰가 있는지 확인하는 메서드
    public int checkMine(int r, int c) {
        if (displayMap[r][c] == "s") { // 보물이 있는지 확인
            if (mineMap[r][c] == "T") return 99; // 보물 발견
            else return 98; // 지뢰 밟음
        } else {
            return Integer.parseInt(mineMap[r][c]); // 아니면 힌트 숫자 반환
        }
    }

    // 맵 출력 메서드
    public void printMap(String [][] a) {
        System.out.println();
        for (int i = 0; i < a.length; i++) {
            System.out.print(a[i][0]);
            for (int j = 1; j < a[0].length; j++)
                System.out.print(" " + a[i][j]);
            System.out.println();
        }
    }

    // 맵 업데이트 메서드
    public void updateMap(int x, int y) {
        displayMap[x][y] = "1";
    }

    // 지뢰 배치 메서드
    public void deployMine(int x, int y, String s) {
        mineMap[x][y] = s;
    }

    // 인접한 지뢰의 수를 반환하는 메서드
    public String numSomething(int r, int c){
        if (displayMap[r][c] == "s")
            return mineMap[r][c];
        int nearby = 0;
        int[] dx = new int[] {0, 0, 1, -1, 1, -1, 1, -1};
        int[] dy = new int[] {1, -1, 0, 0, 1, -1, -1, 1};
        for (int i = 0; i < 8; i++) {
            int newRow = r + dx[i];
            int newCol = c + dy[i];
            if (newRow >= 0 && newRow < width && newCol >= 0 && newCol < width) {
                if (displayMap[newRow][newCol] == "s") nearby++;
            }
        }
        return String.valueOf(nearby);
    }

    // 맵에 힌트 숫자를 부여하는 메서드
    public void numbering() {
        for (int i = 0; i < width*width; i++)
            mineMap[i/width][i%width] = numSomething(i/width, i%width);
    }
}
