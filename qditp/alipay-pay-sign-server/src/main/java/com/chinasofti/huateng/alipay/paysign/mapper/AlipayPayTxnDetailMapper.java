package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code ALIPAY_PAY_TXN_DETAIL} 数据访问。
 *
 * <p>语句形态逐条对齐 pay-sign-server 的 {@code PayTxnDetailMapper}，外加本模块 1.1.21
 * 在 {@code payQuery} 上修过的那条非终态白名单（{@link #updatePayQueryResultIfNotSuccess}）。
 * 每条语句的设计约束写在 {@code AlipayPayTxnDetailMapper.xml} 的注释里，改 SQL 前 MUST 先读。</p>
 *
 * <p>本接口目前**没有业务调用方**：新表与链路接线分两轮做，本轮只落表与数据访问层。
 * 接线时的调用顺序 MUST 是「① insert + markRequesting 落库并提交 → ② 无事务调支付中心
 * → ③ updateRequestResult 回写」，即先留痕、再出网、后回写；
 * <b>NEVER 把这三步包进同一个 {@code @Transactional}</b>（AGENTS.md §5.2 那条 2026-08-26 生产事故）。</p>
 */
@Mapper
public interface AlipayPayTxnDetailMapper {

    int insert(AlipayPayTxnDetail record);

    AlipayPayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);

    List<AlipayPayTxnDetail> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    AlipayPayTxnDetail selectByEntryId(@Param("entryId") String entryId);

    AlipayPayTxnDetail selectByExitId(@Param("exitId") String exitId);

    /**
     * 出网前留痕：请求次数 +1、状态压回 {@code PROCESSING}、记首次与最近请求时间。
     * MUST 在调支付中心**之前**执行并提交，否则远端已受理、本地无任何凭据。
     */
    int markRequesting(@Param("orderNo") String orderNo);

    /** 回写支付中心同步应答（受理结果），不带状态机 —— 本方就是这一步的唯一写入者。 */
    int updateRequestResult(AlipayPayTxnDetail record);

    /**
     * 支付回调回写，带「非终态」状态白名单。
     *
     * <p>影响 0 行即「该单已是 {@code SUCCESS} 终态」，调用方 MUST 把它判为重推并返成功，
     * <b>NEVER 直接回非 0000</b>，否则状态机自己会造出一个新的重推循环。</p>
     */
    int updatePayCallback(AlipayPayTxnDetail record);

    /**
     * 支付结果<b>查询</b>接口的回写，WHERE 额外要求当前状态不是 {@code SUCCESS}。
     *
     * <p>与 {@link #updatePayCallback} 的区别只在语义：{@code payQuery} 是查询，
     * <b>NEVER 允许它把已 {@code SUCCESS} 的订单改写成 {@code FAIL}</b> ——
     * {@code PAY_STATUS} 正是 {@link #countUnsettledByCardId} 判欠费的依据，
     * 一旦被「支付中心暂时查不通」打成 FAIL，就等于把乘客算成欠费。</p>
     *
     * <p>影响 0 行有两种含义，调用方 MUST 回读区分：本地已是目标状态（幂等），
     * 或本地已 {@code SUCCESS} 而支付中心说失败（口径冲突，只告警等人工）。</p>
     */
    int updatePayQueryResultIfNotSuccess(AlipayPayTxnDetail record);

    /**
     * 按 {@code ALIPAY_REFUND_TXN_DETAIL} 重算本单的已退金额与退款状态。
     *
     * <p>只传 {@code orderNo}：金额与状态全由 SQL 从明细表汇总，
     * <b>NEVER 由调用方传增量</b> —— 入参没有幂等键，传增量意味着重复执行会重复累加，
     * 而这一列又是「可退金额 = 已付 - 已退」的判据，虚高后真实退款会被误拒且账面无法自愈。</p>
     */
    int updateRefundSummary(@Param("orderNo") String orderNo);

    /**
     * 运营后台分页查询。
     *
     * <p>{@code offset} 是<b>已跳过的行数</b>、{@code pageSize} 是<b>每页行数</b>。
     * 旧 {@code AlipayPayLogMapper.selectAlipayPayLogList} 的谓词是
     * {@code rn > offset AND rn <= limit}，把 limit 当成了行号上界，
     * 于是第 2 页 {@code offset=10, limit=10} 恒返 0 行（只有第 1 页对）。
     * 本方法的 SQL 用 {@code rn > offset AND rn <= offset + pageSize}，
     * <b>NEVER 回退成传行号上界</b>。</p>
     */
    List<AlipayPayTxnDetail> selectPagedList(@Param("thirdUserId") String thirdUserId,
                                            @Param("cardId") String cardId,
                                            @Param("debitRequestResult") String debitRequestResult,
                                            @Param("invoice") String invoice,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);

    int countPagedList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardId") String cardId,
                       @Param("debitRequestResult") String debitRequestResult,
                       @Param("invoice") String invoice,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate);

    /**
     * 按卡号统计未结清订单，供 blacklist-server 盘点该卡欠费是否结清。
     *
     * <p>口径：{@code PAY_STATUS} 非 {@code SUCCESS} 即未结清。显式兼容 NULL ——
     * Oracle 三值逻辑下 {@code PAY_STATUS != 'SUCCESS'} 对 NULL 不成立，漏掉会把脏数据判成已结清。</p>
     */
    int countUnsettledByCardId(@Param("cardId") String cardId);
}
