package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestActiveTicketReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestTakeTicketAuthReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class F2fTakeTicketService {

    private static final Logger log = LoggerFactory.getLogger(F2fTakeTicketService.class);

    private static final String STATUS_PAID = F2fOrderStatus.PAID.name();

    private static final String ACTIVATED = "1";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    public F2fTakeTicketService(F2fOrderMapper orderMapper, F2fPaymentMapper paymentMapper) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
    }

    public JSONObject requestActiveTicket(RequestActiveTicketReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("激活取票订单 订单不存在, orderNo={}", orderNo);
            return TvmResponses.fail(DeviceRetCode.ORDER_NO_ERROR, "订单号错误,没有找到匹配的订单");
        }
        if (!STATUS_PAID.equals(order.getOrderStatus())) {
            log.info("激活取票订单 订单不在已支付状态, orderNo={}, status={}", orderNo, order.getOrderStatus());
            return TvmResponses.fail(DeviceRetCode.FAIL, "该订单非支付成功，不可激活");
        }
        int activated = orderMapper.activateForDevice(orderNo, request.getDeviceId(),
                request.getQrcodeGenDate(), request.getRandomFact(), LocalDateTime.now());
        if (activated == 0) {
            log.warn("激活取票订单 未抢到（已被其他设备激活或状态已变）, orderNo={}, deviceId={}",
                    orderNo, request.getDeviceId());
            return TvmResponses.fail(DeviceRetCode.ORDER_LOCKED, "该订单已被其他设备激活");
        }
        log.info("激活取票订单成功, orderNo={}, deviceId={}", orderNo, request.getDeviceId());
        return TvmResponses.success();
    }

    public JSONObject requestTakeTicketAuth(RequestTakeTicketAuthReqDTO request) {
        F2fOrder order = orderMapper.selectByQrcode(
                request.getDeviceId(), request.getQrcodeGenDate(), request.getRandomFact());
        if (order == null) {
            log.info("取票鉴权 没有找到匹配的取票订单, deviceId={}, qrcodeGenDate={}",
                    request.getDeviceId(), request.getQrcodeGenDate());
            return TvmResponses.takeTicketAuthNoActiveOrder();
        }
        if (!ACTIVATED.equals(order.getActivateFlag())) {
            log.info("取票鉴权 订单未激活, orderNo={}, activateFlag={}",
                    order.getOrderNo(), order.getActivateFlag());
            return TvmResponses.takeTicketAuthNoActiveOrder();
        }
        log.info("取票鉴权 命中激活订单, orderNo={}", order.getOrderNo());
        return TvmResponses.takeTicketAuthSuccess(order.getOrderNo(), order.getActivateDeviceId(),
                order.getEntryStationCode(), order.getExitStationCode(), order.getTicketPrice(),
                order.getTicketNum(), order.getSingleTicketType(), channelCodeOf(order.getOrderNo()));
    }

    private String channelCodeOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayChannelCode();
    }
}
