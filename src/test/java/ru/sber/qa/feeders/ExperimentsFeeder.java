package ru.sber.qa.feeders;

import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;


public class ExperimentsFeeder {

        private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        private static final int LENGTH = 8;

        private static final SecureRandom random = new SecureRandom();

        static ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));
        static ZonedDateTime tomorrow = now.plusDays(1);
        public static long startDt = tomorrow.toInstant().toEpochMilli();

        static ZonedDateTime nextMonth = now.plusMonths(1);
        public static long endDt = tomorrow.toInstant().toEpochMilli();

        public static long formatId = generateFormatId();



        public static String generateAqaMalilId() {
                StringBuilder sb = new StringBuilder("AQA_malil_");
                for (int i = 0; i < LENGTH; i++) {
                        int index = ThreadLocalRandom.current().nextInt(CHARS.length());
                        sb.append(CHARS.charAt(index));
                }
                return sb.toString();
        }


        public static long generateFormatId() {
                SecureRandom random = new SecureRandom();
                // Максимальный диапазон для long: -9,223,372,036,854,775,808 до 9,223,372,036,854,775,807
                return random.nextLong(); // Возвращает случайный long в полном диапазоне
        }

        public static String generateSalt() {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < LENGTH; i++) {
                        int index = random.nextInt(CHARS.length());
                        sb.append(CHARS.charAt(index));
                }
                return sb.toString();
        }

}
