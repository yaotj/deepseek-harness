package com.chinasofti.huateng.model.recon;

import lombok.Data;

/**
 * 分片接收回执（IF-RECON-02 的响应）。
 */
@Data
public class ReconPartReceiptDTO {

    private String batchId;

    private String source;

    private String fileType;

    private int partNo;

    private long byteCount;

    private long recordCount;

    /** 金额合计，单位分。 */
    private long amountTotal;

    private String sha256;

    private String status;

    /** 是否命中幂等（服务端。 */
    private boolean duplicated;
}
