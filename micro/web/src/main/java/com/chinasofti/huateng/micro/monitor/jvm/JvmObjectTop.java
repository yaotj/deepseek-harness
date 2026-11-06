package com.chinasofti.huateng.micro.monitor.jvm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

public class JvmObjectTop {

    public static Logger log = LoggerFactory.getLogger(JvmObjectTop.class);

    public static List<Map.Entry<String, Long>> getObjectCountTop(long maxSize) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName diagCmdName = new ObjectName("com.sun.management:type=DiagnosticCommand");

            Object[] params = {new String[0]};
            String[] signature = {"[Ljava.lang.String;"};
            String histoOutput = (String) server.invoke(
                    diagCmdName,
                    "gcClassHistogram",
                    params,
                    signature
            );

            // 解析输出（格式：num #instances #bytes class name）
            Map<String, Long> countMap = new HashMap<>();
            Scanner scanner = new Scanner(histoOutput);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.startsWith("num") || line.startsWith("Total")) {
                    continue;
                }

                String[] parts = Arrays.stream(line.split("\\s+"))
                        .filter(s -> !s.isBlank())
                        .toArray(String[]::new);

                if (parts.length < 4) continue;

                try {
                    long count = Long.parseLong(parts[1]);
                    String className = parts[3];

                    // 过滤数组、内部类（可选）
                    if (className.startsWith("[") || className.startsWith("java.lang.ref")) {
                        continue;
                    }
                    countMap.put(className, count);
                } catch (NumberFormatException ignored) {
                }
            }

            // 排序取 TOP
            return countMap.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .limit(maxSize)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("获取类直方图失败", e);
        }
    }

    public static void getObjectCountTopAndLog(long maxSize) {
        var top10 = getObjectCountTop(maxSize);
        for (int i = 0; i < top10.size(); i++) {
            var e = top10.get(i);
            log.info("{}. {} 实例数量：{}", i + 1, e.getKey(), e.getValue());
        }
    }

}