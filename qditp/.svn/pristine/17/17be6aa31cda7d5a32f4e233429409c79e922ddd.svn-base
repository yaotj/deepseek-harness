package com.chinasofti.huateng.online.service;

import com.chinasofti.huateng.online.entity.QRCodeStatus;
import com.chinasofti.huateng.online.model.agm.AgmDtos;

/**
 * 扣费服务。
 * 当前作为 AGM 检票后的扣费决策与扣费调用占位服务，后续可替换为真实计费/支付能力。
 */
public interface DeductionService {
    /**
     * 根据交易类型与金额判断本次检票是否需要发起扣费。
     */
    boolean shouldDeduct(AgmDtos.NotiVerifyResultReqDTO request, QRCodeStatus qrCodeStatus);

    /**
     * 发起扣费。
     */
    void deduct(AgmDtos.NotiVerifyResultReqDTO request, QRCodeStatus qrCodeStatus);
}
