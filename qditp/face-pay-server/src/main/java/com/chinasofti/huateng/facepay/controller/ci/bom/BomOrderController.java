package com.chinasofti.huateng.facepay.controller.ci.bom;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.DeviceRequests;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.bom.NotiBusResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.NotiTopupResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.NotiUpdateHceDataReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestOrderResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestTicketRefundReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPaymentReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.service.F2fBomOrderService;
import com.chinasofti.huateng.facepay.service.F2fDeviceHeartbeatService;
import com.chinasofti.huateng.facepay.service.F2fHceService;
import com.chinasofti.huateng.facepay.service.F2fQrCodeStatusService;
import com.chinasofti.huateng.facepay.service.F2fScanPayService;
import com.chinasofti.huateng.facepay.service.F2fTicketIssueService;
import com.chinasofti.huateng.facepay.service.F2fTopupResultService;
import com.chinasofti.huateng.facepay.service.F2fTopupService;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/itpbom/ci/bom", "/itptvm/ci/bom"})
public class BomOrderController {

    private static final Logger log = LoggerFactory.getLogger(BomOrderController.class);

    private static final String CODE_INVALID_PARAM = "8003";

    private final F2fBomOrderService bomOrderService;

    private final F2fDeviceHeartbeatService heartbeatService;

    private final F2fScanPayService scanPayService;

    private final F2fTopupService topupService;

    private final F2fTopupResultService topupResultService;

    private final F2fHceService hceService;

    private final F2fTicketIssueService ticketIssueService;

    private final F2fQrCodeStatusService qrCodeStatusService;

    public BomOrderController(F2fBomOrderService bomOrderService,
                             F2fDeviceHeartbeatService heartbeatService,
                             F2fScanPayService scanPayService,
                             F2fTopupService topupService,
                             F2fTopupResultService topupResultService,
                             F2fHceService hceService,
                             F2fTicketIssueService ticketIssueService,
                             F2fQrCodeStatusService qrCodeStatusService) {
        this.bomOrderService = bomOrderService;
        this.heartbeatService = heartbeatService;
        this.scanPayService = scanPayService;
        this.topupService = topupService;
        this.topupResultService = topupResultService;
        this.hceService = hceService;
        this.ticketIssueService = ticketIssueService;
        this.qrCodeStatusService = qrCodeStatusService;
    }

    @PostMapping({"/notiDeviceHeard", "/deviceHeartbeat"})
    public JSONObject notiDeviceHeard(@ModelAttribute BaseDeviceRequest form) {
        log.debug("接收到 BOM 设备心跳, deviceId={}", form == null ? null : form.getDeviceId());
        if (form != null) {
            heartbeatService.recordHeartbeat(F2fChannel.BOM, form.getDeviceId(), null);
        }
        return BomResponses.success();
    }

