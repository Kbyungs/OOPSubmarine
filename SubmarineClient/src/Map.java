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
        mineMap = new String[width][width];
        displayMap = new String[width][width];
        for (int i=0; i<width*width; i++) {
            mineMap[i/width][i%width] = "0";
            displayMap[i/width][i%width] = "0";
        }
        printMap(mineMap);
    }

    public int checkMine(int x, int y) {
        int pos = (x*width) + y;

        if (trsPosition.containsValue(pos)) {
            System.out.println("   Find mine at ("+x+", "+y+")");
            return pos;
        }
        else {
            System.out.println("   No mine at ("+x+", "+y+")");
            return -1;
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
        printMap(displayMap);
    }
}
