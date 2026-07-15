package com.chinasofti.huateng.online.service.impl;

import com.chinasofti.huateng.online.entity.QRCodeStatus;
import com.chinasofti.huateng.online.model.agm.AgmDtos;
import com.chinasofti.huateng.online.service.DeductionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 扣费服务默认实现。
 * 当前先保留 AGM 检票后的扣费判定与调用占位，方便先把 online-server 编排接口跑通。
 */
@Service
public class DeductionServiceImpl implements DeductionService {
    private static final Logger log = LoggerFactory.getLogger(DeductionServiceImpl.class);

    @Override
    public boolean shouldDeduct(AgmDtos.NotiVerifyResultReqDTO request, QRCodeStatus qrCodeStatus) {
        Integer trxAmount = parseInteger(request == null ? null : request.getTrxAmount());
        if (trxAmount == null || trxAmount <= 0) {
            return false;
        }
        String trxType = request.getTrxType();
        return "02".equals(trxType) || "03".equals(trxType);
    }

    @Override
    public void deduct(AgmDtos.NotiVerifyResultReqDTO request, QRCodeStatus qrCodeStatus) {
        /*
         * TODO 接真实扣费服务:
         * 1. 根据 cardId/itpUserId/交易流水组装扣费请求；
         * 2. 调用专门的扣费或账户服务；
         * 3. 记录扣费结果并在失败时触发补扣/补偿流程。
         */
        log.info("TODO deduct after AGM verify, cardId={}, itpUserId={}, trxType={}, trxAmount={}",
                qrCodeStatus == null ? null : qrCodeStatus.getCardId(),
                qrCodeStatus == null ? null : qrCodeStatus.getItpUserId(),
                request == null ? null : request.getTrxType(),
                request == null ? null : request.getTrxAmount());
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
