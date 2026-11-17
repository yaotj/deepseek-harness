package com.chinasofti.huateng.model.agm;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;


public class RequestAgmSynKeyListResult extends CommonResult {
    private List<AgmKeyCurVerDTO> keyCurVerList;

    public List<AgmKeyCurVerDTO> getKeyCurVerList() { return keyCurVerList; }
    public void setKeyCurVerList(List<AgmKeyCurVerDTO> keyCurVerList) { this.keyCurVerList = keyCurVerList; }
}
