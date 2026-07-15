package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

/**
 * IF2A-07 充值失败通知请求参数。
 */
@Data
public class TopupCardFailNotiReqDTO extends BaseRequestDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 票卡逻辑号。
     */
    private String ticketLogicNum;

    /**
     * 票卡物理号。
     */
    private String ticketPhysicsNum;

    /**
     * 充值状态：01-失败，02-存疑，03-取消。
     */
    private String topupStatus;

    /**
     * 故障时间格式 YYYYMMDDHH24mmss。
     */
    private String faultOccurDate;

    /**
     * 故障凭条号。
     */
    private String faultSlipSeq;

    /**
     * 错误代码。
     */
    private String errorCode;

    /**
     * 执行错误信息。
     */
    private String errorMessage;
}
