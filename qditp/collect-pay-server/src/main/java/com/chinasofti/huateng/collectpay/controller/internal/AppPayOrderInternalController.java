package com.chinasofti.huateng.collectpay.controller.internal;

import com.chinasofti.huateng.collectpay.service.AppPayOrderInternalService;
import com.chinasofti.huateng.model.collectpay.AppPayOrderCloseReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderQueryReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRegisterReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderResultRespDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code TBL_TVM_APP_ORDER} / {@code TBL_TVM_ORDER_PAY_PRE} 的对内写入入口。
 *
 * <p>把 gate-txn-pay-server 的跨域直写收回到 owner 侧（2026-09-14），
 * 字段口径与三条资损防线见 {@link AppPayOrderInternalService} 接口注释。</p>
 *
 * <p><b>【开发测试阶段：本接口当前无鉴权，上线前 MUST 恢复】</b>用户 2026-09-14 裁决
 * 「不加鉴权，照本模块现有 {@code /internal/recon} 的做法」。这是**状态变更型**接口，
 * 比 {@code /internal/recon} 更敏感：拿到任意订单号即可给他人建单、或关掉他人的待支付单。
 * 与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」冲突，属**有意为之的临时降级**。
 * <b>NEVER 拿「/internal/recon 也没加」当长期理由</b>：那批是只读导出与内部编排，
 * 本批是按订单号改他人的支付状态，敏感度不同级。</p>
 *
 * <p>恢复方式 MUST 对齐现有验签实现（{@code AccountRequestVerifier} / {@code ItpRequestSignVerifier}），
 * <b>NEVER 自造签名逻辑</b>；若走共享令牌，比较 MUST 用
 * {@link java.security.MessageDigest#isEqual} 做定长时间比较、令牌由 K8s Secret 注入。</p>
 */
@RestController
@RequestMapping("/internal/app-order")
public class AppPayOrderInternalController {

    private final AppPayOrderInternalService appPayOrderInternalService;

    public AppPayOrderInternalController(AppPayOrderInternalService appPayOrderInternalService) {
        this.appPayOrderInternalService = appPayOrderInternalService;
    }

    /** 登记订单行 + 支付前置单行，按 {@code orderNo} 幂等。 */
    @PostMapping("/register")
    public AppPayOrderRespDTO register(@RequestBody AppPayOrderRegisterReqDTO request) {
        return appPayOrderInternalService.register(request);
    }

    /** 关闭仍待支付的订单行，带 {@code PAY_STATUS='0'} 白名单，影响 0 行也返成功。 */
    @PostMapping("/close-unpaid")
    public AppPayOrderRespDTO closeUnpaid(@RequestBody AppPayOrderCloseReqDTO request) {
        return appPayOrderInternalService.closeUnpaid(request);
    }

    /** 回查支付结果；查不到时 {@code found=false}，不抛异常。 */
    @PostMapping("/pay-result")
    public AppPayOrderResultRespDTO payResult(@RequestBody AppPayOrderQueryReqDTO request) {
        return appPayOrderInternalService.queryPayResult(request == null ? null : request.getOrderNo());
    }
}
