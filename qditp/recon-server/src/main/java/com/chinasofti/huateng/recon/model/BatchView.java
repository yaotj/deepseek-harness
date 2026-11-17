package com.chinasofti.huateng.recon.model;

import java.time.LocalDateTime;

public record BatchView(String batchId,
                        String businessDate,
                        String windowStart,
                        String windowEnd,
                        ReconBatchStatus status,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt) {
}
