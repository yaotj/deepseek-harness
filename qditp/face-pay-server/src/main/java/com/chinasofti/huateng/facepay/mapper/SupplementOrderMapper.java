package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * IF8A-26 补款单与补款单明细数据访问（face-pay-server owner）。
 *
 * <p>与原 gate-txn-pay 版本的差异：
 * CollectPay 链路的 outbox 四列读写方法（markSaleSyncSuccess/Failed/Rejected、
 * selectPendingSaleSync）已移除——PayCenter 直连架构下不需要。</p>
 */
@Mapper
public interface SupplementOrderMapper {

    /** 新增补款单，ORDER_NO 唯一索引兜底重复下单。 */
    int insert(SupplementOrder record);

    /** 按补款单号查询。 */
    SupplementOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 补款单状态推进：仅允许从 INIT / PROCESSING 更新为目标状态。
     *
     * <p>白名单而非黑名单：SUCCESS / FAIL / CLOSED 已是终态，重复回调 NEVER 改写。</p>
     */
    int updatePayStatusFromPending(@Param("orderNo") String orderNo,
                                   @Param("payStatus") String payStatus,
                                   @Param("remark") String remark);

    /** 新增补款单明细。 */
    int insertItem(SupplementOrderItem record);

    /** 查询补款单下的全部明细。 */
    List<SupplementOrderItem> selectItemsByOrderNo(@Param("orderNo") String orderNo);

    /** 更新单条明细的结清标记。 */
    int updateItemSettleStatus(@Param("orderNo") String orderNo,
                               @Param("origOrderNo") String origOrderNo,
                               @Param("settleStatus") String settleStatus,
                               @Param("remark") String remark);

    /**
     * 回写 PayCenter 预下单结果，并把补款单从 INIT 推进到 PROCESSING。
     *
     * <p>WHERE 只认 INIT，同一笔重复调预下单时第二次返回 0 行。上层收到 0 行
     * MUST 改为回放库里已有的 PAYMENT_INFO，NEVER 再向支付中心预下单一次。</p>
     */
    int updatePrepayResult(@Param("orderNo") String orderNo,
                           @Param("payChannelCode") String payChannelCode,
                           @Param("merchantOrderNo") String merchantOrderNo,
                           @Param("paymentInfo") String paymentInfo);

    /** 捞超时未支付的补款单，仅限 INIT。PROCESSING 已在 PayCenter 挂待支付单，不能单方面关单。 */
    List<SupplementOrder> selectTimeoutPending(@Param("timeoutMinutes") int timeoutMinutes,
                                               @Param("limit") int limit);

    /** 超时关单，白名单只放 INIT。 */
    int closeTimeoutOrder(@Param("orderNo") String orderNo, @Param("remark") String remark);

    /**
     * 已作废（CLOSED）或已判失败（FAIL）却又收到钱时的强制销账，白名单只放这两个状态。
     * 作废 / 关单 / 判失败与乘客付款之间存在毫秒级竞态，钱既然收了就 MUST 用来清欠费。
     * 三个渠道凭据参数 MUST 一起传，分两步写会留下「状态已 SUCCESS 但商户单号为空」的行。
     */
    int forceSuccessFromClosedOrFail(@Param("orderNo") String orderNo,
                                     @Param("remark") String remark,
                                     @Param("payChannelCode") String payChannelCode,
                                     @Param("merchantOrderNo") String merchantOrderNo,
                                     @Param("paymentInfo") String paymentInfo);

    /**
     * 捞未终结的补款单（INIT / PROCESSING），供收敛任务回查 PayCenter 的支付结果。
     * 还会把 closedLookbackHours 小时内的 CLOSED / FAIL 单一起捞回来，
     * 专为「作废/关单/判失败后乘客仍完成了支付」兜底。
     */
    List<SupplementOrder> selectPendingOrders(@Param("limit") int limit,
                                             @Param("closedLookbackHours") int closedLookbackHours);
}
