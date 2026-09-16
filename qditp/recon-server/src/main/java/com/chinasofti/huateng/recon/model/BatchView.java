package com.chinasofti.huateng.recon.model;

import java.time.LocalDateTime;

/**
 * {@code RECON_BATCH} 一行的视图。
 *
 * @implNote 本 record <b>没有任何基本类型分量</b>，因此不受「MyBatis 构造器映射按装箱类型
 *     精确查找构造器」那条坑影响（详见 {@link SourceProgress}）。新增数值分量时 MUST 用装箱类型。
 */
public record BatchView(String batchId,
                        String businessDate,
                        String windowStart,
                        String windowEnd,
                        ReconBatchStatus status,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt) {
}
