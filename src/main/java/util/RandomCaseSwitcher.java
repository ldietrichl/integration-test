package util;

import java.util.Random;

public class RandomCaseSwitcher {
    private static final Random RAND = new Random();

    /**
     * Возвращает новую строку, где каждый символ может случайно
     * поменять регистр (заглавный → строчный, строчный → заглавный).
     */
    public static String randomToggleCase(String input) {
        StringBuilder sb = new StringBuilder(input.length());

        for (char ch : input.toCharArray()) {
            // решаем, менять ли регистр у текущего символа
            if (Character.isLetter(ch) && RAND.nextBoolean()) {
                // меняем регистр
                if (Character.isUpperCase(ch)) {
                    sb.append(Character.toLowerCase(ch));
                } else {
                    sb.append(Character.toUpperCase(ch));
                }
            } else {
                // оставляем как есть
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
