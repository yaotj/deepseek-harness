package com.chinasofti.huateng.acc.es.server.model;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TBL_STL_TICKET_INFO
 * @author 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblStlTicketInfo implements Serializable {
    private String ticketLogicNo;

    private Short ticketType;

    private Short ticketSubType;

    private String ticketCsn;

    private String faceId;

    private String ticketKindFlag;

    private Short ticketVer;

    private Integer phyType;

    private String pubDate;

    private Integer pubBatchNo;

    private String validArea;

    private String miscCd;

    private Integer initAmt;

    private Integer initRewAmt;

    private String expireDate;

    private String ticketStatus;

    private String changeDate;

    private Date saleTime;

    private Integer consCounter;

    private Integer ticketBal;

    private Integer acctBal;

    private Date lastTxnTms;

    private String lastUpdUser;

    private Date lastUpdTms;

    private String originalTicketNo;

    private static final long serialVersionUID = 1L;
}