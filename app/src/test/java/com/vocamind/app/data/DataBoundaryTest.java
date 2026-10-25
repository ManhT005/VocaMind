package com.vocamind.app.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-level architecture guard: UI/domain cannot bypass repository contracts. */
public class DataBoundaryTest {
    @Test public void presentationAndDomainDoNotReferenceDataOrRoom() throws Exception {
        Path source = findSources();
        int inspected = 0;
        for (String area : new String[]{"feature", "domain"}) {
            Path directory = source.resolve("com/vocamind/app/" + area);
            List<Path> files;
            try (var stream = Files.walk(directory)) {
                files = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
            }
            assertFalse("Expected sources in " + area, files.isEmpty());
            for (Path file : files) {
                String code = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(file + " depends on data implementation", code.contains("com.vocamind.app.data."));
                assertFalse(file + " depends on Room", code.contains("androidx.room."));
                if (area.equals("domain")) {
                    assertFalse(file + " depends on Android", code.matches("(?s).*import\\s+android\\..*"));
                }
                inspected++;
            }
        }
        assertTrue(inspected > 0);
    }

    private Path findSources() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            for (String candidate : new String[]{"app/src/main/java", "src/main/java"}) {
                Path source = current.resolve(candidate);
                if (Files.isDirectory(source.resolve("com/vocamind/app/domain"))) return source;
            }
            current = current.getParent();
        }
        throw new AssertionError("Cannot find app Java source tree for boundary inspection");
    }
}
