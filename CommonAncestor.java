import java.util.Arrays;
import java.util.Scanner;

public class CommonAncestor {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int[] fa = new int[31];

        while (sc.hasNext()) {
            int n = sc.nextInt();
            Arrays.fill(fa, 0);

            for (int i = 0; i < n; i++) {
                int a = sc.nextInt();
                int b = sc.nextInt();
                fa[a] = b;
            }

            int d1 = 0, x = 1;
            while (fa[x] != 0) {
                d1++;
                x = fa[x];
            }

            int d2 = 0;
            x = 2;
            while (fa[x] != 0) {
                d2++;
                x = fa[x];
            }

            if (d2 < d1) {
                System.out.println("You are my elder");
            } else if (d2 > d1) {
                System.out.println("You are my younger");
            } else {
                System.out.println("You are my brother");
            }
        }
        sc.close();
    }
}
