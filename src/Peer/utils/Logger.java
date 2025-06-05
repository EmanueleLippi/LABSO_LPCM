package Peer.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void info(String msg) {
        System.out.println("[" + time() + "][INFO] " + msg);
    }

    public static void warn(String msg) {
        System.out.println("[" + time() + "][WARN] " + msg);
    }

    public static void error(String msg) {
        System.err.println("[" + time() + "][ERROR] " + msg);
    }

    private static String time() {
        return LocalDateTime.now().format(formatter);
    }
}
