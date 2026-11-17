package com.chinasofti.huateng.model.manage;

import java.io.Serializable;


public class SysUserInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;


    private String id;


    private String mlcPwd;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMlcPwd() {
        return mlcPwd;
    }

    public void setMlcPwd(String mlcPwd) {
        this.mlcPwd = mlcPwd;
    }
}
