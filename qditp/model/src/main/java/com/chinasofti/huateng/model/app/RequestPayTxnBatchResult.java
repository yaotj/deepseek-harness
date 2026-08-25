package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import java.util.List;

/**
 * 批量查询支付明细响应参数。
 */
public class RequestPayTxnBatchResult {
    /** 接口处理状态码，0000 表示成功。 */
    private String retCode;
    /** 返回信息。 */
    private String retMsg;
    /** 支付明细列表（新字段名）。 */
    private List<PayTxnDetailDTO> payTxnDetailList;
    /** 支付明细列表（旧字段名，保留兼容）。 */
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
