package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import org.springframework.util.StringUtils;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;

/** 支付签约域**应答装配的唯一实现点**（2026-09-15 拆分批次 2）。 */
public final class PaySignResponses {

    private PaySignResponses() {
    }

    public static void fillError(BaseRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(BaseRespDTO response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(RequestPayResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(RequestPayResult response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(RequestRefundResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(RequestRefundResult response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    /** B 类：{@code requestSignInfo} 的应答模型只有 retCode / retMsg。 */
    public static void fillError(RequestSignInfoResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(RequestSignInfoResult response) {
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(PaySignCallbackResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    public static void fillSuccess(PaySignCallbackResult response) {
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    /** B 类：IF8A-75 直接解绑只有失败装配，成功分支由调用方按业务字段自行填。 */
    public static void fillError(UnbindAgreementResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    /** C 类：内部批处理应答用 resultCode / resultMsg，与 retCode 不是同一个字段。 */
    public static void fillError(CompensateNotifyRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setResultCode(errorCode.getCode());
        response.setResultMsg(msg);
    }

    public static void fillSuccess(CompensateNotifyRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(CheckFailedOrdersRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setResultCode(errorCode.getCode());
        response.setResultMsg(msg);
    }

    public static void fillSuccess(CheckFailedOrdersRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    public static void fillError(ProcessTerminationRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setResultCode(errorCode.getCode());
        response.setResultMsg(msg);
    }

    public static void fillSuccess(ProcessTerminationRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }
    /** IF8B 签约通知重发（{@code /internal/paySign/resendSignNotify}）。 */
    public static ResendSignNotifyRespDTO fillSuccess(ResendSignNotifyRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
        return response;
    }

    /** 同上；{@code notified} 恒置 false —— 失败分支 NEVER 报「已通知」。 */
    public static ResendSignNotifyRespDTO fillError(ResendSignNotifyRespDTO response,
                                                    PaySignErrorCodeEnum error, String msg) {
        response.setResultCode(error.getCode());
        response.setResultMsg(StringUtils.hasText(msg) ? msg : error.getMsg());
        response.setNotified(false);
        return response;
    }
    /** IF8A-22 签约结果查询的成功应答：一次把 {@code 0000} 与 4 个签约字段填齐。 */
    public static void fillContractResult(RequestContractResultRespDTO response, PaySignInfo signInfo,
                                          String defaultStatus) {
        fillSuccess(response);
        response.setStatus(PaySignValues.defaultString(signInfo.getContractStatus(), defaultStatus));
        response.setPayUserId(signInfo.getPayAccountId());
        response.setPayAccountId(signInfo.getPayAccountId());
        response.setPayAgreementNo(signInfo.getPayAgreementNo());
    }
}
