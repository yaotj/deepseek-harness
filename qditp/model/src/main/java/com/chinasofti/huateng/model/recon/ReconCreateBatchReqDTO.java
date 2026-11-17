package com.chinasofti.huateng.model.recon;

import lombok.Data;


@Data
public class ReconCreateBatchReqDTO {

    private String batchId;

    private String businessDate;

    private String windowStart;

    private String windowEnd;
}
