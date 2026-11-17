package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import java.util.List;


public class RequestPayTxnBatchResult {
    private String retCode;
    private String retMsg;
    private List<PayTxnDetailDTO> payTxnDetailList;
    private List<PayTxnDetailDTO> data;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public List<PayTxnDetailDTO> getPayTxnDetailList() {
        if (payTxnDetailList != null) {
            return payTxnDetailList;
        }
        return data;
    }

    public void setPayTxnDetailList(List<PayTxnDetailDTO> payTxnDetailList) {
        this.payTxnDetailList = payTxnDetailList;
    }

    public List<PayTxnDetailDTO> getData() {
        return data;
    }

    public void setData(List<PayTxnDetailDTO> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "RequestPayTxnBatchResult{retCode='" + retCode + "', retMsg='" + retMsg +
                "', payTxnDetailList=" + payTxnDetailList + "}";
    }
}
