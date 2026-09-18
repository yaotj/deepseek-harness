package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code ALIPAY_PAY_TXN_DETAIL} 数据访问。
 *
 * <p><b>只有两条读语句，且都按 {@code orderNo}。</b>这是「行程列表 / 详情统一先查
 * {@code GATE_TXN_PAY} 拿主体、再按 {@code orderNo} 取各业务详情」这个编排的直接结果：
 * 列表的分页、按用户 / 卡号 / 卡类型 / 交易日区间过滤、排序，全部由主表侧的
 * {@code /ci/gateTxnPay/app/requestTransList} + {@code /countTransList} 负责，单笔走
 * {@code queryByOrderNo}；本表只做「补齐支付侧字段」这一件事，入口就是 {@link #selectByOrderNos}。
 *
 * <p>因此这里 <b>没有</b> 也 NEVER 加四类方法：
 * ① 任何分页 / 计数查询（{@code selectPagedList} / {@code countPagedList} 已删）；
 * ② 按 {@code THIRD_USER_ID} / {@code CARD_ID} 过滤的查询 —— 那两列已从表里删掉；
 * ③ 按 {@code ENTRY_ID} / {@code EXIT_ID} 反查（{@code selectByEntryId} / {@code selectByExitId}
 *    已删，对应的三个对外端点 {@code payLog/entryId}、{@code payLog/exitId}、
 *    {@code payLog/queryByTravelRecord} 一并废弃，全仓实测零调用方）；
 * ④ 判断「某张卡有没有欠费」—— 权威是 {@code GATE_TXN_PAY.DEBIT_STATUS}，MUST 由
 *    gate-txn-pay-server 回答。
 *
 * <p>接线顺序 MUST 是「① {@link #insert} + {@link #markRequesting} 落库并提交 → ② <b>无事务</b>
 * 调支付中心 → ③ {@link #updateRequestResult} 回写」，即先留痕、再出网、后回写；
 * NEVER 把这三步包进同一个 {@code @Transactional}。
 */
@Mapper
public interface AlipayPayTxnDetailMapper {

    /**
     * 首次落支付明细。并发下第二条会撞 {@code UK_APTD_ORDER}，调用方 MUST 沿
     * {@code getCause()} 链判完整性冲突后当「重推」处理，NEVER 只 catch 最外层
     * {@code DuplicateKeyException}（本模块已开 tracing，观测切面会换异常类型）。
     */
    int insert(AlipayPayTxnDetail record);

    AlipayPayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按主表给出的订单号批量补齐支付侧详情 —— 这是行程列表编排里本表的<b>唯一</b>入口。
     * 调用方 MUST 自己按 {@code orderNo} 建 Map 回填，NEVER 依赖返回顺序与入参顺序一致。
     */
    List<AlipayPayTxnDetail> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    /** 出网前留痕：请求次数 +1、状态压回 {@code PROCESSING}、记首次与最近请求时间。 */
    int markRequesting(@Param("orderNo") String orderNo);

    /** 回写支付中心同步应答（受理结果），不带状态机 —— 本方就是这一步的唯一写入者。 */
    int updateRequestResult(AlipayPayTxnDetail record);

    /** 支付回调回写，带「非终态」状态白名单；0 行即回调的幂等出口。 */
    int updatePayCallback(AlipayPayTxnDetail record);

    /** 支付结果查询接口的回写，WHERE 额外要求当前状态不是 {@code SUCCESS}。 */
    int updatePayQueryResultIfNotSuccess(AlipayPayTxnDetail record);

    /** 按 {@code ALIPAY_REFUND_TXN_DETAIL} 重算本单的已退金额与退款状态。 */
    int updateRefundSummary(@Param("orderNo") String orderNo);
}
