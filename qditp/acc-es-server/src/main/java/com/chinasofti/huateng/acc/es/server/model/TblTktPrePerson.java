package com.chinasofti.huateng.acc.es.server.model;

import java.io.Serializable;
import lombok.Data;


@Data
public class TblTktPrePerson implements Serializable {

    private Integer taskNo;


    private String phyCode;

    private String personType;


    private String userName;


    private String idType;


    private String id;


    private String empNo;


    private String workUnit;


    private static final long serialVersionUID = 1L;
}