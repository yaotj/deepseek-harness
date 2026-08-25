package com.chinasofti.huateng.fep.dev.service.impl;

import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListRespDTO;
import com.chinasofti.huateng.fep.dev.service.DevService;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 设备请求处理服务默认实现（薄壳编排层）。
 * 只做参数校验和路由分发，具体业务逻辑由各 Handler 承担。
 */
@Service
public class DevServiceImpl implements DevService {
    private static final Logger log = LoggerFactory.getLogger(DevServiceImpl.class);

    @Autowired
    private GateTransactionHandler gateTransactionHandler;
    @Autowired
    private KeySyncHandler keySyncHandler;
    @Autowired
    private QrCodeStatusHandler qrCodeStatusHandler;

    @Override
    public RequestSynKeyListRespDTO requestSynKeyList(RequestSynKeyListReqDTO request, String deviceId, String requestBizData) {
        return keySyncHandler.requestSynKeyList(request, deviceId, requestBizData);
    }

    @Override
    public RequestQrCodeStatusRespDTO requestQrCodeStatus(RequestQrCodeStatusReqDTO request) {
        return qrCodeStatusHandler.requestQrCodeStatus(request);
    }

    @Override
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request) {
        return gateTransactionHandler.notifyVerifyResult(request);
    }
}
