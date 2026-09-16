package com.chinasofti.huateng.account.controller.ci.channel;

import com.chinasofti.huateng.account.service.AlipayTripRegistrationService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行-开卡申请（规范 3.69）在 account-server 侧的实现，<b>当前保留但不在链路上</b>。
 *
 * <p><b>权威实现是 alipay-account-server</b>（用户 2026-09-11 裁定：以 alipay-account 为准、旧代码保留不用）。
 * 真实链路是 fep-alipay-server {@code POST /channel/requestApplication}
 * → {@code AlipayApplicationServiceImpl} → {@code AlipayAccountClient}
 * → <b>alipay-account-server</b> 的同名端点，落 {@code ALIPAY_USER_INFO} + {@code ALIPAY_REG_LOG}。
 * 该模块的 {@code service.account.url} 指向的就是 alipay-account-server，不是本服务。</p>
 *
 * <p>本端点与 alipay-account-server 的端点<b>URL 完全相同</b>（{@code /channel/requestApplication}）、
 * 入参出参 DTO 也完全相同，但落的是 {@code USER_ITP_REG_INFO} + {@code USER_ITP_REG_LOG}、
 * 用 {@code selectActiveByThirdUserId} 查重。两套查重键互不相交，因此
 * <b>NEVER 把任何模块的 {@code service.account.url} 指到本服务来处理支付宝开卡</b>——
 * 那会让同一个 {@code thirdUserId} 在两边各开一次户、彼此看不见，属真实双写风险。</p>
 *
 * <p>全仓库 grep {@code accountClient.alipayTripRequestApplication} 零调用点（2026-09-11 核实），
 * 即本端点虽在跑但无上游会请求它。保留原因是甲方口径尚未最终确认，删除需另行裁决。</p>
 */
@RestController
public class FepAlipayTripRequestApplicationController {
    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripRequestApplicationController.class);

    private final AlipayTripRegistrationService alipayTripRegistrationService;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public FepAlipayTripRequestApplicationController(AlipayTripRegistrationService alipayTripRegistrationService) {
        this.alipayTripRegistrationService = alipayTripRegistrationService;
    }

    @PostMapping("/channel/requestApplication")
    public AlipayTripRequestApplicationRespDTO requestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        log.info("接收到支付宝出行-开卡申请报文: {}", request);
        return alipayTripRegistrationService.alipayTripRequestApplication(request);
    }
}
