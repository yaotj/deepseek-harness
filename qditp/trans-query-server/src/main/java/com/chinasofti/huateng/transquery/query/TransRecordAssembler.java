package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;

/** 将 GATE_TXN_PAY + PAY_TXN_DETAIL 双源数据组装为 APP 应答 {@link TransRecordDTO}。 */
public class TransRecordAssembler {

    /**
     * @param gate GATE_TXN_PAY 行，null 时整条记录无意义、直接返 null
     * @param pay PAY_TXN_DETAIL 行，可以为 null：BOM 补站单与日票免扣费单没有支付明细行，
     */
    public static TransRecordDTO assemble(GateTxnPayListDTO gate, PayTxnDetailDTO pay) {
        if (gate == null) {
            return null;
        }
        TransRecordDTO dto = new TransRecordDTO();
        dto.setCardNum(nullSafe(gate.getCardId()));
        dto.setPayAmount(toFen(gate.getTotalAmount()));
        dto.setOrderExpType(nullSafe(gate.getOrderExpType()));
        dto.setTradeOrderNo(nullSafe(gate.getOrderNo()));
        dto.setPayTradeOrderNo(nullSafe(pay != null ? pay.getChannelOrderNo() : null));
        dto.setPayOrderNoDate(nullSafe(pay != null ? pay.getPayTime() : null));
        dto.setAttributableParty(nullSafe(gate.getAttributableParty()));
        dto.setReceivingParty(nullSafe(gate.getReceivingParty()));
        dto.setPayChannelCode(nullSafe(pay != null ? pay.getPaymentVendor() : null));
        dto.setDebitRequestResult(toAppDebitResult(gate.getDebitStatus(),
                pay != null ? pay.getDebitRequestResult() : null));
        dto.setDiscountFee(pay != null ? pay.getDiscountFee() : null);
        dto.setDiscountInfo(pay != null ? pay.getDiscountInfo() : null);
        dto.setTransferFlag(gate.getTransferFlag());
        dto.setCumulativeType(gate.getCumulativeType());
        dto.setOriginalFare(gate.getOriginalFare());
        dto.setTotalAmount(gate.getOriginalFare());
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

    /**
     * 把库内扣款结果转成 APP 侧值域。
     *
     * @param gateDebitStatus {@code GATE_TXN_PAY.DEBIT_STATUS}，权威扣费状态
     * @param payDebitRequestResult {@code PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT}，仅兜底
     */
    static String toAppDebitResult(String gateDebitStatus, String payDebitRequestResult) {
        String authoritative = gateDebitStatus != null && !gateDebitStatus.isBlank()
                ? gateDebitStatus : payDebitRequestResult;
        return "SUCCESS".equals(authoritative) ? "0" : "1";
    }

    private static String toFen(Integer fen) {
        if (fen == null) {
            return null;
        }
        return String.valueOf(fen);
    }
}
