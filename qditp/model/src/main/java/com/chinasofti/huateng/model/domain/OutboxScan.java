package com.chinasofti.huateng.model.domain;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;


public final class OutboxScan {

    private OutboxScan() {
    }


    public record Result(int scanned, int success, int failed) {
    }


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
