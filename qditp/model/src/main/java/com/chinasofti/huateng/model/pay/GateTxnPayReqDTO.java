package com.chinasofti.huateng.model.pay;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;

/**
 * 过闸扣费交易请求。
 *
 * <p>该对象由 fep-dev 在收到 IF1A-01 出站/超时出站交易且 ticket 处理成功后转发给
 * gate-txn-pay-server。字段沿用设备闸机检票通知业务参数，gate-txn-pay-server
 * 负责生成地铁侧 {@code orderNo}、落库 {@code GATE_TXN_PAY}，再调用 pay-sign-server。</p>
 */
public class GateTxnPayReqDTO extends NotifyVerifyResultReqDTO {
}
