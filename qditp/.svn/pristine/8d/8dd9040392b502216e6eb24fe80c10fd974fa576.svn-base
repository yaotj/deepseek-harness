package com.chinasofti.huateng.online.service;

import com.chinasofti.huateng.online.entity.QRCodeStatus;
import com.chinasofti.huateng.online.model.agm.AgmDtos;

/**
 * 二维码票卡状态服务。
 * 负责统一维护 QR_CODE_STATUS 表，避免业务编排层直接操作状态表细节。
 */
public interface QRCodeStatusService {
    /**
     * 按 cardId 查询二维码状态，不存在时自动初始化一条默认记录。
     */
    QRCodeStatus getOrInitByCardId(String cardId);

    /**
     * 按 cardId 查询二维码状态。
     */
    QRCodeStatus findByCardId(String cardId);

    /**
     * 按 ITP 用户号查询二维码状态。
     */
    QRCodeStatus findByItpUserId(String itpUserId);

    /**
     * 保存二维码状态快照。
     */
    void save(QRCodeStatus qrCodeStatus);

    /**
     * 处理 AGM 检票通知并更新二维码状态。
     */
    QRCodeStatus handleAgmVerifyResult(AgmDtos.NotiVerifyResultReqDTO request, String deviceId);

    /**
     * 组装平台侧行业数据快照。
     */
    String buildIndustryData(QRCodeStatus qrCodeStatus);
}
