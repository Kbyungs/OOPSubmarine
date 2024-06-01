import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Map {
    int width; // 맵의 너비
    int num_trs; // 보물의 수
    String [][] mineMap; // 지뢰 맵
    String [][] displayMap; // 표시 맵
    HashMap<Integer, Integer> trsPosition; // 보물 위치 저장

    // 생성자: 맵 초기화
    public Map(int width, int num_trs) {
        this.width = width;
        this.num_trs = num_trs;

        // 맵 생성
        System.out.println("Create  "+ width+" X "+ width + "  map");
        mineMap = new String[width][width];
        displayMap = new String[width][width];
        for (int i = 0; i < width * width; i++) {
            mineMap[i / width][i % width] = "0"; // 모든 위치를 "0"으로 초기화
            displayMap[i / width][i % width] = "0"; // 모든 위치를 "0"으로 초기화
        }
        printMap(mineMap); // 맵 출력
    }

    // 특정 위치에 보물이 있는지 확인하는 메서드
    public int checkMine(int x, int y) {
        int pos = (x * width) + y;

        if (trsPosition.containsValue(pos)) { // 보물이 있는지 확인
            System.out.println("   Find mine at ("+x+", "+y+")");
            return pos;
        } else { // 보물이 없음을 알림
            System.out.println("   No mine at ("+x+", "+y+")");
            return -1;
        }
    }

    // 맵을 출력하는 메서드
    public void printMap(String [][] a) {
        System.out.println();
        for (int i = 0; i < a.length; i++) {
            System.out.print(a[i][0]);
            for (int j = 1; j < a[0].length; j++) {
                System.out.print(" " + a[i][j]);
            }
            System.out.println();
        }
    }

    // 맵을 업데이트하는 메서드
    public void updateMap(int x, int y) {
        displayMap[x][y] = "1"; // 지뢰를 밟은 위치 표시
        printMap(displayMap); // 업데이트된 맵 출력
    }
}
