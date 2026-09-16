package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayScene;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.support.F2fOrderNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TVM 拉码下单与 BOM 柜台售票下单（同一条 URL，{@code providerId} 分流）。
 *
 * <p><b>2026-09-16 拆分（P2）</b>：原类 616 行、依赖 9 个，同时管下单、查支付结果、过期收口三件事。
 * 现在只留下单，另两件分别在 {@link F2fTvmPayResultService}（{@code queryPayResult} /
 * {@code receivePayNotice} / {@code requestPayOrderDetail}）与
 * {@link F2fOrderExpireService}（{@code reconcileExpiredOrder} 等三个）。
 * <b>URL、retCode 族与响应键集一行未改</b>，方法体逐字搬迁。</p>
 *
 * <h2>三条不可违反的编排约束</h2>
 * <ol>
 *   <li><b>整个类不带 {@code @Transactional}</b>：链路里有支付中心调用，事务包住网络调用会把行锁
 *       持有时长拉长到对端响应时长（AGENTS.md §5.2 的 2026-08-26 生产事故）。每条 SQL 自动提交，
 *       顺序是「INSERT 订单 → 事务外调支付中心 → UPDATE 结果」。</li>
 *   <li><b>对端没答上来时 NEVER 把订单写成 PAY_FAILED</b>：钱可能已经扣了。订单留在
 *       {@code CREATED}，由 180 秒过期扫表或后续查询接口收口。旧实现在这里直接写 FAILED，是本次
 *       重写要修掉的行为。</li>
 *   <li><b>状态判断用白名单</b>：只有明确列举的状态才短路，其余一律去查支付中心。</li>
 * </ol>
 */
@Service
public class F2fTvmOrderService {

    private static final Logger log = LoggerFactory.getLogger(F2fTvmOrderService.class);

    /** 受理渠道：TVM。 */
    private static final String CHANNEL_TVM = "02";

    /**
     * 受理渠道：BOM，与 {@code F2fChannel.BOM} 同值。
     *
     * <p>BOM 卖单程票走的是<b>TVM 的 URL</b>（{@code /itptvm/ci/tvm/requestGenSjtOrder}），
     * 靠 {@code providerId=03} 分流，不是 {@code /itpbom} 下的接口——2026-09-10 双跑抓包实测确认。
     * 旧实现把这笔单写进 {@code TBL_BOM_ORDER_PAY} + {@code TBL_BOM_SALE_INFO} +
     * {@code TBL_TVM_ORDER_PAY_PRE} 三张表，且<b>没有任何一列存 {@code '03'}</b>，
     * 渠道信息只存在于路由分支里、落库后无从分辨。新表用 CHANNEL 记住它。</p>
     */
    private static final String CHANNEL_BOM = "03";

    /** 业务类型：购票。 */
    private static final String BIZ_BUY_TICKET = "01";

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();

    /**
     * 预下单被拒时的 CAS 前置白名单。
     *
     * <p>与 {@link F2fTvmPayResultService} / {@link F2fOrderExpireService} 里的同名常量是
     * 同一个来源（{@code F2fOrderStatus.PENDING}），拆分后各类各引一次、<b>不是三份字面量副本</b>。</p>
     */
    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    private static final String PAY_SUBJECT = "单程票购票";

    private static final String PAY_BODY = "地铁单程票";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPayCenterFlow payCenterFlow;

    private final int qrcodeExpireSeconds;

    /**
     * BOM 售票单的失效时长（秒）。
     *
     * <p><b>不能复用 {@code qrcodeExpireSeconds}（180 秒）</b>：BOM 售票没有二维码，
     * 下单后由售票员扫乘客付款码再调 {@code requestPayment}，中间是人工操作，
     * 180 秒内收不了口。但也 NEVER 留 null——APP 单已经踩过这个坑：
     * {@code EXPIRE_TMS} 为空的订单永远不被 {@code F2fOrderExpireJob} 扫到，
     * 未付款的单会永久停在 {@code CREATED}。折中给 30 分钟：足够柜台完成一笔，
     * 又能让弃单被自动收口成 {@code EXPIRED}。</p>
     */
    private final int bomSaleExpireSeconds;

