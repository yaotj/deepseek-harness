package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 创建对账批次的请求（IF-RECON-00）。
 */
@Data
public class ReconCreateBatchReqDTO {

    /** 批次标识，建议 {@code RECON<yyyyMMdd>}。 */
    private String batchId;

    /** 账期日期 {@code yyyyMMdd}。 */
    private String businessDate;

    /** 窗口起点（含），{@code yyyyMMddHHmmss}。 */
    private String windowStart;

    /** 窗口终点（不含），{@code yyyyMMddHHmmss}。 */
    private String windowEnd;
}
