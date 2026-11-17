package com.chinasofti.huateng.model.recon;

import lombok.Data;


@Data
public class ReconBatchViewDTO {

    private String batchId;

    private String businessDate;

    private String windowStart;

    private String windowEnd;


    private String status;

    private int completedSources;

    private int expectedSources;

    private String failReason;
}