    public F2fTvmOrderService(F2fOrderMapper orderMapper,
                              F2fPaymentMapper paymentMapper,
                              F2fOrderNoGenerator orderNoGenerator,
                              PayCenterMessageFactory messageFactory,
                              F2fPayCenterFlow payCenterFlow,
                              @Value("${f2f.order.qrcodeExpireSeconds:180}") int qrcodeExpireSeconds,
                              @Value("${f2f.order.bomSaleExpireSeconds:1800}") int bomSaleExpireSeconds) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.messageFactory = messageFactory;
        this.payCenterFlow = payCenterFlow;
        this.qrcodeExpireSeconds = qrcodeExpireSeconds;
        this.bomSaleExpireSeconds = bomSaleExpireSeconds;
    }

    /**
     * IF2A-01 拉码下单。
     *
     * <p>顺序：取号 → INSERT 订单（CREATED）→ {@code payType=0} 则本地出聚合码并返回；否则
     * 事务外调支付中心预下单 → 按结果落 {@code F2F_PAYMENT} 并推进订单状态。</p>
     */
    public JSONObject createSingleTicketOrder(RequestGenSjtOrderReqDTO request) {
        TicketAmount parsed = parseTicketAmount(request);
        if (parsed.invalid() != null) {
            return TvmResponses.genSjtOrderFail(DeviceRetCode.INVALID_PARAM, parsed.invalid());
        }
        long amount = parsed.total();

        String orderNo = orderNoGenerator.nextSingleTicketOrderNo();
        LocalDateTime now = LocalDateTime.now();
        orderMapper.insert(buildOrder(orderNo, request, CHANNEL_TVM, parsed,
                now.plusSeconds(qrcodeExpireSeconds), now));
        log.info("拉码下单已落库, orderNo={}, amount={}, deviceId={}", orderNo, amount, request.getDeviceId());

        if ("0".equals(request.getPayType())) {
            String payUrl = messageFactory.buildAggregateCodePayUrl(orderNo);
            paymentMapper.insert(buildPayment(orderNo, amount, request.getPayType(), "INIT", payUrl, null, now));
            log.info("payType=0 走本地聚合码, orderNo={}", orderNo);
            return TvmResponses.genSjtOrderSuccess(orderNo, payUrl);
        }
        return preOrderAtPayCenter(orderNo, amount, request.getPayType(), now);
    }

    /**
     * BOM 售票下单（同一条 URL，{@code providerId=03} 分流）。
     *
     * <p><b>只落订单、不碰支付中心、不出二维码</b>，返回体只有 {@code orderNo}——
     * 逐字对齐旧 {@code BomOrderServiceImpl.requestBomSaleOrder}：它按顺序 INSERT
     * 三张表后就 {@code BomOrderResult.successData(orderNo)} 返回，全程无外呼。
     * 收款发生在下一步 {@code requestPayment}（扫乘客付款码，B 扫 C）。</p>
     *
     * <p><b>返回体是 BOM 族（{@code 0000} / {@code 8999}），不是 TVM 的 2xxx</b>，
     * 与同 URL 上的 {@code requestPayment} 一致；但<b>入参校验失败仍回 TVM 的 2002</b>——
     * 校验发生在 controller 分流之前，两个渠道共用一套文案，NEVER 为 BOM 单独改成 8003。</p>
     *
     * <p>不带 {@code @Transactional}：本方法只有一条 INSERT，事务无意义；
     * 旧实现三张表的 INSERT 也没有事务（{@code BomOrderServiceImpl:189} 无注解），
     * 且它连 {@code iBomTikcetInfo} 的返回值都不检查——三写两成也照样回成功。
     * 新表合一后这个不一致自然消失。</p>
     */
    public JSONObject createBomSaleOrder(RequestGenSjtOrderReqDTO request) {
        TicketAmount parsed = parseTicketAmount(request);
        if (parsed.invalid() != null) {
            return TvmResponses.genSjtOrderFail(DeviceRetCode.INVALID_PARAM, parsed.invalid());
        }
        String orderNo = orderNoGenerator.nextSingleTicketOrderNo();
        LocalDateTime now = LocalDateTime.now();
        orderMapper.insert(buildOrder(orderNo, request, CHANNEL_BOM, parsed,
                now.plusSeconds(bomSaleExpireSeconds), now));
        log.info("BOM 售票下单已落库, orderNo={}, amount={}, deviceId={}, entry={}, exit={}",
                orderNo, parsed.total(), request.getDeviceId(),
                request.getEntryStationCode(), request.getExitStationCode());
        return BomResponses.successOrderNo(orderNo);
    }

    /**
     * 票价与张数的解析结果。{@code invalid} 非空即表示入参不合法，其余字段无意义。
     *
     * <p>用一个字段带回文案而不是直接抛异常，是为了让 TVM 与 BOM 两条分支
     * <b>共用同一套校验但各自组装自己族的响应</b>，同时不改动既有文案。</p>
     */
    private record TicketAmount(long ticketPrice, int ticketNum, long total, String invalid) {
    }

    private TicketAmount parseTicketAmount(RequestGenSjtOrderReqDTO request) {
        long ticketPrice;
        int ticketNum;
        try {
            ticketPrice = Long.parseLong(request.getTicketPrice().trim());
            ticketNum = Integer.parseInt(request.getSingelTicketNum().trim());
        } catch (RuntimeException e) {
            log.warn("下单入参非数字, ticketPrice={}, singelTicketNum={}",
                    request.getTicketPrice(), request.getSingelTicketNum());
            return new TicketAmount(0L, 0, 0L, "ticketPrice或singelTicketNum不是合法数字");
        }
        if (ticketPrice <= 0 || ticketNum <= 0) {
            return new TicketAmount(0L, 0, 0L, "ticketPrice与singelTicketNum必须为正数");
        }
        return new TicketAmount(ticketPrice, ticketNum, ticketPrice * ticketNum, null);
    }

    /**
     * @param expireTms 失效时间：TVM 是二维码的 180 秒，BOM 是柜台窗口
     *                  {@code bomSaleExpireSeconds}。<b>两个渠道都 MUST 有值</b>，
     *                  否则 {@code F2fOrderExpireJob} 永远扫不到未付款的单。
     */
    private F2fOrder buildOrder(String orderNo, RequestGenSjtOrderReqDTO request, String channel,
                                TicketAmount parsed, LocalDateTime expireTms, LocalDateTime now) {
        F2fOrder order = new F2fOrder();
        order.setOrderNo(orderNo);
        order.setChannel(channel);
        order.setBizType(BIZ_BUY_TICKET);
        order.setOrderStatus(STATUS_CREATED);
        order.setOrderAmount(parsed.total());
        order.setDeviceId(request.getDeviceId());
        order.setTicketNum(parsed.ticketNum());
        order.setTicketPrice(parsed.ticketPrice());
        order.setSingleTicketType(request.getSingleTicketType());
        order.setEntryStationCode(request.getEntryStationCode());
        order.setExitStationCode(request.getExitStationCode());
        order.setActivateFlag("0");
        order.setExpireTms(expireTms);
        order.setCreateTms(now);
        order.setUpdateTms(now);
        return order;
    }

    /** 预下单：<b>本方法必须在事务外执行</b>，中间那次 {@code execute} 是网络调用。 */
    private JSONObject preOrderAtPayCenter(String orderNo, long amount, String payType, LocalDateTime now) {
        PayCenterRequest message = messageFactory.buildPayRequest(new PayCenterPayCommand(
                orderNo, PayScene.QRCODE, null, payType, amount, PAY_SUBJECT, PAY_BODY, null));
        paymentMapper.insert(buildPayment(orderNo, amount, payType, "INIT", null, message.getBizData(), now));

        F2fPayCenterFlow.Submitted submitted = payCenterFlow.submit(new F2fPayCenterFlow.SubmitSpec(
                orderNo, 1, message, "二维码串",
                new F2fPayCenterFlow.RejectTransition(PENDING, "支付中心预下单失败:"),
                false, "拉码下单"));
        return switch (submitted) {
            case F2fPayCenterFlow.Submitted.Accepted accepted ->
                    TvmResponses.genSjtOrderSuccess(orderNo, accepted.result().string("data"));
            // 被拒 / 状态不明对 TVM 是同一句话，区别只在订单侧动没动状态（已在 flow 内按策略处理）。
            case F2fPayCenterFlow.Submitted.Rejected ignored -> TvmResponses.genSjtOrderFail(DeviceRetCode.FAIL);
            case F2fPayCenterFlow.Submitted.Unknown ignored -> TvmResponses.genSjtOrderFail(DeviceRetCode.FAIL);
            // 拉码链路没开同步支付状态判定，构造上不可能收到；真收到说明 spec 被改错了，宁可炸也不能静默按失败回。
            case F2fPayCenterFlow.Submitted.SyncPaid ignored ->
                    throw new IllegalStateException("拉码链路不应收到同步支付成功, orderNo=" + orderNo);
        };
    }

    private F2fPayment buildPayment(String orderNo, long amount, String payType, String payStatus,
                                    String payUrl, String requestBody, LocalDateTime now) {
        F2fPayment payment = new F2fPayment();
        payment.setOrderNo(orderNo);
        payment.setAttemptNo(1);
        payment.setPayScene(PayScene.QRCODE.getCode());
        payment.setPayStatus(payStatus);
        payment.setPayAmount(amount);
        payment.setPayType(payType);
        payment.setPayUrl(payUrl);
        payment.setRequestBody(requestBody);
        payment.setRequestTms(now);
        payment.setCreateTms(now);
        payment.setUpdateTms(now);
        return payment;
    }

    /**
     * 冲突上报统一走 {@link F2fPayCenterFlow#warnIfConflict}，本类不再自留副本。
     *
     * <p>口径未变：<b>只告警、不改应答、不拒绝请求</b>，与过闸链路 CAS 的观察期一致
     * （{@code docs/domain/state-machines.md} §四）。要升级成「按库内真实状态应答」
     * <b>MUST 先看 WARN 频率与构成</b>，那是改对外行为，NEVER 顺手改掉。</p>
     *
     * <p>「二维码超时收口」里的 {@code markPaid} 仍自己接返回值并据此分流，不走
     * {@code markPaidAndReport}；那段代码 2026-09-16 起在
     * {@link F2fOrderExpireService#reconcileExpiredOrder}，<b>改它时 MUST 确认仍然接住了返回值</b>。</p>
     */
}
