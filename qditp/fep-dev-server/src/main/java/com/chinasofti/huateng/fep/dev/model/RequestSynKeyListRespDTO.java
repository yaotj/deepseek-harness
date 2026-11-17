package com.chinasofti.huateng.fep.dev.model;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;

public class RequestSynKeyListRespDTO extends CommonResult {

    private List<KeyCurVerRespDTO> keyCurVerList;

    public List<KeyCurVerRespDTO> getKeyCurVerList() {
        return keyCurVerList;
    }

    public void setKeyCurVerList(List<KeyCurVerRespDTO> keyCurVerList) {
        this.keyCurVerList = keyCurVerList;
    }
}
