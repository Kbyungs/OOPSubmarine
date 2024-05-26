import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Map {
    int width;
    int num_trs; //treasure
    String [][] mineMap;
    String [][] displayMap;
    HashMap<Integer, Integer> trsPosition;

    public Map(int width, int num_trs) {
        this.width = width;
        this.num_trs = num_trs;

        // create map
        System.out.println("Create  "+ width+" X "+ width + "  map");
        mineMap = new String [width][width];
        displayMap = new String [width][width];
        for (int i=0; i<width*width; i++) {
            mineMap[i/width][i%width] = "0";
            displayMap[i/width][i%width] = "0";
        }

        // create mines
        System.out.println("Create  "+num_trs+"  mines");
        Random r = new Random();
        trsPosition = new HashMap<>();
        for (int i = 0; i < num_trs; i++) {
            int position = r.nextInt(width * width);
            while (trsPosition.containsValue(position))   // check repetition
                position = r.nextInt(width * width);
            trsPosition.put(i, position);
        }

        // deploy mines
        System.out.println("mine positions");
        for (int i = 0; i < num_trs; i++) {
            int x = trsPosition.get(i) / width;
            int y = trsPosition.get(i) % width;
            System.out.println(x+", "+y);
            mineMap[x][y] = "T";
            displayMap[x][y] = "s"; //something
        }

//        printMap(mineMap);

    }

    public int checkMine(int r, int c) {
//        int pos = (r*width) + c;
//        if (trsPosition.containsValue(pos)) {
//            //System.out.println("   Find mine at ("+x+", "+y+")");
//            return pos;
//        }
//        else {
//            //System.out.println("   No mine at ("+x+", "+y+")");
//            return -1;
//        }

        if (displayMap[r][c] == "s") {
            if (mineMap[r][c] == "T") return 99; // 보물발견
            else return 98; //지뢰밟음
        } else {
            return Integer.parseInt(mineMap[r][c]); // 아니면 힌트숫자 주기
        }
    }

    public void printMap(String [][] a) {
        System.out.println();
        for (int i = 0; i < a.length; i++) {
            System.out.print(a[i][0]);
            for (int j = 1; j < a[0].length; j++)
                System.out.print(" " + a[i][j]);
            System.out.println();
        }
    }

    public void updateMap(int x, int y) {
        displayMap[x][y] = "1";
//		printMap(displayMap);
    }

    public void deployMine(int x, int y, String s) {
        mineMap[x][y] = s;
    }

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

    public void numbering() {
        for (int i = 0; i < width*width; i++)
            mineMap[i/width][i%width] = numSomething(i/width, i%width);
    }

}
