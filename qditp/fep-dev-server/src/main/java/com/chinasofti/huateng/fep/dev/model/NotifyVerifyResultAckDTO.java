package com.chinasofti.huateng.fep.dev.model;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * IF1A-01 闸机检票通知【给闸机的应答】，按接口规范 §4.1.4 只有 retCode + retMsg 两个字段。
 *
 * <p><b>NEVER 在本类里加任何字段。</b>闸机（AGM）侧是老项目，JSON 反序列化对未知字段直接抛异常
 * ——多返回一个字段，闸机就判定本次上送失败并重发，重发上限 3 次。2026-08-27 生产实测：
 * 我方原来直接把 {@link com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO}
 * （16 个字段）序列化给闸机，导致<b>每一笔进站、每一笔出站都被上送 3 次</b>
 * （进站 11:17:30.322 / 30.721 / 31.692，出站 11:17:41.915 / 42.166 / 42.558，
 * 每次都在我方返回 0000 后约 19ms 到达、且报文 timestamp 重新生成，即闸机重建报文而非重传）。
 * 后果是 ticket-server 的 QRCODE_STATUS.USE_COUNT / TXN_SEQ 一趟行程被推进 6 格而非 2 格。</p>
 *
 * <p>与 {@link com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO} 的分工：后者是
 * ticket-server → fep-dev 的<b>内部</b>应答，承载 ticketStatus / payChannelCode / requestSignSeq
 * 等字段供 fep-dev 组装 gate-txn-pay 扣费报文（见 GateTransactionHandler#requestGateTxnPay），
 * <b>MUST 保留全字段</b>；本类只是对外收口，两者不可合并。</p>
 */
public class NotifyVerifyResultAckDTO extends CommonResult {
}
