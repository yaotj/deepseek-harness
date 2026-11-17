package com.chinasofti.huateng.model.recon;

import lombok.Data;


@Data
public class ReconSourceCompleteReqDTO {

    private int totalParts;

    private long totalRecords;

    private long totalAmount;

    private String failReason;
}
