package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.constant.ActivateFlagEnum;
import com.chinasofti.huateng.collectpay.constant.ItpStatusEnum;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;
import com.chinasofti.huateng.collectpay.entity.TvmAppOrder;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.entity.TvmTakeTicketOrder;
import com.chinasofti.huateng.collectpay.mapper.TvmAppOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmTakeTicketOrderMapper;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestActiveTicketReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestTakeTicketAuthReqDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.AppOrderService;
import com.chinasofti.huateng.collectpay.service.TvmTakeTicketService;
import com.chinasofti.huateng.collectpay.service.support.TakeTicketWaiter;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TVM扫码取票服务实现。 */
@Slf4j
@Service
public class TvmTakeTicketServiceImpl implements TvmTakeTicketService {

    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TvmTakeTicketOrderMapper tvmTakeTicketOrderMapper;

    @Autowired
    private TvmOrderMapper tvmOrderMapper;
    @Autowired
    private TvmAppOrderMapper tvmAppOrderMapper;

    @Autowired
    private TakeTicketWaiter takeTicketWaiter;

    /** 取票授权查不到激活订单时的挂起时长。 */
    @Value("${tvm.takeTicket.waitMillis:10000}")
    private long takeTicketWaitMillis;

    /** 兜底回查间隔。 */
    @Value("${tvm.takeTicket.pollIntervalMillis:500}")
    private long takeTicketPollIntervalMillis;

    // todo 激活和激活订单查询的问题：1.要激活的订单在哪里，预设tvm表的订单，如果这样，是否要在这个表中加一个新字段（是否已激活字段)
    @Override
    public JSONObject requestActiveTicket(RequestActiveTicketReqDTO request) {
        log.info("1.开始处理激活取票订单, request={}", request);
        String orderNo = request.getOrderNo();

        // 查询原支付订单
        TvmAppOrder appPayOrder = tvmAppOrderMapper.selectByOrderNo(orderNo);
        if (ObjectUtils.isEmpty(appPayOrder)) {
            log.info("2.没有找到匹配的订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }

        // 检查订单状态是否为支付成功
        if (!ItpStatusEnum.SUCCESS.getCode().equals(appPayOrder.getPayStatus())) {
            log.info("2.订单未支付或支付失败，orderNo={}, status={}", request.getOrderNo(), appPayOrder.getPayStatus());
            return TvmOrderResult.failMessage("该订单非支付成功，不可激活");
        }

        String nowTime = DateUtils.getNowTime();

        // 1.将原支付订单状态改为已激活
        int i = updateAppOrder(request,nowTime);
        if (i > 0) {
            log.info("5.激活取票订单处理完成, orderNo={}", request.getOrderNo());
            // 唤醒可能正挂在 requestTakeTicketAuth 上的那台 TVM，让它这一次请求就能拿到订单
            takeTicketWaiter.signal(TakeTicketWaiter.key(
                    request.getDeviceId(), request.getQrcodeGenDate(), request.getRandomFact()));
            return TvmOrderResult.success();
        } else {
            log.info("order {} 激活失败，结束", orderNo);
            return TvmOrderResult.failMessage( "激活失败");
        }

    }

    private int updateAppOrder(RequestActiveTicketReqDTO request,String nowStr) {
        Map<String, String> updateMap = new LinkedHashMap<>();
        String orderNo = request.getOrderNo();
        updateMap.put("orderNo", orderNo);
        updateMap.put("activateFlag", ActivateFlagEnum.ACTIVATE_ED.getCode());
        updateMap.put("deviceId", request.getDeviceId());
        updateMap.put("qrcodeGenDate", request.getQrcodeGenDate());
        updateMap.put("randomFact", request.getRandomFact());
        updateMap.put("activeTime", nowStr);
        updateMap.put("updateTime", nowStr);

        log.info("开始修改原支付订单的激活状态 orderNo is {}", orderNo);
        int i = tvmAppOrderMapper.updateByOrderNo(updateMap);
        log.info("激活结束 i i {}", orderNo);
        return i;
    }

    @Override
    public JSONObject requestTakeTicketAuth(RequestTakeTicketAuthReqDTO request) {
        log.info("1.开始处理扫码取票订单查询, request={}", request);

        AuthProbe probe = queryAuthOnce(request);
        if (probe.answer() != null) {
            return probe.answer();
        }

        String key = TakeTicketWaiter.key(request.getDeviceId(), request.getQrcodeGenDate(), request.getRandomFact());
        if (takeTicketWaitMillis <= 0 || !takeTicketWaiter.tryAcquire()) {
            return notActiveResult(probe.rowFound());
        }
        long deadline = System.currentTimeMillis() + takeTicketWaitMillis;
        try {
            log.info("3.订单尚未激活，挂起等待激活事件, key={}, waitMillis={}", key, takeTicketWaitMillis);
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                takeTicketWaiter.await(key, Math.min(takeTicketPollIntervalMillis, remaining));
                probe = queryAuthOnce(request);
                if (probe.answer() != null) {
                    return probe.answer();
                }
            }
        } finally {
            takeTicketWaiter.discard(key);
            takeTicketWaiter.release();
        }
        log.info("5.等待 {}ms 仍无激活订单, key={}", takeTicketWaitMillis, key);
        return notActiveResult(probe.rowFound());
    }

    /**
     * 按三要素查一次授权。
     *
     * @param answer   非空表示已可以答复 TVM（已激活订单 / 数据异常）；null 表示「还没激活」，可继续等
     * @param rowFound 是否查到了订单行，决定超时后回哪一种「无激活订单」的报文
     */
    private record AuthProbe(JSONObject answer, boolean rowFound) { }

    private AuthProbe queryAuthOnce(RequestTakeTicketAuthReqDTO request) {
        List<TvmAppOrder> tvmAppOrderLs = tvmAppOrderMapper.selectByDeviceAndQrcode(
                request.getDeviceId(), request.getQrcodeGenDate(), request.getRandomFact());
        if (ObjectUtils.isEmpty(tvmAppOrderLs)) {
            return new AuthProbe(null, false);
        }
        if (tvmAppOrderLs.size() > 1) {
            log.info("订单数超过1个，结束");
            return new AuthProbe(TvmOrderResult.failMessage("查询到的订单数量过多，失败"), true);
        }
        TvmAppOrder tvmAppOrder = tvmAppOrderLs.get(0);
        if (!StringUtils.equals(tvmAppOrder.getActivateFlag(), ActivateFlagEnum.ACTIVATE_ED.getCode())) {
            return new AuthProbe(null, true);
        }
        log.info("4.查询到激活订单，返回订单详情, orderNo={}", tvmAppOrder.getOrderNo());
        return new AuthProbe(TvmOrderResult.successData(DeviceResponse.getQuerySuccessResult(tvmAppOrder)), true);
    }

    /** 「无激活订单」的答复，两种形态与改动前**逐字节一致**： */
    private JSONObject notActiveResult(boolean rowFound) {
        if (!rowFound) {
            log.info("2.没有找到匹配的取票订单");
            return TvmOrderResult.fail(TvmPayCodeEnum.NO_ACTIVE_ORDER.getCode(), TvmPayCodeEnum.NO_ACTIVE_ORDER.getMsg());
        }
        return TvmOrderResult.failData(DeviceResponse.getQuerySuccessResult(new TvmAppOrder()));
    }


}
