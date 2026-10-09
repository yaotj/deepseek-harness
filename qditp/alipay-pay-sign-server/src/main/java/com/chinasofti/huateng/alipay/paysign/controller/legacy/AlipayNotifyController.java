package com.chinasofti.huateng.alipay.paysign.controller.legacy;

import com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部约定的入向通知收口：黑名单状态变更通知、业务关闭结果通知。
 *
 * <p><b>2026-09-20 迁入 {@code controller.legacy} 子包</b>（迁移第 11 条，**按用户裁决**）：
 * 本类是历史形态的留存 —— 类级不写 {@code @RequestMapping}、两条方法各自写完整路径，
 * 与 {@code controller.sign} / {@code payment} / {@code paycenter} / {@code internal} 那套
 * 「类级前缀 + 方法级短路径」的形态不一致。<b>URL 逐字未变</b>，
 * Spring 的映射只看注解、与包路径无关，因此 {@code rpc/AlipayPaySignClient} 那两个包装方法不需要改一行。
 * 组件扫描根是启动类所在的 {@code ...alipay.paysign}，子包天然被扫到，
 * <b>NEVER 因为迁了包就去加 {@code @ComponentScan}</b>。
 *
 * <p><b>本类两条端点都有真实且在跑的调用方，NEVER 因为它在 `legacy` 包里就当成死代码删掉</b>：
 * <ul>
 *   <li>{@code blackListChange} ← blacklist-server {@code AlipayBlacklistNotifyPort}
 *       （经 {@code AlipayPaySignClient.notifyBlackListChange}）</li>
 *   <li>{@code closeResultForAlipay} ← fep-alipay-server {@code AlipayNotifyServiceImpl}
 *       （经 {@code AlipayPaySignClient.notifyCloseResult}），
 *       该端点 2026-09-18 才补齐，此前本服务没有 handler、链路是 404 被伪装成
 *       HTTP 200 + UUID retCode（见 ADR-D137）</li>
 * </ul>
 *
 * <p>两条都是本平台内部约定，**不在**支付中心网关契约 §5 的回调清单内；
 * 契约 §5 那两条（支付结果 / 退款结果）在 {@code controller/paycenter/PayCenterCallbackController}。
 *
 * <p><b>名字是入向、实质是出向代理</b>：两条 handler 收到内网推送后，同步转发给支付中心
 * （{@code service/impl/notify/PaymentNotifyAdapter} 是唯一出网处），<b>本服务不落库、不排队、不补偿</b>，
 * 失败只把错误码回给上游。黑名单方向靠上游 blacklist-server 的 {@code CHANNEL_SYNC_*} outbox 重推兜；
 * <b>销卡结果方向上游没有载体表，推失败即永久丢</b>（未闭合）。
 * <b>NEVER 在本类里加落库或重试</b> —— 要补偿得先在上游建载体表。
 *
 * <p><b>2026-09-21 起本类直接注 {@link PaymentNotifyAdapter}、不再经
 * {@code AlipayTripPaymentService}</b>（拆门面第 1 步）：那个门面同时横跨支付申请 / 查询 / 退款 /
 * 通知四类聚合，本类只需要通知这一类，经它转发纯属多一跳。适配器本身已是 public {@code @Service}、
 * 两条方法签名与返回逐字不变，<b>行为零变化、URL 与 Bean 名也一个字没动</b>。
 * <b>NEVER 退回注门面</b>：那等于把已经摘掉的跨聚合依赖重新接上。
 */
@RestController
public class AlipayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyController.class);
    private final PaymentNotifyAdapter paymentNotifyAdapter;

    public AlipayNotifyController(PaymentNotifyAdapter paymentNotifyAdapter) {
        this.paymentNotifyAdapter = paymentNotifyAdapter;
    }

    /**
     * 支付宝出行-黑名单状态变更通知（blacklist-server 推送）。
     */
    @PostMapping("/channel/notify/blackListChange")
    public AlipayCommonResponse notifyBlackListChange(@RequestBody AlipayBlackListNotifyReqDTO request) {
        log.info("收到黑名单变更通知: cardId={}", request != null ? request.getCardId() : null);
        AlipayCommonResponse response = paymentNotifyAdapter.notifyBlackListChange(request);
        log.info("黑名单变更通知响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }

    /**
     * 支付宝出行-业务关闭结果通知（fep-alipay-server 转发，收到后由本服务再通知支付中心）。
     *
     * <p>{@code result} 的 null 判 **NEVER 删** —— 下游签名是 {@code boolean}，拆箱前必须挡住 null。
     */
    @PostMapping("/channel/notify/closeResultForAlipay")
    public AlipayCommonResponse closeResultForAlipay(@RequestBody AlipayTripCloseResultReqDTO request) {
        String agreementNo = request != null ? request.getAgreementNo() : null;
        Boolean result = request != null ? request.getResult() : null;
        log.info("收到业务关闭结果通知: agreementNo={}, result={}", agreementNo, result);
        if (agreementNo == null || agreementNo.isEmpty() || result == null) {
            log.warn("业务关闭结果通知参数不完整: agreementNo={}, result={}", agreementNo, result);
            return AlipayCommonResponse.fail("协议号与关闭结果必填");
        }
        AlipayCommonResponse response = paymentNotifyAdapter.notifyCloseResult(agreementNo, result);
        log.info("业务关闭结果通知响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
