package com.chinasofti.huateng.dailyticket.service;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.github.pagehelper.PageInfo;

/**
 * 日票业务服务。
 */
public interface DailyTicketService {
    /**
     * 日票下单，生成日票订单号。
     */
    DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request);

    /**
     * IF8A-70 旅游票下单。生成聚合主单及其内含的日票子单。
     */
    TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request);

    /**
     * 日票支付，转换支付场景并调用支付服务。
     */
    DailyTicketPayResult requestPay(DailyTicketPayReqDTO request);

    /**
     * 查询日票支付结果。
     */
    DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request);

    /** 运营页面主动查询支付平台结果并同步本地订单状态。 */
    DailyTicketPayQueryResult queryPayTicket(DailyTicketOrderNoReqDTO request);

    /**
     * 日票退款。未激活可直接退款，已激活未使用后续应进入核验退款流程。
     */
    DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 查询已提交到支付平台的日票退款结果。 */
    DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 使用原退款单号重新提交支付平台退款请求。 */
    DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request);

    /**
     * 重提交：支付平台从未受理过的退款单（{@code PLATFORM_REFUND_NO IS NULL}）重新发起退款。
     *
     * <p>与 {@link #retryRefundTicket} 的分工是**互斥的，不要混用**：
     * {@code retryRefundTicket} 先查再重发，前提是支付平台已建过退款单、查得到结论；
     * 而对端从未受理时 {@code refundQuery} 永远查不到东西，那条链路会一直卡在
     * 「支付平台退款单号缺失，无法执行双字段退款查询」，退款能力永久丧失。
     * 本方法就是补这个分支：<b>对端没受理过 ⇒ 重发是安全的，不会重复退款</b>。</p>
     *
     * <p>它也是 {@code REFUND_TYPE='01'}（核验退款）唯一的出口 —— {@code WAIT_VERIFY}
     * 在本模块内**只被写入、从无任何代码读取**，核销观察期满后没有任何驱动方，
     * 因此对已过 {@code VERIFY_AFTER_TIME} 的核验单放行重提交；观察期内仍拒绝。</p>
     */
    DailyTicketRefundResult resubmitRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 运营页面分页查询日票订单及退款、票实例摘要。 */
    ResultVO<PageInfo<DailyTicketRefundOrderView>> pageRefundOrders(DailyTicketRefundOrderQuery query);

    /** 运营页面分页查询日票退款记录。 */
    ResultVO<PageInfo<DailyTicketRefundView>> pageRefundRecords(DailyTicketRefundQuery query);

    /**
     * 取消未支付日票订单。
     */
    DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request);

    /**
     * 激活日票并生成票实例。
     */
    DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request);

    /**
     * 标记日票已使用并通知ACC。
     */
    DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request);

    /**
     * 处理支付结果回调。
     */
    DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request);

    /**
     * 查询日票票实例信息（ticketCode、actualTimes）。
     */
    QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request);

    /**
     * 按日票票号查购票支付信息（{@code payTradeOrderNo} / {@code payOrderNoDate} / {@code payChannelCode}）。
     *
     * <p>只服务 IF8A-05 / IF8A-34 的日票免扣费单：那趟行程不扣费、{@code PAY_TXN_DETAIL} 里没有行，
     * 三个支付字段本来只能返空串，改由本方法从购票订单取真值回填。</p>
     *
     * <p><b>与 {@link #queryDailyTicketInfo} 分开是有意的，NEVER 合并</b>：后者服务 IF1A-01 闸机热路径，
     * 入参是 {@code orderNo} / {@code cardId}、出参只有计次相关字段；把两者塞进一个方法会让闸机
     * 每次检票都多 join 一次订单表。</p>
     *
     * <p>查不到实例或订单时 <b>MUST 返 {@code 0000} 且三个字段留空</b>，NEVER 返失败码 ——
     * 调用方是详情页的富化步骤，拿不到就保持原有空串输出，不该让整个 IF8A-34 失败。</p>
     */
    QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(QueryDailyTicketPayInfoReqDTO request);

    /**
     * 日票进站校验（闸机入口调用）。
     * <p>校验规则：
     * <ul>
     *   <li>有效期校验：当前时间在 countingStart ~ countingEnd 范围内</li>
     *   <li>未出站校验：存在未完成出站的票实例</li>
     *   <li>计次票次数检查：次数为0则拒绝进站（不扣减，扣减在出站时执行）</li>
     * </ul>
     *
     * @param cardNum 卡号（对应 DAILY_TICKET_INSTANCE.CARD_NUM）
     * @return retCode=0000 通过；否则 retMsg 携带拒绝原因
     */
    DailyTicketBaseResult validateEntryCheck(String cardNum);

    /**
     * 日票出站处理（闸机出站时调用）。
     * <p>处理规则：
     * <ul>
     *   <li>计次票扣减一次可用次数（actualTimes - 1），次数≤0则保持0</li>
     *   <li>标记 TICKET_STATUS = USED，记录首次使用时间、结束时间</li>
     *   <li>INSERT 一条 DAILY_TICKET_USAGE_LOG 做扣次明细（UK_DTUL_ORDER 幂等）</li>
     * </ul>
     *
     * @param cardNum 卡号
     * @param countingEnd 有效期截止时间（毫秒时间戳）
     * @return 处理结果
     */
    DailyTicketBaseResult markUsed(String cardNum, Long countingEnd);

    /**
     * 日票出站处理（扩展版，携带行程关联信息用于扣次明细记录）。
     *
     * @param cardNum 卡号
     * @param countingEnd 有效期截止时间（毫秒时间戳）
     * @param orderNo 关联 GATE_TXN_PAY.ORDER_NO
     * @param inStation 进站编码
     * @param outStation 出站编码
     * @return 处理结果
     */
    DailyTicketBaseResult markUsed(String cardNum, Long countingEnd,
                                   String orderNo, String inStation, String outStation);

    /**
     * 查询某张日票的扣次使用明细（按创建时间倒序）。
     *
     * @param cardNum 日票虚拟卡号
     * @return 扣次明细列表（retCode=0000 时 data 有值）
     */
    DailyTicketBaseResult queryUsageLog(String cardNum);
}
