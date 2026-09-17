package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** IF8A-26 补款单与补款单明细数据访问（face-pay-server owner）。 */
@Mapper
public interface SupplementOrderMapper {

    /** 新增补款单，ORDER_NO 唯一索引兜底重复下单。 */
    int insert(SupplementOrder record);

    /** 按补款单号查询。 */
    SupplementOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /** 补款单状态推进：仅允许从 INIT / PROCESSING 更新为目标状态。 */
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

    /** 回写 PayCenter 预下单结果，并把补款单从 INIT 推进到 PROCESSING。 */
    int updatePrepayResult(@Param("orderNo") String orderNo,
                           @Param("payChannelCode") String payChannelCode,
                           @Param("merchantOrderNo") String merchantOrderNo,
                           @Param("paymentInfo") String paymentInfo);

    /** 捞超时未支付的补款单，仅限 INIT。 */
    List<SupplementOrder> selectTimeoutPending(@Param("timeoutMinutes") int timeoutMinutes,
                                               @Param("limit") int limit);

    /** 超时关单，白名单只放 INIT。 */
    int closeTimeoutOrder(@Param("orderNo") String orderNo, @Param("remark") String remark);

    /** 已作废（CLOSED）或已判失败（FAIL）却又收到钱时的强制销账，白名单只放这两个状态。 */
    int forceSuccessFromClosedOrFail(@Param("orderNo") String orderNo,
                                     @Param("remark") String remark,
                                     @Param("payChannelCode") String payChannelCode,
                                     @Param("merchantOrderNo") String merchantOrderNo,
                                     @Param("paymentInfo") String paymentInfo);

    /** 捞未终结的补款单（INIT / PROCESSING），供收敛任务回查 PayCenter 的支付结果。 */
    List<SupplementOrder> selectPendingOrders(@Param("limit") int limit,
                                             @Param("closedLookbackHours") int closedLookbackHours);
}
