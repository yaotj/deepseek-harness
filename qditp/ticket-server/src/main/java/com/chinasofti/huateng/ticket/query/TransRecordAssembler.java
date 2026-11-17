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
        dto.setPayChannelCode(nullSafe(pay != null ? pay.getPaymentVendor() : null));
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

    static Integer resolveTotalAmount(GateTxnPayListDTO gate, PayTxnDetailDTO pay) {
        if (pay != null && pay.getTotalAmount() != null) {
            return pay.getTotalAmount();
        }
        return gate.getOriginalFare();
    }

    private static Integer resolveDiscountFee(PayTxnDetailDTO pay) {
        if (pay == null) {
            return null;
        }
        return pay.getDiscountFee() != null ? pay.getDiscountFee() : pay.getCouponAmount();
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
        return "SUCCESS".equals(authoritative) ? "0" : "1";
    }

    private static String toFen(Integer fen) {
        if (fen == null) {
            return null;
        }
        return String.valueOf(fen);
    }
}
