package com.chinasofti.huateng.transquery.service;

import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;

/**
 * 交易查询服务对外接口（IF8A-05 / IF8A-34 / IF8A-41）。
 *
 * <p>与 ticket-server 原 {@code TicketTransService} 的差异：**本接口不含支付宝行程查询那两个方法**。
 * 原接口把「APP 交易记录」与「支付宝渠道行程」绑在一起，而两者的数据源完全不同 ——
 * 前者纯 RPC（gate-txn-pay + pay-sign），后者直读 {@code QRCODE_TXN_DETAIL}。
 * 支付宝那两个端点仍在 ticket-server，迁移前提见 {@code TransQueryServer} 的 TODO。
 */
public interface TransQueryService {

    /** IF8A-05 查询交易记录列表。 */
    RequestTransListResult requestTransList(QueryTransListReqDTO request);

    /** IF8A-41 查询账单统计。 */
    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);

    /** IF8A-34 获取订单详情。 */
    RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request);
}
