package com.chinasofti.huateng.recon.model.page;

import lombok.Data;

@Data
public class ReconTxnDiffPageView {
    private String billDate;
    private String txnType;
    private String logicCardNo;
    private String txnDateTime;
    private Long originalFare;
    private String curStationName;
    private String deviceNo;
    private String ticketType;
    private String ticketSubType;
    private String times;
    private String payChannel;
    private String activateTime;
    private String quantity;
    private String ticketTransSeq;
    private String orderNo;
    private Long orderAmount;
    private Long actualAmount;
    private Long discountCumulativeAmount;
    private Long overtimeAmount;
    private String diffSide;
    private String failReason;
    private String batchNo;
    private String createTime;
}
