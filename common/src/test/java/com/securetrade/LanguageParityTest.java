package com.securetrade;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LanguageParityTest {
    private static final char UTF8_BOM = 0xFEFF;
    private static final Path LANG_DIR = Path.of("src", "main", "resources", "assets", "securetrade", "lang");

    @Test
    void everyLanguageHasExactlyTheEnglishKeys() throws IOException {
        Set<String> english = keys(LANG_DIR.resolve("en_us.json"));
        List<Path> files;
        try (Stream<Path> list = Files.list(LANG_DIR)) {
            files = list.filter(file -> file.toString().endsWith(".json")).toList();
        }

        assertEquals(23, files.size());
        for (Path file : files) {
            assertEquals(english, keys(file), () -> file.getFileName() + " differs from en_us.json");
        }
    }

    @Test
    void noTranslationIsEmpty() throws IOException {
        try (Stream<Path> list = Files.list(LANG_DIR)) {
            for (Path file : list.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject json = read(file);
                for (String key : json.keySet()) {
                    assertFalse(json.get(key).getAsString().isBlank(), () -> file.getFileName() + ": " + key);
                }
            }
        }
    }

    private static Set<String> keys(Path file) throws IOException {
        return new TreeSet<>(read(file).keySet());
    }

    private static JsonObject read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == UTF8_BOM) {
            text = text.substring(1);
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
