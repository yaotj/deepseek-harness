package com.chinasofti.huateng.model.domain;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 「落库状态 + 扫表补偿」（手写 transactional outbox）的**扫描循环骨架**。
 */
public final class OutboxScan {

    private OutboxScan() {
    }

    /**
     * 一轮扫描的结果。{@code scanned} 是本轮捞到的行数，恒等于 {@code success + failed}。
     */
    public record Result(int scanned, int success, int failed) {
    }

    /**
     * 逐行投递并统计。
     * @param rows 本轮扫到的待投递行；{@code null} 或空直接返回全 0。
     * @param deliver 投递一行，返回 true 表示已确认成功。
     * @param onFailure {@code deliver} 返回 false 后的处置，如「重试次数达上限就开工单」。
     * @param onUnexpected {@code deliver} / {@code onFailure} 抛异常时的兜底。
     */
    public static <T> Result run(List<T> rows,
                                 Predicate<T> deliver,
                                 Consumer<T> onFailure,
                                 BiConsumer<T, RuntimeException> onUnexpected) {
        if (rows == null || rows.isEmpty()) {
            return new Result(0, 0, 0);
        }
        int success = 0;
        int failed = 0;
        for (T row : rows) {
            boolean delivered = false;
            try {
                delivered = deliver.test(row);
                if (!delivered) {
                    onFailure.accept(row);
                }
            } catch (RuntimeException e) {
                onUnexpected.accept(row, e);
            }
            if (delivered) {
                success++;
            } else {
                failed++;
            }
        }
        return new Result(rows.size(), success, failed);
    }
}
