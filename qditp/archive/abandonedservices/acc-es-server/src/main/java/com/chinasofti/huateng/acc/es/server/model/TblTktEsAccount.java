package com.chinasofti.huateng.acc.es.server.model;

import java.io.Serializable;
import lombok.Data;


@Data
public class TblTktEsAccount implements Serializable {

    private String username;


    private String password;


    private Short userType;


    private String remark;


    private String addDate;


    private String updateDate;


    private String lastUpdateId;


    private static final long serialVersionUID = 1L;
}