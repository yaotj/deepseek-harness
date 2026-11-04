package com.chinasofti.huateng.recon.model.page;

import lombok.Data;

/** 对账差异明细（{@code RECON_TXN_DIFF}）运营查询视图。 */
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
    /** 差异方：见 TxnDiffReconService 口径（渠道侧 / ITP 侧）。 */
    private String diffSide;
    private String failReason;
    private String batchNo;
    private String createTime;
}
