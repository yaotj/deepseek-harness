package com.chinasofti.huateng.facepay.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 架构门禁：把本模块两条只靠注释维持的约束固化成会失败的构建。 */
class F2fGuardTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** 当前实测：7 个 {@code @Scheduled} 分布在 6 个类（补款那个类里有两个）。 */
    private static final int EXPECTED_SCHEDULED = 7;
    private static final int EXPECTED_SCHEDULED_CLASSES = 6;

    @Test
    void noRawDuplicateKeyCatchInCode() throws IOException {
        List<String> hits = scanCodeLines("catch (DuplicateKeyException");
        assertTrue(hits.isEmpty(),
                "幂等兜底 MUST 走 F2fDuplicateKey.isConflict 沿 cause 链判定，裸 catch 在开 tracing 后会静默失效；"
                        + "命中: " + hits);
    }

    @Test
    void scheduledInventoryIsPinnedForSingleReplicaConstraint() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String trimmed = lines.get(i).trim();
                    if (trimmed.startsWith("@Scheduled")) {
                        hits.add(file.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
        long classes = new TreeSet<>(hits.stream().map(h -> h.substring(0, h.indexOf(':'))).toList()).size();
        assertEquals(EXPECTED_SCHEDULED, hits.size(),
                "本模块 @Scheduled 数量变了。它们都没有分布式锁、MUST 单副本；"
                        + "新增或删除时 MUST 同步复核单副本约束与 docs 口径。当前: " + hits);
        assertEquals(EXPECTED_SCHEDULED_CLASSES, classes,
                "@Scheduled 的宿主类数变了，同步复核后再改本测试的期望值。当前: " + hits);
    }

    /** 返回剥离注释后仍命中 needle 的行，形如 {@code 文件名:行号 | 代码}。 */
    private static List<String> scanCodeLines(String needle) throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                boolean inBlock = false;
                for (int i = 0; i < lines.size(); i++) {
                    String raw = lines.get(i);
                    String code = inBlock ? afterBlockEnd(raw) : beforeCommentStart(raw);
                    inBlock = nextInBlock(raw, inBlock);
                    if (code.contains(needle)) {
                        hits.add(file.getFileName() + ":" + (i + 1) + " | " + code.trim());
                    }
                }
            }
        }
        return hits;
    }

    private static String afterBlockEnd(String raw) {
        int end = raw.indexOf("*/");
        return end < 0 ? "" : raw.substring(end + 2);
    }

    private static String beforeCommentStart(String raw) {
        int block = raw.indexOf("/*");
        int slash = raw.indexOf("//");
        if (block >= 0 && (slash < 0 || block < slash)) {
            return raw.substring(0, block);
        }
        return slash >= 0 ? raw.substring(0, slash) : raw;
    }

    private static boolean nextInBlock(String raw, boolean inBlock) {
        int from = 0;
        boolean state = inBlock;
        while (from < raw.length()) {
            if (state) {
                int end = raw.indexOf("*/", from);
                if (end < 0) {
                    return true;
                }
                state = false;
                from = end + 2;
            } else {
                int start = raw.indexOf("/*", from);
                int slash = raw.indexOf("//", from);
                if (start < 0 || (slash >= 0 && slash < start)) {
                    return false;
                }
                state = true;
                from = start + 2;
            }
        }
        return state;
    }
}
