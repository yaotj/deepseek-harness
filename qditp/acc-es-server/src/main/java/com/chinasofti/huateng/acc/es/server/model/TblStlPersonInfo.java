package com.chinasofti.huateng.acc.es.server.model;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TblStlPersonInfo implements Serializable {

    private String ticketId;


    private String personName;


    private String personSex;


    private String pidCd;


    private String pidCode;


    private String passwd;


    private String langCd;


    private String companyNm;


    private String empNo;


    private String conTelNo;


    private String conMail;


    private String address;


    private String postCd;


    private String lastUpdUser;


    private Date lastUpdTms;


    private byte[] photoBlob;


    private static final long serialVersionUID = 1L;
}