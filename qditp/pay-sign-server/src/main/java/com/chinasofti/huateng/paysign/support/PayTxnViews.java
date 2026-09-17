package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import java.util.ArrayList;
import java.util.List;

/** `PAY_TXN_DETAIL` 实体到对内查询 DTO 的装配（2026-09-16 由 {@code PaySignServiceImpl} 外提，ADR-D98 判据）。 */
public final class PayTxnViews {

    private PayTxnViews() {
    }

    /** 批量装配，顺序与入参一致。{@code null} 入参返回空列表（NEVER 返回 {@code null}）。 */
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

    /** 单条装配。**26 个字段逐一对应，新增字段 MUST 同步加到这里**（有测试守）。 */
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
