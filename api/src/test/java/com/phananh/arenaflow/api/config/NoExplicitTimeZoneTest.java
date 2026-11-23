package com.phananh.arenaflow.api.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quét tĩnh code production của 4 module: "bây giờ" và lịch chạy job chỉ được lấy theo múi giờ của JVM,
 * không được đặt múi giờ tường minh trong code.
 */
class NoExplicitTimeZoneTest {

    private static final List<String> MODULES = List.of("common", "domain", "application", "api");

    private static final Map<String, Pattern> FORBIDDEN = Map.of(
            "ZoneId.of(", Pattern.compile("\\bZoneId\\.of\\("),
            "ZoneOffset.", Pattern.compile("\\bZoneOffset\\."),
            "TimeZone.getTimeZone(", Pattern.compile("\\bTimeZone\\.getTimeZone\\("),
            "Clock.systemUTC()", Pattern.compile("\\bClock\\.systemUTC\\("),
            "\"Asia/Ho_Chi_Minh\"", Pattern.compile("\"Asia/Ho_Chi_Minh\""),
            "@Scheduled(zone = …)", Pattern.compile("@Scheduled\\s*\\([^)]*\\bzone\\s*=", Pattern.DOTALL));

    @Test
    void productionCodeUsesJvmTimeZoneOnly() {
        Path root = Path.of("").toAbsolutePath().getParent();
        List<String> violations = new ArrayList<>();
        for (String module : MODULES) {
            Path sources = root.resolve(module).resolve("src/main/java");
            assertThat(sources).as("thư mục mã nguồn của module %s", module).isDirectory();
            javaFiles(sources).forEach(file -> violations.addAll(violationsIn(root, file)));
        }

        assertThat(violations).as("đặt múi giờ tường minh trong code production").isEmpty();
    }

    @Test
    void detectsEachWayOfPinningTimeZone() {
        List<String> pinned = List.of(
                "LocalDateTime.now(ZoneId.of(\"UTC\"))",
                "OffsetDateTime.now(ZoneOffset.UTC)",
                "TimeZone.getTimeZone(\"UTC\")",
                "String zone = \"Asia/Ho_Chi_Minh\";",
                "@Scheduled(cron = \"0 0 3 * * *\", zone = \"UTC\")",
                "Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));");

        assertThat(pinned).allMatch(snippet -> FORBIDDEN.values().stream()
                .anyMatch(pattern -> pattern.matcher(snippet).find()));
    }

    @Test
    void detectsEveryForbiddenPattern() {
        String source = """
                class Sample {
                    LocalDateTime a = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
                    OffsetDateTime b = OffsetDateTime.now(ZoneOffset.UTC);
                    TimeZone c = TimeZone.getTimeZone("UTC");
                    Clock d = Clock.systemUTC();
                    @Scheduled(cron = "0 0 3 * * *",
                            zone = "UTC")
                    void job() {}
                }
                """;

        assertThat(FORBIDDEN.values()).allMatch(pattern -> pattern.matcher(source).find());
        assertThat(FORBIDDEN.values()).noneMatch(pattern -> pattern.matcher("""
                @Scheduled(cron = "${job.cron}")
                // JVM chạy -Duser.timezone=Asia/Ho_Chi_Minh
                LocalDateTime now = LocalDateTime.now(clock);
                ZoneId zone = ZoneId.systemDefault();
                """).find());
    }

    /** Mọi file {@code .java} dưới thư mục mã nguồn. */
    private static List<Path> javaFiles(Path sources) {
        try (Stream<Path> paths = Files.walk(sources)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Mỗi mẫu cấm xuất hiện trong file thành một dòng "đường dẫn: mẫu". */
    private static List<String> violationsIn(Path root, Path file) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        String relative = root.relativize(file).toString().replace('\\', '/');
        return FORBIDDEN.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(source).find())
                .map(entry -> relative + ": " + entry.getKey())
                .sorted()
                .toList();
    }
}
