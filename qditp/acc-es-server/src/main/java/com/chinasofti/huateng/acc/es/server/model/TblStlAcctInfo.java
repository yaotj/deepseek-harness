package com.chinasofti.huateng.acc.es.server.model;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TBL_STL_ACCT_INFO
 * @author 
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TblStlAcctInfo implements Serializable {
    private String ticketLogicNo;

    private Short ticketType;

    private Short ticketSubType;

    private String ticketCsn;

    private String ticketFaceNo;

    private String cardType;

    private Short ticketVer;

    private Integer phyType;

    private String pubDate;

    private Integer pubBatchNo;

    private String validArea;

    private String miscCd;

    private Integer initAmt;

    private Integer initRewAmt;

    private Integer depAmt;

    private Integer remDepAmt;

    private String expireDate;

    private String ticketStatus;

    private String refundStatus;

    private String changeDate;

    private Date saleTime;

    private Integer totAddAmt;

    private Integer totAddCount;

    private Integer addCounter;

    private Integer totConsCount;

    private Integer totConsAmt;

    private Integer consCounter;

    private Integer ticketBal;

    private Integer acctBal;

    private Date lastTxnTms;

    private String orgTicketId;

    private String newTicketId;

    private String lastUpdUser;

    private Date lastUpdTms;

    private static final long serialVersionUID = 1L;


}