package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import java.util.ArrayList;
import java.util.List;

/**
 * `PAY_TXN_DETAIL` 实体到对内查询 DTO 的装配（2026-09-16 由 {@code PaySignServiceImpl} 外提，ADR-D98 判据）。
 *
 * <p><b>外提理由是「零协作者」而不是行数</b>：这 26 行只读入参、不碰任何 mapper / client / properties，
 * 调用点搬走后只需 {@code import static}，正是 ADR-D98 立的准入条件。
 *
 * <p><b>真正的风险是「漏一个 setter 不会有任何提示」</b>：`PayTxnDetailDTO` 有 26 个字段，
 * 手写逐字段拷贝时漏掉一个，编译通过、单测（原先零覆盖）也通过，只是调用方拿到的那一列恒为 {@code null}。
 * 收进本类之后由 {@code PayTxnViewsTest} 用反射遍历 DTO 的全部 getter 守着 ——
 * <b>新增字段但忘了在这里搬，那个用例立刻变红</b>。这才是外提换来的东西。
 *
 * <p><b>NEVER 往本类加过滤 / 脱敏 / 状态判断</b>：它只做字段搬运。要按渠道或状态裁剪字段，
 * 那是业务判断，归领域服务。
 */
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
