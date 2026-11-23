package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;

public class TransRecordAssembler {


    public static TransRecordDTO assemble(GateTxnPayListDTO gate, PayTxnDetailDTO pay) {
        if (gate == null) {
            return null;
        }
        TransRecordDTO dto = new TransRecordDTO();
        dto.setCardNum(nullSafe(gate.getCardId()));
        dto.setPayAmount(resolvePayAmount(gate, pay));
        dto.setOrderExpType(nullSafe(gate.getOrderExpType()));
        dto.setTradeOrderNo(nullSafe(gate.getOrderNo()));
        dto.setPayTradeOrderNo(nullSafe(pay != null ? pay.getChannelOrderNo() : null));
        dto.setPayOrderNoDate(nullSafe(pay != null ? pay.getPayTime() : null));
        dto.setAttributableParty(nullSafe(gate.getAttributableParty()));
        dto.setReceivingParty(nullSafe(gate.getReceivingParty()));
        // IT-082 阶段三：当 PAY_TXN_DETAIL 缺失时（如免扣费交易、站中出站等场景），
        // 从 GATE_TXN_PAY.PAYMENT_VENDOR 兜底获取渠道信息，避免 payChannelCode 为空。
        dto.setPayChannelCode(resolvePayChannelCode(pay, gate));
        dto.setDebitRequestResult(toAppDebitResult(gate.getDebitStatus(),
                pay != null ? pay.getDebitRequestResult() : null));
        dto.setDiscountFee(resolveDiscountFee(pay));
        dto.setDiscountInfo(resolveDiscountInfo(pay));
        dto.setTransferFlag(gate.getTransferFlag());
        dto.setCumulativeType(gate.getCumulativeType());
        dto.setOriginalFare(gate.getOriginalFare());
        dto.setTotalAmount(resolveTotalAmount(gate, pay));
        dto.setWalletTotalAmt(gate.getWalletTotalAmt());
        dto.setDiscountLevelAmt(gate.getDiscountLevelAmt());
        dto.setDiscountRate(gate.getDiscountRate());
        dto.setExpectedGateAmount(gate.getExpectedGateAmount());
        dto.setEntryStationName(nullSafe(gate.getEntryStationName() != null ? gate.getEntryStationName() : gate.getInStation()));
        dto.setEntryDate(nullSafe(gate.getInTime()));
        dto.setExitStationName(nullSafe(gate.getExitStationName() != null ? gate.getExitStationName() : gate.getOutStation()));
        dto.setExitDate(nullSafe(gate.getOutTime()));
        dto.setCompanionFlag(nullSafe(gate.getCompanionFlag()));
        dto.setTicketCode(nullSafe(gate.getTicketCode()));
        dto.setCountingTimes(gate.getCountingTimes());
        dto.setCountingFlag(nullSafe(gate.getCountingFlag()));
        dto.setOfflineFlag(nullSafe(gate.getOfflineFlag()));
        return dto;
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }


    static String resolvePayAmount(GateTxnPayListDTO gate, PayTxnDetailDTO pay) {
        if (pay != null && pay.getCashAmount() != null) {
            return String.valueOf(pay.getCashAmount());
        }
        return toFen(gate.getTotalAmount());
    }

    static String resolveTotalAmount(GateTxnPayListDTO gate, PayTxnDetailDTO pay) {
        Integer fen = pay != null && pay.getTotalAmount() != null ? pay.getTotalAmount() : gate.getOriginalFare();
        return toFen(fen);
    }

    private static String resolveDiscountFee(PayTxnDetailDTO pay) {
        if (pay == null) {
            return null;
        }
        return toFen(pay.getDiscountFee() != null ? pay.getDiscountFee() : pay.getCouponAmount());
    }


    private static String resolveDiscountInfo(PayTxnDetailDTO pay) {
        if (pay == null) {
            return null;
        }
        if (pay.getDiscountInfo() != null && !pay.getDiscountInfo().isBlank()) {
            return pay.getDiscountInfo();
        }
        return pay.getChannelDiscountInfo();
    }


    static String toAppDebitResult(String gateDebitStatus, String payDebitRequestResult) {
        String authoritative = gateDebitStatus != null && !gateDebitStatus.isBlank()
                ? gateDebitStatus : payDebitRequestResult;
        // IT-082 阶段三（2026-11-20 用户裁定，APP 侧口径已确认）：对外四态 ——
        // 0=线上扣款成功、1=线下补款人工成功（暂不产出）、空=支付中、其他一律失败。
        // 「支付中」MUST 返回 null（JSON 里该字段为 null，等同旧 ITP 的「不传值」），
        // NEVER 用 1 / 3 / 占位串表达在途态 —— APP 把 1 当成功态、把非空非 0/1 一律判失败。
        // 在途白名单只有 INIT / PROCESSING / RETRY 三个；CLOSED 与未知值 MUST 落 2，
        // NEVER 反过来按「支付中」兜底 —— 那会让一条没有定论的脏数据在 APP 上永远显示「支付中」。
        // 本方法与 trans-query-server 同名方法 MUST 逐字一致（跨模块不能复用，见 AGENTS.md §4.1）。
        if (authoritative == null || authoritative.isBlank()) {
            return "2";
        }
        return switch (authoritative) {
            case "SUCCESS" -> "0";
            case "INIT", "PROCESSING", "RETRY" -> null;
            default -> "2";
        };
    }

    private static String resolvePayChannelCode(PayTxnDetailDTO pay, GateTxnPayListDTO gate) {
        if (pay != null && pay.getPaymentVendor() != null && !pay.getPaymentVendor().isBlank()) {
            return pay.getPaymentVendor();
        }
        // 兜底：从闸机订单获取支付渠道
        return gate.getPaymentVendor() != null ? gate.getPaymentVendor() : "";
    }

    private static String toFen(Integer fen) {
        if (fen == null) {
            return null;
        }
        return String.valueOf(fen);
    }
}
