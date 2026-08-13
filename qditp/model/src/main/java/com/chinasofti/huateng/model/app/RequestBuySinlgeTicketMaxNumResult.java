package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * IF8A-09 获取单次购买单程票最大张数响应参数。
 *
 * <p>字段名称遵循 APP 接口规范中的既有拼写 {@code Sinlge}。</p>
 */
public class RequestBuySinlgeTicketMaxNumResult extends CommonResult {
    /** 单次可购买的单程票最大张数。 */
    private String buySinlgeTicketMaxNum;

    public String getBuySinlgeTicketMaxNum() {
        return buySinlgeTicketMaxNum;
    }

    public void setBuySinlgeTicketMaxNum(String buySinlgeTicketMaxNum) {
        this.buySinlgeTicketMaxNum = buySinlgeTicketMaxNum;
    }
}
