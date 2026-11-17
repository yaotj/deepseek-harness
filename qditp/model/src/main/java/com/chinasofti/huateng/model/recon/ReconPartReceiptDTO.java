package com.chinasofti.huateng.model.recon;

import lombok.Data;


@Data
public class ReconPartReceiptDTO {

    private String batchId;

    private String source;

    private String fileType;

    private int partNo;

    private long byteCount;

    private long recordCount;

    private long amountTotal;

    private String sha256;

    private String status;

    private boolean duplicated;
}
