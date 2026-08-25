package com.chinasofti.huateng.fep.dev.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.model.CommonFormRequest;
import com.chinasofti.huateng.fep.dev.model.DeviceHeartbeatRespDTO;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AGM 设备接口前置入口。
 */
@RestController
@RequestMapping("/ci/agm")
public class FepAgmController {
    private static final Logger log = LoggerFactory.getLogger(FepAgmController.class);

    @Autowired
    private DevService devService;

    /**
     * IF1A-01 闸机检票通知。
     */
    @PostMapping("/notiVerifyResult")
    public NotifyVerifyResultRespDTO notifyVerifyResult(@ModelAttribute CommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-01 闸机检票通知, deviceId={}", deviceId);
        if (request == null || !StringUtils.hasText(request.getBizData())) {
            return invalidNotifyParam("bizData不能为空");
        }

        NotifyVerifyResultReqDTO bizData;
        try {
            bizData = JSON.parseObject(request.getBizData(), NotifyVerifyResultReqDTO.class);
        } catch (Exception e) {
            log.error("IF1A-01 闸机检票通知, bizData解析失败, deviceId={}, bizData={}",
                    request.getDeviceId(), request.getBizData(), e);
            return invalidNotifyParam("bizData格式错误");
        }

        bizData.setDeviceId(request.getDeviceId());
        log.info("IF1A-01 闸机检票通知, deviceId={}, bizData={}", request.getDeviceId(), request.getBizData());

        if (!"000".equals(bizData.getHandleResultCode())) {
            log.warn("IF1A-01 闸机检票通知, 读写器返回非成功状态，跳过业务处理, "
                            + "deviceId={}, handleResultCode={}, cardId={}, trxType={}, handleDateTime={}",
                    request.getDeviceId(), bizData.getHandleResultCode(),
                    bizData.getCardId(), bizData.getTrxType(), bizData.getHandleDateTime());
            NotifyVerifyResultRespDTO errorResponse = new NotifyVerifyResultRespDTO();
            errorResponse.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());
            errorResponse.setRetMsg("接收成功");
            return errorResponse;
        }

        NotifyVerifyResultRespDTO response = devService.notifyVerifyResult(bizData);
        log.info("IF1A-01 闸机检票通知, deviceId={}, 响应 retCode={}", request.getDeviceId(), response.getRetCode());
        return response;
    }

    /**
     * IF1A-02 密钥同步。
     */
    @PostMapping("/requestSynKeyList")
    public RequestSynKeyListRespDTO requestSynKeyList(@ModelAttribute CommonFormRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-02 密钥同步, deviceId={}", deviceId);
        if (request == null || !StringUtils.hasText(request.getBizData())) {
            RequestSynKeyListRespDTO response = new RequestSynKeyListRespDTO();
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("bizData不能为空");
            return response;
        }

        RequestSynKeyListReqDTO bizData;
        try {
            bizData = JSON.parseObject(request.getBizData(), RequestSynKeyListReqDTO.class);
        } catch (Exception e) {
            log.error("IF1A-02 密钥同步, bizData解析失败, deviceId={}, bizData={}",
                    request.getDeviceId(), request.getBizData(), e);
            RequestSynKeyListRespDTO response = new RequestSynKeyListRespDTO();
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("bizData格式错误");
            return response;
        }

        log.info("IF1A-02 密钥同步, deviceId={}, keyCount={}", deviceId,
                bizData.getKeyCurVerList() == null ? 0 : bizData.getKeyCurVerList().size());
        RequestSynKeyListRespDTO response = devService.requestSynKeyList(bizData, deviceId, request.getBizData());
        log.info("IF1A-02 密钥同步完成, deviceId={}, retCode={}, keyVersionCount={}",
                deviceId, response.getRetCode(),
                response.getKeyCurVerList() == null ? 0 : response.getKeyCurVerList().size());
        return response;
    }

    /**
     * IF1A-04 查询票卡状态。
     */
    @PostMapping("/requestQrCodeStatus")
    public RequestQrCodeStatusRespDTO requestQrCodeStatus(@ModelAttribute CommonFormRequest request) {
        log.info("IF1A-04 查询票卡状态, deviceId={}", request == null ? null : request.getDeviceId());
        if (request == null || !StringUtils.hasText(request.getBizData())) {
            return invalidParam("bizData不能为空", null);
        }

        RequestQrCodeStatusReqDTO bizData;
        try {
            bizData = JSON.parseObject(request.getBizData(), RequestQrCodeStatusReqDTO.class);
        } catch (Exception e) {
            log.error("IF1A-04 查询票卡状态, bizData解析失败, deviceId={}, bizData={}",
                    request.getDeviceId(), request.getBizData(), e);
            return invalidParam("bizData格式错误", null);
        }

        log.info("IF1A-04 查询票卡状态, deviceId={}, bizData={}", request.getDeviceId(), request.getBizData());
        RequestQrCodeStatusRespDTO response = devService.requestQrCodeStatus(bizData);
        log.info("IF1A-04 查询票卡状态, 响应 retCode={}", response.getRetCode());
        return response;
    }

    /**
     * IF1A-03 设备心跳。
     *
     * <p>心跳接口只确认设备链路可达，不解析 bizData，不调用后端业务服务。</p>
     */
    @PostMapping({"/deviceHeartbeat", "/notiDeviceHeard"})
    public DeviceHeartbeatRespDTO deviceHeartbeat(@ModelAttribute CommonFormRequest request) {
        log.info("IF1A-03 设备心跳, deviceId={}", request == null ? null : request.getDeviceId());
        DeviceHeartbeatRespDTO response = new DeviceHeartbeatRespDTO();
        response.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(FepDevErrorCodeEnum.SUCCESS.getMessage());
        return response;
    }

    private RequestQrCodeStatusRespDTO invalidParam(String retMsg, RequestQrCodeStatusReqDTO request) {
        RequestQrCodeStatusRespDTO response = new RequestQrCodeStatusRespDTO();
        response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(retMsg);
        if (request != null) {
            response.setItpUserId(request.getItpUserId());
            response.setCardId(request.getCardId());
        }
        return response;
    }

    private NotifyVerifyResultRespDTO invalidNotifyParam(String retMsg) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(retMsg);
        return response;
    }
}
