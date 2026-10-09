package com.chinasofti.huateng.gatetxnpay.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构门禁：把本模块两条只靠注释维持的约束固化成会失败的构建。
 *
 * <p>防的是「加回来既不编译失败也不告警」的两类改动；判定在剥离注释后的文本上做。
 * 本类只管 {@code @Scheduled}，NEVER 扩成连 {@code @EnableScheduling} 一起禁
 * （{@code resource/micro} 里有 3 个 {@code @Scheduled} 靠那个开关）。
 */
class GateTxnPayGuardTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MAPPER_XML =
            Path.of("src", "main", "resources", "mapper", "GateTxnPayMapper.xml");

    /** 防本模块加回模块内定时任务（调度已外移到 face-pay 与 web-admin sys_job 295/300，2026-09-21 由 120/121 改号）。 */
    @Test
    void moduleHasNoScheduledAnnotation() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String trimmed = lines.get(i).trim();
                    boolean commentLine = trimmed.startsWith("*") || trimmed.startsWith("//")
                            || trimmed.startsWith("/*");
                    if (!commentLine && trimmed.startsWith("@Scheduled")) {
                        hits.add(file.getFileName() + ":" + (i + 1) + " | " + trimmed);
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "本模块的调度已全部外移（补款迁 face-pay、离线码与换乘迁 web-admin sys_job 295/300），"
                        + "NEVER 加回模块内定时任务；命中: " + hits);
    }

    /** 防两条 converge 语句被合并：补款侧白名单含 FAIL、回调侧不含，改一侧会打挂另一侧。 */
    @Test
    void supplementConvergeWhitelistKeepsFailWhileCallbackOneDoesNot() throws IOException {
        String xml = stripXmlComments(Files.readString(MAPPER_XML, StandardCharsets.UTF_8));

        String callback = statementBody(xml, "convergeDebitStatus");
        String supplement = statementBody(xml, "convergeDebitStatusForSupplement");

        assertTrue(supplement.contains("FAIL"),
                "补款专用收敛的白名单 MUST 含 FAIL，否则会出现「放行下单 + 补款支付成功 + 收敛不了」，"
                        + "钱已实收而行程仍挂欠费");
        assertFalse(callback.contains("FAIL"),
                "支付回调收敛的白名单 MUST NOT 含 FAIL：FAIL 在那条链路里是已到达的终态，"
                        + "不该被回调改写。两条语句 NEVER 合并");
        assertEquals("SUCCESS", targetStatus(supplement),
                "补款收敛的目标状态写死 SUCCESS、不做入参，少一个入参就少一处传错状态的可能");
    }

    /** 取出指定 id 的语句体，id 必须精确匹配，避免前缀包含关系误取。 */
    private static String statementBody(String xml, String id) {
        String marker = "id=\"" + id + "\"";
        int start = xml.indexOf(marker);
        assertTrue(start >= 0, "mapper 里找不到语句 " + id + "，它是本约束的载体，NEVER 删");
        int end = xml.indexOf("</update>", start);
        assertTrue(end > start, "语句 " + id + " 没有闭合的 update 标签");
        return xml.substring(start, end);
    }

    /** 从 {@code SET DEBIT_STATUS = 'X'} 里取出目标状态。 */
    private static String targetStatus(String body) {
        int idx = body.indexOf("DEBIT_STATUS");
        assertTrue(idx >= 0, "收敛语句里必须显式写 DEBIT_STATUS");
        int open = body.indexOf('\'', idx);
        int close = body.indexOf('\'', open + 1);
        return open < 0 || close < 0 ? "" : body.substring(open + 1, close);
    }

    private static String stripXmlComments(String xml) {
        StringBuilder out = new StringBuilder(xml.length());
        int from = 0;
        while (true) {
            int start = xml.indexOf("<!" + "--", from);
            if (start < 0) {
                out.append(xml, from, xml.length());
                return out.toString();
            }
            out.append(xml, from, start);
            int end = xml.indexOf("--" + ">", start);
            if (end < 0) {
                return out.toString();
            }
            from = end + 3;
        }
    }
}
