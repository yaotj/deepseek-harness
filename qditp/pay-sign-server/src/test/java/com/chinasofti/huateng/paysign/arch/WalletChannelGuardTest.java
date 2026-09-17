package com.chinasofti.huateng.paysign.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 护栏：渠道码常量单点持有（ADR-D108），入口只准 classify + 穷尽 switch（ADR-D109）。 */
class WalletChannelGuardTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** ADR-D108：渠道码常量单点持有。收口前 5 份副本，现在只允许这一处。 */
    @Test
    void walletCodeConstantIsDeclaredOnlyInPaymentChannels() throws IOException {
        List<String> hits = scanCodeLines("private static final String WALLET");
        assertEquals(1, hits.size(),
                "渠道码常量只允许在 support/PaymentChannels 声明一次，实际命中: " + hits);
        assertTrue(hits.get(0).contains("PaymentChannels.java"),
                "唯一声明必须在 PaymentChannels，实际在: " + hits.get(0));
    }

    /** ADR-D109：入口不得用布尔谓词分派，那个 else 会静默兜住新增渠道。 */
    @Test
    void noBooleanPredicateDispatchInCode() throws IOException {
        List<String> hits = new ArrayList<>();
        hits.addAll(scanCodeLines("if (isWallet("));
        hits.addAll(scanCodeLines("if (PaymentChannels.isWallet("));
        assertTrue(hits.isEmpty(),
                "入口级分派 MUST 用 classify + 穷尽 switch，不得退回布尔谓词，命中: " + hits);
    }

    /** ADR-D109：每个 classify 调用点都必须就地进入 switch，且数量不得减少。 */
    @Test
    void everyClassifyCallSiteFeedsASwitch() throws IOException {
        List<String> hits = scanCodeLines("PaymentChannels.classify(");
        assertTrue(hits.size() >= 6,
                "classify 调用点不得少于收口时的 6 处（少一处即意味着某个入口退回了旧写法），实际: " + hits.size());
        List<String> notSwitch = hits.stream().filter(line -> !line.contains("switch")).toList();
        assertTrue(notSwitch.isEmpty(),
                "classify 的返回值只能交给穷尽 switch，以下调用点没有: " + notSwitch);
    }

    /** 返回剥离注释后仍命中 needle 的行，形如 {@code 文件名:行号 | 代码}。 */
    private static List<String> scanCodeLines(String needle) throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                boolean inBlock = false;
                for (int i = 0; i < lines.size(); i++) {
                    String code = stripComment(lines.get(i), inBlock);
                    inBlock = nextInBlock(lines.get(i), inBlock);
                    if (code.contains(needle)) {
                        hits.add(file.getFileName() + ":" + (i + 1) + " | " + code.trim());
                    }
                }
            }
        }
        return hits;
    }

    private static String stripComment(String raw, boolean inBlock) {
        if (inBlock) {
            int end = raw.indexOf("*/");
            return end < 0 ? "" : raw.substring(end + 2);
        }
        String line = raw;
        int block = line.indexOf("/*");
        int slash = line.indexOf("//");
        if (block >= 0 && (slash < 0 || block < slash)) {
            return line.substring(0, block);
        }
        return slash >= 0 ? line.substring(0, slash) : line;
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
