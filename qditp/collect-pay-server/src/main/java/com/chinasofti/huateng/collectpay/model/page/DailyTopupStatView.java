package com.chinasofti.huateng.collectpay.model.page;

import lombok.Data;

/** 按日聚合的订单统计行（TVM 充值日报表 / 非现金充值数据查询共用，纯查询口径）。 */
@Data
public class DailyTopupStatView {
    /** 统计日期，yyyy-MM-dd。 */
    private String statDate;
    /** 当日订单总数。 */
    private long orderCount;
    /** 当日支付成功（STATUS='1'）笔数。 */
    private long paidCount;
    /** 当日已发起退款（RSV2 非空）笔数。 */
    private long refundCount;
    /** 当日支付成功订单的 TRANS_AMOUNT 合计，单位分。 */
    private long paidTransAmountSum;
}
