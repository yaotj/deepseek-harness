package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.DailyTicketAppService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 日票接口入口。
 *
 * <p>本 Controller 只负责承接 APP 公共 FormData 报文、解析 bizData 并转发到 daily-ticket-server。
 * 同时支持 {@code /ci/app} 和 {@code /app} 两条路径。</p>
 *
 * <p><b>每个接口还额外注册 {@code /app/ticket/**} 别名</b>：APP 端把 {@code dailyTicket} 段写成了
 * {@code ticket}（2026-09-09 20:03 实测，{@code /app/ticket/updateTicket} 无映射，落到静态资源处理器
 * 报 {@code No static resource}，响应退化成全局异常处理器的 UUID retCode，日票激活失败）。
 * 与 {@link AppTicketController} 的 {@code /app/ticket/**} 别名风格一致，两边路径 **NEVER** 重名。</p>
 */
@RestController
public class AppDailyTicketController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppDailyTicketController.class);

    private final DailyTicketAppService dailyTicketAppService;

    public AppDailyTicketController(DailyTicketAppService dailyTicketAppService) {
        this.dailyTicketAppService = dailyTicketAppService;
    }

    @PostMapping({"/ci/app/dailyTicket/requestOrder", "/app/dailyTicket/requestOrder", "/app/requestCountingOrder",
            "/app/ticket/requestOrder"})
    public DailyTicketOrderResult requestOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-60 日票下单, request={}", request);
        return dailyTicketAppService.requestCountingOrder(parseBizData(request, DailyTicketOrderReqDTO.class));
    }

    /**
     * IF8A-70 旅游票下单。
     *
     * <p><b>{@code /app/ticket/requestTravelOrder} 是 APP 实际在调的地址</b>
     * （2026-09-10 14:26 生产日志实测，6 秒内重试 9 次，服务端无映射、落到静态资源处理器报
     * {@code No static resource app/ticket/requestTravelOrder}，响应退化成全局异常处理器的 UUID retCode）。
     * 与本 Controller 其余接口一致同时保留 {@code /ci/app/dailyTicket} 与 {@code /app/dailyTicket} 两条，
     * 扁平那条 **NEVER** 删。</p>
     */
    @PostMapping({"/ci/app/dailyTicket/requestTravelOrder", "/app/dailyTicket/requestTravelOrder",
            "/app/ticket/requestTravelOrder"})
    public TravelTicketOrderResult requestTravelOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-70 旅游票下单, request={}", request);
        return dailyTicketAppService.requestTravelOrder(parseBizData(request, TravelTicketOrderReqDTO.class));
    }

    /**
     * if8a_61 日票支付。
     *
     * <p><b>{@code /app/payment/requestPay} 是接口规范 R6 给定的地址，APP 实际在调这一条</b>
     * （2026-09-09 实测：该路径此前被 {@code PaySignController} 占为 IF8A-19 通用请求支付，
     * 日票支付被透传到 pay-sign，因缺 amount 报 {@code retCode=8001}，APP 显示
     * 「创建支付渠道订单失败」）。三条路径同时保留，**NEVER** 删掉扁平那条。</p>
     */
    @PostMapping({"/ci/app/dailyTicket/payment/requestPay", "/app/dailyTicket/payment/requestPay",
            "/app/payment/requestPay", "/app/ticket/payment/requestPay"})
    public DailyTicketPayResult pay(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-61 日票支付, request={}", request);
        return dailyTicketAppService.requestPay(parseBizData(request, DailyTicketPayReqDTO.class));
    }

    /** if8a_62 日票支付结果查询。{@code /app/payment/requestPayResult} 为规范给定地址。 */
    @PostMapping({"/ci/app/dailyTicket/payment/requestPayResult", "/app/dailyTicket/payment/requestPayResult",
            "/app/payment/requestPayResult", "/app/ticket/payment/requestPayResult"})
    public DailyTicketPayQueryResult queryPayResult(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-62 日票支付结果查询, request={}", request);
        return dailyTicketAppService.requestPayResult(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/payment/requestRefundTicket", "/app/dailyTicket/payment/requestRefundTicket",
            "/app/ticket/payment/requestRefundTicket"})
    public DailyTicketRefundResult requestRefund(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-64 日票退款, request={}", request);
        return dailyTicketAppService.requestRefundTicket(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/cancelOrder", "/app/dailyTicket/cancelOrder", "/app/ticket/cancelOrder"})
    public DailyTicketBaseResult cancelOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-65 日票取消订单, request={}", request);
        return dailyTicketAppService.cancelOrder(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/updateTicket", "/app/dailyTicket/updateTicket", "/app/ticket/updateTicket"})
    public DailyTicketBaseResult activateTicket(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-67 日票激活, request={}", request);
        return dailyTicketAppService.updateTicket(parseBizData(request, DailyTicketActivateReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/updateAndNotice", "/app/dailyTicket/updateAndNotice",
            "/app/ticket/updateAndNotice"})
    public DailyTicketBaseResult notifyAccUsed(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-71 通知 ACC 车票已使用, request={}", request);
        return dailyTicketAppService.updateAndNotice(parseBizData(request, DailyTicketUsedNoticeReqDTO.class));
    }

    /**
     * 日票支付回调（支付网关 -> fep-app -> daily-ticket-server）。
     *
     * <p>{@code /app/payment/receivePayResult} 是 daily-ticket-server 自己上报给网关的地址
     * （{@code daily-ticket-server/src/main/resources/application.properties:28} 的
     * {@code daily-ticket.pay.notify-url}），此前未在此注册，回调会被全局异常处理器兜成
     * UUID retCode，订单卡在 PAYING。改 notify-url 或删本路径 **MUST** 两边同步。</p>
     */
    @PostMapping({"/ci/app/dailyTicket/payment/receivePayResult", "/app/dailyTicket/payment/receivePayResult",
            "/app/payment/receivePayResult", "/app/ticket/payment/receivePayResult"})
    public DailyTicketBaseResult receivePayNotify(@RequestBody String requestBody) {
        log.info("日票支付回调原始报文={}", requestBody);
        DailyTicketBaseResult result = dailyTicketAppService.handlePayResultCallback(requestBody);
        log.info("日票支付回调处理结果={}", JSON.toJSONString(result));
        return result;
    }
}
