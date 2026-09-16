package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code ALIPAY_REFUND_TXN_DETAIL} 数据访问，形态对齐 pay-sign-server 的 {@code PayRefundDetailMapper}。
 *
 * <p>两条 UPDATE 的 WHERE 都是 {@code REFUND_ORDER_NO + TXN_DATE}：既命中唯一索引
 * {@code UK_ARTD_REFUND_ORDER}，又能做分区裁剪。<b>NEVER 只按 {@code REFUND_ORDER_NO}</b>。</p>
 *
 * <p>本接口目前没有业务调用方（新表与链路接线分两轮）。接线时注意两条：
 * ① 退款方法<b>不能带 {@code @Transactional}</b>（方法体内要调支付中心，
 * 事务包住网络调用即 AGENTS.md §5.2 那条生产事故；回滚还会把刚插入的退款明细一起丢弃，
 * 而远端可能已受理）；② 幂等靠 {@link #countByOrderNoAndStatus} 的状态短路 +
 * 插入侧沿 {@code getCause()} 链判完整性冲突两道，本表没有调用方提供的幂等键。</p>
 */
@Mapper
public interface AlipayRefundTxnDetailMapper {

    int insert(AlipayRefundTxnDetail record);

    AlipayRefundTxnDetail selectByRefundOrderNo(@Param("refundOrderNo") String refundOrderNo);

    List<AlipayRefundTxnDetail> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 已退成功金额合计（分）。{@code updateRefundSummary} 在 SQL 内自行汇总，
     * 本方法只给 Java 侧做「可退金额 = 已付 - 已退」校验用，
     * <b>NEVER 拿它的返回值去回写主表</b>（那会退回「读旧值再加」的非幂等写法）。
     */
    Integer sumSuccessRefundAmount(@Param("orderNo") String orderNo);

    /**
     * 按原订单号 + 状态计数，退款申请的幂等短路：
     * {@code countByOrderNoAndStatus(orderNo, "PROCESSING") > 0} 即拒绝新申请。
     */
    int countByOrderNoAndStatus(@Param("orderNo") String orderNo,
                                @Param("refundStatus") String refundStatus);

    /** 出网前留痕：请求次数 +1、状态置 {@code PROCESSING}、落本次请求报文快照。 */
    int markRequesting(@Param("refundOrderNo") String refundOrderNo,
                       @Param("txnDate") String txnDate,
                       @Param("requestBody") String requestBody);

    /** 回写支付中心退款受理结果，我方应答码与支付中心应答码分列存放。 */
    int updateRequestResult(AlipayRefundTxnDetail record);
}
