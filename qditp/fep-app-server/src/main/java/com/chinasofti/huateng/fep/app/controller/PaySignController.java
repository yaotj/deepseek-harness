package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.fep.app.service.PaySignAppService;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultResult;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 支付签约相关接口入口。
 *
 * <p>所有接口透传到 pay-sign-server。</p>
 */
@RestController
public class PaySignController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(PaySignController.class);

    private final PaySignAppService paySignAppService;

    public PaySignController(PaySignAppService paySignAppService) {
        this.paySignAppService = paySignAppService;
    }

    /**
     * IF8A-19 请求支付（通用），仅内部 `/ci/app/requestPay` 一条路径。
     *
     * <p><b>NEVER 再挂 {@code /app/payment/requestPay}</b>：接口规范 R6 中该地址属于
     * if8a_61 日票支付，已归还 {@code AppDailyTicketController}。挂在此处会让日票支付请求
     * 透传到 pay-sign，因缺 amount / subject / body / cardId / cardType 被
     * {@code PaySignWorkflow.validateRequestPay} 拦为 {@code retCode=8001 amount不能为空}
     * （2026-09-09 实测，订单 0E202609091941230001）。</p>
     */
    @PostMapping({"/ci/app/requestPay"})
    public RequestPayResult requestPay(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-19 请求支付, 请求参数: {}", request);
        RequestPayReqDTO dto = parseBizData(request, RequestPayReqDTO.class);
        if (dto.getScene() == null) {
            JSONObject bizData = JSON.parseObject(request.getBizData());
            if (bizData.containsKey("channelType")) {
                dto.setScene(bizData.getString("channelType"));
            }
        }
        if (dto.getIndustryType() == null) {
            dto.setIndustryType("1");
        }
        return paySignAppService.requestPay(dto);
    }

    @PostMapping({"/ci/app/requestSignInfo"})
    public RequestSignInfoResult requestSignInfo(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-16 请求签约信息, 请求参数: {}", request);
        RequestSignInfoReqDTO dto = parseBizData(request, RequestSignInfoReqDTO.class);
        return paySignAppService.requestSignInfo(dto);
    }

    /**
     * IF8A-05 支付成功回调（外部支付平台 -> fep-app -> pay-sign-server）。
     * 兼容 form 和 JSON 两种请求体格式。
     */
    @PostMapping("/ci/app/receivePayResult")
    public PaySignCallbackResult receivePayResult(@RequestBody String requestBody) {
        log.info("IF8A-05 支付回调, 请求参数: {}", requestBody);
        ReceivePayResultReqDTO dto = parseCallbackBody(requestBody, ReceivePayResultReqDTO.class);
        if (dto == null) {
            dto = parseBizData(JSON.parseObject(requestBody, ItpCommonFormRequest.class), ReceivePayResultReqDTO.class);
        }
        return paySignAppService.receivePayResult(dto);
    }

    /**
     * IF8A-06 请求解约。
     */
    @PostMapping({"/ci/app/requestTermination"})
    public RequestTerminationResult requestTermination(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-06 请求解约, 请求参数: {}", request);
        return paySignAppService.requestTermination(parseBizData(request, RequestTerminationReqDTO.class));
    }

    /**
     * IF8A-75 直接解绑支付方式。
     *
     * <p>与 IF8A-06 请求解约的区别：本接口立即向支付渠道发起解绑，不等账期结束的定时任务，
     * 用于用户长时间未登录需强制解除绑定关系的场景。</p>
     *
     * <p><b>{@code /userData/unbindAgreement} 是 APP 实际在调的路径</b>（2026-09-09 实测：
     * 销户后 APP 紧接着请求该路径，因未注册被全局异常处理器兜成 UUID retCode + HTTP 200，
     * APP 无法识别失败，导致用户 00522948 已注销但支付宝签约仍为 SIGNED、通道残留 ACTIVE）。
     * 三条路径同时保留，**NEVER** 删掉 {@code /userData/} 这条，除非 APP 侧确认已切换。</p>
     */
    @PostMapping({"/userData/unbindAgreement", "/ci/app/unbindAgreement", "/app/unbindAgreement"})
    public UnbindAgreementResult unbindAgreement(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-75 直接解绑支付方式, 请求参数: {}", request);
        return paySignAppService.unbindAgreement(parseBizData(request, UnbindAgreementReqDTO.class));
    }

    /**
     * IF8A-22 签约结果查询。
     */
    @PostMapping({"/ci/app/requestContractResult"})
    public RequestContractResultResult requestContractResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-22 签约结果查询, 请求参数: {}", request);
        return paySignAppService.requestContractResult(parseBizData(request, RequestContractResultReqDTO.class));
    }

    /**
     * 内部签约结果通知（pay-sign-server -> fep-app -> 外部支付平台）。
     * 兼容 form 和 JSON 两种请求体格式。
     */
    @PostMapping("/ci/app/receiveSignResult")
    public PaySignCallbackResult receiveSignResult(@RequestBody String requestBody) {
        log.info("IF8A-07 内部签约结果通知, 请求参数: {}", requestBody);
        ReceiveSignResultReqDTO dto = parseCallbackBody(requestBody, ReceiveSignResultReqDTO.class);
        if (dto == null) {
            dto = parseBizData(JSON.parseObject(requestBody, ItpCommonFormRequest.class), ReceiveSignResultReqDTO.class);
        }
        return paySignAppService.receiveSignResult(dto);
    }

    /**
     * 内部解约结果通知（pay-sign-server -> fep-app -> 外部支付平台）。
     * 兼容 form 和 JSON 两种请求体格式。
     *
     * <p>{@code /ci/app/receiveUnsignResult} 是支付中心侧使用的别名路径，语义与
     * {@code receiveTerminationResult} 完全一致，同样转发到 pay-sign-server 的
     * {@code /ci/app/receiveTerminationResult}。</p>
     */
    @PostMapping({"/ci/app/receiveTerminationResult", "/ci/app/receiveUnsignResult"})
    public PaySignCallbackResult receiveTerminationResult(@RequestBody String requestBody) {
        log.info("IF8A-10 内部解约结果通知, 请求参数: {}", requestBody);
        ReceiveTerminationResultReqDTO dto = parseCallbackBody(requestBody, ReceiveTerminationResultReqDTO.class);
        if (dto == null) {
            dto = parseBizData(JSON.parseObject(requestBody, ItpCommonFormRequest.class), ReceiveTerminationResultReqDTO.class);
        }
        return paySignAppService.receiveTerminationResult(dto);
    }

    /**
     * IF8A-05 批量查询支付明细（供 ticket-server 双源合并）。
     */
    @PostMapping("/ci/app/queryPayTxnBatch")
    public RequestPayTxnBatchResult queryPayTxnBatch(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-05 批量查询支付明细, 请求参数: {}", request);
        QueryPayTxnBatchReqDTO dto = parseBizData(request, QueryPayTxnBatchReqDTO.class);
        return paySignAppService.queryPayTxnBatch(dto);
    }
}
