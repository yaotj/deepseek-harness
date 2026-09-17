package com.chinasofti.huateng.recon.model;

import java.time.LocalDateTime;

/** {@code RECON_BATCH} 一行的视图。 */
public record BatchView(String batchId,
                        String businessDate,
                        String windowStart,
                        String windowEnd,
                        ReconBatchStatus status,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt) {
}
