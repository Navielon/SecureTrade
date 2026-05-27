package com.securetrade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TradeLogger {
    private static final Path LOG_FILE = Paths.get("logs", "securetrade.log");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(String message) {
        if (!TradeConfig.ENABLE_TRADE_LOGGING.get()) return;
        
        try {
            if (!Files.exists(LOG_FILE.getParent())) {
                Files.createDirectories(LOG_FILE.getParent());
            }
            String time = LocalDateTime.now().format(FORMATTER);
            String logEntry = String.format("[%s] %s%n", time, message);
            Files.writeString(LOG_FILE, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
