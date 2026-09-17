package com.chinasofti.huateng.fep.dev.model;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;

/** IF1A-02 密钥同步响应业务参数。 */
public class RequestSynKeyListRespDTO extends CommonResult {

    private List<KeyCurVerRespDTO> keyCurVerList;

    public List<KeyCurVerRespDTO> getKeyCurVerList() {
        return keyCurVerList;
    }

    public void setKeyCurVerList(List<KeyCurVerRespDTO> keyCurVerList) {
        this.keyCurVerList = keyCurVerList;
    }
}
