package com.chinasofti.huateng.collectpay.controller.page;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.service.AppOrderService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营端 APP 取票订单退款（**按指定金额**）。
 *
 * <p>存在的原因只有一个：`/ci/app/requestRefundTicket` 的退款金额取
 * `TBL_TVM_APP_ORDER.PAY_AMOUNT` 全额，对**已部分退款**的订单必然超额被支付中心拒。
 * 那条是 APP 对外契约（APP 只传 orderNo），**NEVER 给它加金额字段**，因此另开本入口。</p>
 *
 * <p>放在 `/page/**` 而不是 `/ci/**`：这是运营/清退用的内部操作，不属于 APP 契约，
 * 与同目录的 {@link FacePayOrderPageController} 同类。</p>
 *
 * <p>⚠️ <b>本端点没有鉴权</b>，与 collect-pay-server 现有全部入口一致（该模块无
 * spring-security、无全局拦截器兜底，`signType=00` 即免签）。这与 AGENTS.md §5.2
 * 「新增状态变更型接口 MUST 有鉴权与归属校验」**冲突**，属**有意为之的临时降级**：
 * 它比旧入口更危险 —— 旧入口只能按全额退，本入口能指定金额。**上线前 MUST 补鉴权**
 * （对齐 `ItpRequestSignVerifier`，NEVER 自造签名逻辑），或改由 web-admin 经 rpc 调用
 * 并只在内网暴露。已知的唯一防线是服务层的可退余额闸门，它挡的是「退多了」，
 * 挡不住「不该退的人来退」。</p>
 */
@Slf4j
@RestController
@RequestMapping("/page/app/orders")
public class AppOrderPageController {

    private final AppOrderService appOrderService;

    public AppOrderPageController(AppOrderService appOrderService) {
        this.appOrderService = appOrderService;
    }

    /**
     * 按指定金额给 APP 取票订单退款，用于补退剩余部分。
     *
     * <p>金额单位是**分**。可退余额与超退拦截在
     * {@link AppOrderService#refundByAmount} 内完成，本方法只做入参非空校验。</p>
     *
     * <p>⚠️ 无幂等：同一订单连调两次会退两次（退款操作手册铁律 2）。调用方 MUST 自己控制，
     * 中断后 MUST 先查 `TBL_APP_ORDER_REFUND` 再续跑，**NEVER 重放原列表**。</p>
     */
    @PostMapping("/{orderNo}/refund")
    public ResultVO<JSONObject> refundByAmount(@PathVariable String orderNo,
                                              @RequestBody(required = false) AppPartialRefundRequest request) {
        log.info("接收到运营端 APP 指定金额退款请求, orderNo={}, request={}", orderNo,
                request == null ? null : request.getRefundAmount());
        if (!StringUtils.hasText(orderNo)) {
            return ResultMapper.illegalParams("订单号不能为空");
        }
        if (request == null || request.getRefundAmount() == null) {
            return ResultMapper.illegalParams("退款金额不能为空");
        }
        if (request.getRefundAmount() <= 0) {
            return ResultMapper.illegalParams("退款金额必须为正整数（单位：分）");
        }

        JSONObject result = appOrderService.refundByAmount(orderNo.trim(), request.getRefundAmount());
        String retCode = result == null ? null : result.getString("retCode");
        return "0000".equals(retCode) ? ResultMapper.ok(result)
                : ResultMapper.error(result == null ? "退款服务未返回结果" : result.getString("retMsg"));
    }
}
