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

/** 架构门禁：把本模块几条只靠注释维持的约束固化成会失败的构建。 */
class F2fGuardTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** 当前实测：7 个 {@code @Scheduled} 分布在 6 个类（补款那个类里有两个）。 */
    private static final int EXPECTED_SCHEDULED = 7;
    private static final int EXPECTED_SCHEDULED_CLASSES = 6;

    /** 当前实测：5 处逻辑卡号归一，分布在 3 个类（出票落库 / 按票故障 / 交易查询 / 按票退款 / 充值下单）。 */
    private static final int EXPECTED_NORMALIZE = 5;
    private static final int EXPECTED_NORMALIZE_CLASSES = 3;

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

    @Test
    void logicCardNoNormalizeInventoryIsPinned() throws IOException {
        List<String> hits = scanCodeLines("F2fLogicCardNo.normalize(");
        long classes = new TreeSet<>(hits.stream().map(h -> h.substring(0, h.indexOf(':'))).toList()).size();
        assertEquals(EXPECTED_NORMALIZE, hits.size(),
                "逻辑卡号归一点数量变了。F2F_TICKET 的四条语句与 F2F_REFUND 的幂等键都是大小写敏感的精确等值，"
                        + "漏一处即重新制造「入库一种大小写、查询另一种」的 8999。当前: " + hits);
        assertEquals(EXPECTED_NORMALIZE_CLASSES, classes,
                "归一点的宿主类数变了，同步复核后再改本测试的期望值。当前: " + hits);
    }

    @Test
    void rawTicketLogicNumNeverReachesPersistenceOrQuery() throws IOException {
        List<String> hits = scanCodeLines("request.getTicketLogicNum()").stream()
                .filter(h -> !h.contains("F2fLogicCardNo.normalize"))
                .filter(h -> !h.contains("log."))
                .filter(h -> !h.contains("isBlank("))
                .toList();
        assertTrue(hits.isEmpty(),
                "入向的 ticketLogicNum MUST 先过 F2fLogicCardNo.normalize 再进 mapper 或实体（空值校验与日志打原文不受限，"
                        + "后者是判断上游送的是哪种大小写的唯一线索）；命中: " + hits);
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
