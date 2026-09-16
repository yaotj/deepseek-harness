package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.app.TransRecordDTO;

/**
 * 将 {@link TransListEntry} 双源数据组装为 APP 应答 {@link TransRecordDTO}。
 *
 * <p>组装规则：
 * <ul>
 *   <li>基础信息（卡号、设备、流水号、金额、商户号等）优先取 GATE_TXN_PAY，缺失时取 PAY_TXN_DETAIL 兜底。</li>
 *   <li>扣款结果、支付状态、支付时间等支付细节以 PAY_TXN_DETAIL 为准。</li>
 *   <li>进出站：GateTxnPayListDTO 一条记录已包含完整进出站信息，直接映射，不再按 TRX_TYPE 区分。</li>
 * </ul>
 */
public class TransRecordAssembler {

    public static TransRecordDTO assemble(TransListEntry entry) {
        if (entry == null) {
            return null;
        }
        TransRecordDTO dto = new TransRecordDTO();
        // 基础信息
        dto.setCardNum(nullSafe(entry.getCardId()));
        // 金额（分转元，保留两位）
        dto.setPayAmount(toYuan(entry.getTrxAmount()));
        dto.setOrderExpType(nullSafe(entry.getOrderExpType()));
        dto.setTradeOrderNo(nullSafe(entry.getOrderNo()));
        dto.setPayTradeOrderNo(nullSafe(entry.getChannelOrderNo()));
        dto.setPayOrderNoDate(nullSafe(entry.getTxnDate()));
        // 商户号
        dto.setAttributableParty(nullSafe(entry.getAttributableParty()));
        dto.setReceivingParty(nullSafe(entry.getReceivingParty()));
        // 支付信息（来自 PAY_TXN_DETAIL）
        dto.setPayChannelCode(nullSafe(entry.getPaymentVendor()));
        dto.setDebitRequestResult(entry.getPayDebitRequestResult());
        dto.setDiscountFee(entry.getPayDiscountFee());
        dto.setDiscountInfo(entry.getPayDiscountInfo());
        // 进出站信息（GateTxnPayListDTO 一条记录已包含完整信息）
        dto.setEntryStationName(nullSafe(entry.getEntryStationName() != null ? entry.getEntryStationName() : entry.getInStation()));
        dto.setEntryDate(nullSafe(entry.getInTime()));
        dto.setExitStationName(nullSafe(entry.getExitStationName() != null ? entry.getExitStationName() : entry.getOutStation()));
        dto.setExitDate(nullSafe(entry.getOutTime()));
        // 票卡信息
        dto.setCompanionFlag(nullSafe(entry.getCompanionFlag()));
        dto.setTicketCode(nullSafe(entry.getTicketCode()));
        dto.setCountingTimes(entry.getCountingTimes());
        dto.setCountingFlag(nullSafe(entry.getCountingFlag()));
        dto.setOfflineFlag(nullSafe(entry.getOfflineFlag()));
        return dto;
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    private static String toYuan(Integer fen) {
        if (fen == null) {
            return null;
        }
        return String.format("%.2f", fen / 100.0);
    }
}