    @PostMapping("/requestGenNoCashOrder")
    public JSONObject requestGenNoCashOrder(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 非现金收款下单请求, form={}", form);
        RequestGenNoCashOrderReqDTO request = DeviceRequests.unwrap(form, RequestGenNoCashOrderReqDTO.class);
        if (request == null) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "请求报文不能为空");
        }
        if (isBlank(request.getTransType())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "transType不能为空");
        }
        if (isBlank(request.getOperaterId())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "operaterId不能为空");
        }
        if (isBlank(request.getShiftId())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "shiftId不能为空");
        }
        if (isBlank(request.getTransAount())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "transAount不能为空");
        }
        if (isBlank(request.getBomOptSeq())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "bomOptSeq不能为空");
        }
        if (isBlank(request.getDeviceId())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "deviceId不能为空");
        }
        return bomOrderService.createNoCashOrder(request);
    }

    @PostMapping("/requestPayment")
    public JSONObject requestPayment(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 扫码支付请求, form={}", form);
        RequestPaymentReqDTO request = DeviceRequests.unwrap(form, RequestPaymentReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.paymentResultFail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getPaymentVendor())) {
            return BomResponses.paymentResultFail(CODE_INVALID_PARAM, "paymentVendor不能为空");
        }
        return scanPayService.requestPayment(request);
    }

    @PostMapping({"/requestGetPayResult", "/requestPayResult"})
    public JSONObject requestGetPayResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 查询支付结果请求, form={}", form);
        RequestPayResultReqDTO request = DeviceRequests.unwrap(form, RequestPayResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.paymentResultFail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        return scanPayService.queryPayResult(request.getOrderNo());
    }

    @PostMapping("/notiBusResult")
    public JSONObject notiBusResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 业务操作结果通知, form={}", form);
        NotiBusResultReqDTO request = DeviceRequests.unwrap(form, NotiBusResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getOptResult())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "optResult不能为空");
        }
        return bomOrderService.receiveBusResult(request);
    }

    @PostMapping("/notiTopupResult")
    public JSONObject notiTopupResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 充值结果通知, form={}", form);
        NotiTopupResultReqDTO request = DeviceRequests.unwrap(form, NotiTopupResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getTopupStatus())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "topupStatus不能为空");
        }
        return topupResultService.receiveBomTopupResult(request.getOrderNo(), request.getTopupStatus(),
                request.getDeviceId(), request.needRefund(), request.toString());
    }

    @PostMapping("/requestOrderResult")
    public JSONObject requestOrderResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到单程票交易查询请求, form={}", form);
        RequestOrderResultReqDTO request = DeviceRequests.unwrap(form, RequestOrderResultReqDTO.class);
        if (request == null || isBlank(request.getTicketLogicNum())) {
            return BomResponses.orderResultFail(CODE_INVALID_PARAM, "ticketLogicNum不能为空");
        }
        if (isBlank(request.getTransDate())) {
            return BomResponses.orderResultFail(CODE_INVALID_PARAM, "transDate不能为空");
        }
        return bomOrderService.requestOrderResult(request);
    }

    @PostMapping("/requestTicketRefund")
    public JSONObject requestTicketRefund(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到单程票退款请求, form={}", form);
        RequestTicketRefundReqDTO request = DeviceRequests.unwrap(form, RequestTicketRefundReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getTicketLogicNum())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "ticketLogicNum不能为空");
        }
        if (isBlank(request.getTransAmount())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "transAmount不能为空");
        }
        if (isBlank(request.getTransType())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "transType不能为空");
        }
        return bomOrderService.requestTicketRefund(request);
    }

    @PostMapping("/requestCardDataAnalyse")
    public JSONObject requestCardDataAnalyse(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到票卡分析请求, form={}", form);
        RequestCardDataAnalyseReqDTO request = DeviceRequests.unwrap(form, RequestCardDataAnalyseReqDTO.class);
        if (request == null || isBlank(request.getCardId())) {
            return BomResponses.cardDataAnalyseFail(CODE_INVALID_PARAM, "cardId不能为空");
        }
        if (isBlank(request.getUpdateType())) {
            return BomResponses.cardDataAnalyseFail(CODE_INVALID_PARAM, "updateType不能为空");
        }
        return hceService.requestCardDataAnalyse(request);
    }

    @PostMapping("/requestUpdateCardData")
    public JSONObject requestUpdateCardData(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到票卡更新请求, form={}", form);
        RequestCardDataUpdateReqDTO request = DeviceRequests.unwrap(form, RequestCardDataUpdateReqDTO.class);
        if (request == null || isBlank(request.getCardId())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "cardId不能为空");
        }
        if (isBlank(request.getAdviceOpt())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "adviceOpt不能为空");
        }
        if (isBlank(request.getUpdateStationCode())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "updateStationCode不能为空");
        }
        if (isBlank(request.getOptDate())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "optDate不能为空");
        }
        return hceService.requestUpdateCardData(request);
    }

    @PostMapping("/notiUpdateHceData")
    public JSONObject notiUpdateHceData(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 HCE 票卡更新结果通知, deviceId={}", form == null ? null : form.getDeviceId());
        NotiUpdateHceDataReqDTO request = DeviceRequests.unwrap(form, NotiUpdateHceDataReqDTO.class);
        if (request == null || isBlank(request.getCardId())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "cardId不能为空");
        }
        if (isBlank(request.getHceData())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "hceData不能为空");
        }
        return hceService.receiveHceUpdateResult(request);
    }

    @PostMapping("/notiTakeTicketResult")
    public JSONObject notiTakeTicketResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到出票成功结果上报(BOM 前缀), form={}", form);
        NotiTakeTicketResultReqDTO request = DeviceRequests.unwrap(form, NotiTakeTicketResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        }
        return ticketIssueService.receiveTakeTicketResult(request, channelOf(request.getProviderId()));
    }

    @PostMapping("/notiTakeTicketFailResult")
    public JSONObject notiTakeTicketFailResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到出票失败结果上报(BOM 前缀), form={}", form);
        NotiTakeTicketFailResultReqDTO request =
                DeviceRequests.unwrap(form, NotiTakeTicketFailResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        }
        return ticketIssueService.receiveTakeTicketFailResult(request, channelOf(request.getProviderId()));
    }

    @PostMapping("/requestQrCodeStatus")
    public JSONObject requestQrCodeStatus(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到票卡状态查询请求, form={}", form);
        RequestQrCodeStatusReqDTO request = DeviceRequests.unwrap(form, RequestQrCodeStatusReqDTO.class);
        if (request == null || isBlank(request.getCardId())) {
            return BomResponses.qrCodeStatusFail(CODE_INVALID_PARAM, "cardId不能为空");
        }
        return qrCodeStatusService.requestQrCodeStatus(request);
    }

    private static String channelOf(String providerId) {
        String channel = F2fChannel.fromProviderId(providerId);
        return channel == null ? F2fChannel.BOM : channel;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
