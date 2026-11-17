package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import java.util.ArrayList;
import java.util.List;

public final class PayTxnViews {

    private PayTxnViews() {
    }

    public static List<PayTxnDetailDTO> toDtoList(List<PayTxnDetail> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        List<PayTxnDetailDTO> dtoList = new ArrayList<>(entities.size());
        for (PayTxnDetail entity : entities) {
            dtoList.add(toDto(entity));
        }
        return dtoList;
    }

    public static PayTxnDetailDTO toDto(PayTxnDetail entity) {
        PayTxnDetailDTO dto = new PayTxnDetailDTO();
        dto.setId(entity.getId());
        dto.setOrderNo(entity.getOrderNo());
        dto.setPayType(entity.getPayType());
        dto.setPayStatus(entity.getPayStatus());
        dto.setThirdUserId(entity.getThirdUserId());
        dto.setCardId(entity.getCardId());
        dto.setCardType(entity.getCardType());
        dto.setPaymentVendor(entity.getPaymentVendor());
        dto.setRequestSignSeq(entity.getRequestSignSeq());
        dto.setAmount(entity.getAmount());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setCashAmount(entity.getCashAmount());
        dto.setCouponAmount(entity.getCouponAmount());
        dto.setRefundStatus(entity.getRefundStatus());
        dto.setRefundAmount(entity.getRefundAmount());
        dto.setLastRefundTime(entity.getLastRefundTime());
        dto.setMerchantOrderNo(entity.getMerchantOrderNo());
        dto.setChannelOrderNo(entity.getChannelOrderNo());
        dto.setPayUserId(entity.getPayUserId());
        dto.setRequestCount(entity.getRequestCount());
        dto.setNextRequestTime(entity.getNextRequestTime());
        dto.setLastRequestTime(entity.getLastRequestTime());
        dto.setFirstRequestTime(entity.getFirstRequestTime());
        dto.setResponseTime(entity.getResponseTime());
        dto.setPayTime(entity.getPayTime());
        dto.setTxnDate(entity.getTxnDate());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setDiscountInfo(entity.getDiscountInfo());
        dto.setDebitRequestResult(entity.getDebitRequestResult());
        dto.setDiscountFee(entity.getDiscountFee());
        return dto;
    }
}
