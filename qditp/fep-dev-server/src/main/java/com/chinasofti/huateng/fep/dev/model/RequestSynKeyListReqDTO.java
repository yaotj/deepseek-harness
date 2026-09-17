package com.chinasofti.huateng.fep.dev.model;

import java.util.List;

/** IF1A-02 密钥同步请求业务参数。 */
public class RequestSynKeyListReqDTO {

    private List<KeyCurVerReqDTO> keyCurVerList;

    public List<KeyCurVerReqDTO> getKeyCurVerList() {
        return keyCurVerList;
    }

    public void setKeyCurVerList(List<KeyCurVerReqDTO> keyCurVerList) {
        this.keyCurVerList = keyCurVerList;
    }
}
