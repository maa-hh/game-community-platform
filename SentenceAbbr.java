import java.util.Scanner;

public class SentenceAbbr {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        sc.nextLine();

        for (int i = 0; i < n; i++) {
            String line = sc.nextLine().trim();
            StringBuilder sb = new StringBuilder();

            for (String word : line.split("\\s+")) {
                if (!word.isEmpty()) {
                    sb.append(Character.toUpperCase(word.charAt(0)));
                }
            }

            System.out.println(sb);
        }
        sc.close();
    }
}
