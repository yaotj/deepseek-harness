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

    public static void fillError(UnbindAgreementResult response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

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
    public static ResendSignNotifyRespDTO fillSuccess(ResendSignNotifyRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
        return response;
    }

    public static ResendSignNotifyRespDTO fillError(ResendSignNotifyRespDTO response,
                                                    PaySignErrorCodeEnum error, String msg) {
        response.setResultCode(error.getCode());
        response.setResultMsg(StringUtils.hasText(msg) ? msg : error.getMsg());
        response.setNotified(false);
        return response;
    }
    public static void fillContractResult(RequestContractResultRespDTO response, PaySignInfo signInfo,
                                          String defaultStatus) {
        fillSuccess(response);
        response.setStatus(PaySignValues.defaultString(signInfo.getContractStatus(), defaultStatus));
        response.setPayUserId(signInfo.getPayAccountId());
        response.setPayAccountId(signInfo.getPayAccountId());
        response.setPayAgreementNo(signInfo.getPayAgreementNo());
    }
}
