package com.chinasofti.huateng.gatetxnpay;

import com.alibaba.druid.wall.WallUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 守住「Oracle 合法、Druid WallFilter 却判成注入」这一类静默失效。 */
class MapperSqlWallCompatibilityTest {

    /** 单数 ROW ONLY：Oracle 接受、druid 1.2.23 的 Oracle 解析器不接受。 */
    private static final Pattern SINGULAR_FETCH =
            Pattern.compile("FETCH\\s+(FIRST|NEXT)\\s+\\S+\\s+ROW\\s+ONLY", Pattern.CASE_INSENSITIVE);

    @Test
    void wallFilterRejectsSingularRowOnly() {
        String base = "SELECT LEVEL_AMT FROM DISCOUNT_LEVEL ORDER BY LEVEL_AMT DESC ";
        assertFalse(WallUtils.isValidateOracle(base + "FETCH FIRST 1 ROW ONLY"),
                "若本断言失败说明 druid 已能解析单数形式，可放宽下面的 mapper 扫描");
        assertTrue(WallUtils.isValidateOracle(base + "FETCH FIRST 1 ROWS ONLY"),
                "复数 ROWS ONLY MUST 能通过 WallFilter，否则本模块所有取一行的 SQL 都要改写法");
    }

    @Test
    void noMapperUsesSingularRowOnly() throws IOException {
        Path mapperDir = Paths.get("src/main/resources/mapper");
        assertTrue(Files.isDirectory(mapperDir), "mapper 目录不存在：" + mapperDir.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mapperDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (SINGULAR_FETCH.matcher(content).find()) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "以下 mapper 用了单数 FETCH ... ROW ONLY，会被 Druid WallFilter 判成注入并静默失效，"
                        + "MUST 改成复数 ROWS ONLY：" + offenders);
    }
}
