package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 对账批次视图，源服务与运营后台查询批次进度时的响应体。
 */
@Data
public class ReconBatchViewDTO {

    private String batchId;

    private String businessDate;

    private String windowStart;

    private String windowEnd;

    /**
     * 批次状态：{@code CREATED / EXPORTING / PARTIAL / ALL_SOURCE_COMPLETED /。
     */
    private String status;

    /** 已完成声明的源数量。 */
    private int completedSources;

    /** 本批次期望的源数量（来源 recon-server 的期望清单配置）。 */
    private int expectedSources;

    private String failReason;
}
