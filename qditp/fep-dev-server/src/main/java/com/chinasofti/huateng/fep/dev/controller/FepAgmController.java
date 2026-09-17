package com.chinasofti.huateng.fep.dev.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.model.DeviceHeartbeatRespDTO;
import com.chinasofti.huateng.fep.dev.model.NotifyVerifyResultAckDTO;
import com.chinasofti.huateng.fep.dev.model.NotifyVerifyResultDeviceReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListRespDTO;
import com.chinasofti.huateng.fep.dev.gate.GateTransactionHandler;
import com.chinasofti.huateng.fep.dev.keysync.KeySyncHandler;
import com.chinasofti.huateng.fep.dev.qrcode.QrCodeStatusHandler;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/** AGM 设备接口前置入口。 */
@RestController
@RequestMapping("/ci/agm")
public class FepAgmController {
    private static final Logger log = LoggerFactory.getLogger(FepAgmController.class);

    private final GateTransactionHandler gateTransactionHandler;
    private final KeySyncHandler keySyncHandler;
    private final QrCodeStatusHandler qrCodeStatusHandler;

    public FepAgmController(GateTransactionHandler gateTransactionHandler,
                            KeySyncHandler keySyncHandler,
                            QrCodeStatusHandler qrCodeStatusHandler) {
        this.gateTransactionHandler = gateTransactionHandler;
        this.keySyncHandler = keySyncHandler;
        this.qrCodeStatusHandler = qrCodeStatusHandler;
    }
    /** IF1A-01 闸机检票通知。 */
    @PostMapping("/notiVerifyResult")
    public NotifyVerifyResultAckDTO notifyVerifyResult(@ModelAttribute ItpCommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-01 闸机检票通知, deviceId={}", deviceId);
        if (!hasBizData(request)) {
            return invalidParam(NotifyVerifyResultAckDTO::new, "bizData不能为空");
        }

        NotifyVerifyResultDeviceReqDTO bizData =
                parseBizData(request, NotifyVerifyResultDeviceReqDTO.class, "IF1A-01 闸机检票通知");

        if (bizData == null) {
            return invalidParam(NotifyVerifyResultAckDTO::new, "bizData格式错误");
        }

        bizData.setDeviceId(deviceId);
        log.info("IF1A-01 闸机检票通知, deviceId={}, bizData={}", deviceId, request.getBizData());

        NotifyVerifyResultRespDTO response = gateTransactionHandler.notifyVerifyResult(bizData);
        log.info("IF1A-01 闸机检票通知, deviceId={}, 响应 retCode={}", deviceId, response.getRetCode());
        return notifyAck(response.getRetCode(), response.getRetMsg());
    }

    /** IF1A-02 密钥同步。 */
    @PostMapping("/requestSynKeyList")
    public RequestSynKeyListRespDTO requestSynKeyList(@ModelAttribute ItpCommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-02 密钥同步, deviceId={}", deviceId);
        if (!hasBizData(request)) {
            return invalidParam(RequestSynKeyListRespDTO::new, "bizData不能为空");
        }

        RequestSynKeyListReqDTO bizData = parseBizData(request, RequestSynKeyListReqDTO.class, "IF1A-02 密钥同步");
        if (bizData == null) {
            return invalidParam(RequestSynKeyListRespDTO::new, "bizData格式错误");
        }

        log.info("IF1A-02 密钥同步, deviceId={}, keyCount={}", deviceId,
                bizData.getKeyCurVerList() == null ? 0 : bizData.getKeyCurVerList().size());
        RequestSynKeyListRespDTO response = keySyncHandler.requestSynKeyList(bizData, deviceId, request.getBizData());
        log.info("IF1A-02 密钥同步完成, deviceId={}, retCode={}, keyVersionCount={}",
                deviceId, response.getRetCode(),
                response.getKeyCurVerList() == null ? 0 : response.getKeyCurVerList().size());
        return response;
    }
    /** IF1A-04 查询票卡状态。 */
    @PostMapping("/requestQrCodeStatus")
    public RequestQrCodeStatusRespDTO requestQrCodeStatus(@ModelAttribute ItpCommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-04 查询票卡状态, deviceId={}", deviceId);
        if (!hasBizData(request)) {
            return invalidParam(RequestQrCodeStatusRespDTO::new, "bizData不能为空");
        }

        RequestQrCodeStatusReqDTO bizData = parseBizData(request, RequestQrCodeStatusReqDTO.class, "IF1A-04 查询票卡状态");
        if (bizData == null) {
            return invalidParam(RequestQrCodeStatusRespDTO::new, "bizData格式错误");
        }

        log.info("IF1A-04 查询票卡状态, deviceId={}, bizData={}", deviceId, request.getBizData());
        RequestQrCodeStatusRespDTO response = qrCodeStatusHandler.requestQrCodeStatus(bizData);
        log.info("IF1A-04 查询票卡状态, 响应 retCode={}", response.getRetCode());
        return response;
    }

    /** IF1A-03 设备心跳。 */
    @PostMapping({"/deviceHeartbeat", "/notiDeviceHeard"})
    public DeviceHeartbeatRespDTO deviceHeartbeat(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF1A-03 设备心跳, deviceId={}", request == null ? null : request.getDeviceId());
        DeviceHeartbeatRespDTO response = new DeviceHeartbeatRespDTO();
        response.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(FepDevErrorCodeEnum.SUCCESS.getMessage());
        return response;
    }

    private boolean hasBizData(ItpCommonFormRequest request) {
        return request != null && StringUtils.hasText(request.getBizData());
    }

    /** 解析设备上送的 bizData。 */
    private <T> T parseBizData(ItpCommonFormRequest request, Class<T> clazz, String apiTag) {
        try {
            return JSON.parseObject(request.getBizData(), clazz);
        } catch (Exception e) {
            log.error("{}, bizData解析失败, deviceId={}, bizData={}",
                    apiTag, request.getDeviceId(), request.getBizData(), e);
            return null;
        }
    }

    /** 构造 INVALID_PARAM 响应。 */
    private <T extends CommonResult> T invalidParam(Supplier<T> factory, String retMsg) {
        T response = factory.get();
        response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(retMsg);
        return response;
    }

    private NotifyVerifyResultAckDTO notifyAck(String retCode, String retMsg) {
        NotifyVerifyResultAckDTO response = new NotifyVerifyResultAckDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
