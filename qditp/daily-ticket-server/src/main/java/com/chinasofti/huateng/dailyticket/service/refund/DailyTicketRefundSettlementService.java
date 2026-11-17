package com.chinasofti.huateng.dailyticket.service.refund;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayLogMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundDetailMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefundDetail;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.DailyTicketRefundNotifyService;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.dailyticket.service.travel.TravelParentSummaryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Service
public class DailyTicketRefundSettlementService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketRefundSettlementService.class);

    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketRefundDetailMapper refundDetailMapper;
    private final DailyTicketPayLogMapper payLogMapper;
    private final DailyTicketRefundNotifyService refundNotifyService;
    private final DailyTicketTicketLockWriter ticketLockWriter;
    private final TravelParentSummaryWriter travelParentSummaryWriter;

    public DailyTicketRefundSettlementService(DailyTicketOrderMapper orderMapper,
                                              TravelTicketOrderMapper travelOrderMapper,
                                              DailyTicketRefundMapper refundMapper,
                                              DailyTicketRefundDetailMapper refundDetailMapper,
                                              DailyTicketPayLogMapper payLogMapper,
                                              DailyTicketRefundNotifyService refundNotifyService,
                                              DailyTicketTicketLockWriter ticketLockWriter,
                                              TravelParentSummaryWriter travelParentSummaryWriter) {
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.refundMapper = refundMapper;
        this.refundDetailMapper = refundDetailMapper;
        this.payLogMapper = payLogMapper;
        this.refundNotifyService = refundNotifyService;
        this.ticketLockWriter = ticketLockWriter;
        this.travelParentSummaryWriter = travelParentSummaryWriter;
    }

    public void markRefunded(DailyTicketOrder order, DailyTicketRefund refund, Map<String, Object> refundData) {
        Date now = new Date();
        updatePlatformRefundNo(refund, refundData);
        refund.setRefundStatus("REFUNDED");
        refund.setRefundDate(parseGatewayRefundDate(
                DailyTicketOrderSupport.stringValue(refundData == null ? null : refundData.get("refundTime"), null), now));
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDED");
        ticketLockWriter.settleOnRefunded(order.getOrderNo());
        markRefundNotifyPending(refund);
        refundNotifyService.deliverOne(refund.getOrderNo());
    }

    public void markRefundFailed(DailyTicketOrder order, DailyTicketRefund refund, Map<String, Object> refundData) {
        Date now = new Date();
        updatePlatformRefundNo(refund, refundData);
        refund.setRefundStatus("FAILED");
        refund.setRefundDate(null);
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        orderMapper.updateOrderStatus(order.getOrderNo(), "PAID");
        ticketLockWriter.releaseLock(order.getOrderNo());
        markRefundNotifyPending(refund);
        refundNotifyService.deliverOne(refund.getOrderNo());
    }

    public void markRefunding(DailyTicketOrder order, DailyTicketRefund refund) {
        Date now = new Date();
        refund.setRefundStatus("REFUNDING");
        refund.setRefundDate(null);
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
    }

    public void markTravelRefunded(TravelTicketOrder parent, DailyTicketRefund refund,
                                   Map<String, Object> refundData) {
        Date now = new Date();
        updatePlatformRefundNo(refund, refundData);
        refund.setRefundStatus("REFUNDED");
        refund.setRefundDate(parseGatewayRefundDate(
                DailyTicketOrderSupport.stringValue(refundData == null ? null : refundData.get("refundTime"), null), now));
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        refundDetailMapper.updateStatusByRefundOrderNo(refund.getRefundOrderNo(), "REFUNDED");
        List<DailyTicketOrder> refundedChildren = new ArrayList<>();
        if ("TRAVEL_SUB".equals(refund.getRefundScope())) {
            DailyTicketOrder child = orderMapper.selectByOrderNo(refund.getOrderNo());
            if (child != null) {
                refundedChildren.add(child);
            }
        } else {
            List<DailyTicketOrder> children = orderMapper.selectByParentOrderNo(parent.getOrderNo());
            if (children != null) {
                refundedChildren.addAll(children);
            }
        }
        for (DailyTicketOrder child : refundedChildren) {
            ticketLockWriter.settleOnRefunded(child.getOrderNo());
        }
        if ("TRAVEL_FULL".equals(refund.getRefundScope())) {
            travelOrderMapper.updateOrderStatus(parent.getOrderNo(), "REFUNDED");
        } else {
            travelParentSummaryWriter.refresh(parent.getOrderNo());
        }
        markRefundNotifyPending(refund);
        refundNotifyService.deliverOne(refund.getOrderNo());
    }

    public void markTravelRefundFailed(TravelTicketOrder parent, DailyTicketRefund refund,
                                       Map<String, Object> refundData) {
        refund.setRefundStatus("FAILED");
        refund.setRefundDate(null);
        refund.setUpdateTime(new Date());
        updatePlatformRefundNo(refund, refundData);
        refundMapper.updateResult(refund);
        refundDetailMapper.updateStatusByRefundOrderNo(refund.getRefundOrderNo(), "FAILED");
        if ("TRAVEL_SUB".equals(refund.getRefundScope())) {
            ticketLockWriter.releaseLock(refund.getOrderNo());
            travelParentSummaryWriter.refresh(parent.getOrderNo());
        } else {
            for (DailyTicketOrder child : orderMapper.selectByParentOrderNo(parent.getOrderNo())) {
                ticketLockWriter.releaseLock(child.getOrderNo());
            }
            travelOrderMapper.updateOrderStatus(parent.getOrderNo(), "PAID");
        }
        markRefundNotifyPending(refund);
        refundNotifyService.deliverOne(refund.getOrderNo());
    }

    public void markTravelRefunding(String parentOrderNo, DailyTicketRefund refund) {
        refund.setRefundStatus("REFUNDING");
        refund.setRefundDate(null);
        refund.setUpdateTime(new Date());
        refundMapper.updateResult(refund);
        travelOrderMapper.updateOrderStatus(parentOrderNo, "REFUNDING");
    }


    public void insertTravelRefundDetails(DailyTicketRefund refund, String parentOrderNo,
                                          List<DailyTicketOrder> children) {
        Date now = new Date();
        if (children == null) {
            return;
        }
        for (DailyTicketOrder child : children) {
            DailyTicketRefundDetail detail = new DailyTicketRefundDetail();
            detail.setId(DailyTicketOrderSupport.nextId());
            detail.setRefundOrderNo(refund.getRefundOrderNo());
            detail.setParentOrderNo(parentOrderNo);
            detail.setSubOrderNo(child.getOrderNo());
            detail.setRefundAmount(child.getTicketPrice());
            detail.setRefundStatus("REFUNDING");
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            refundDetailMapper.insert(detail);
        }
    }


    public void markRefundNotifyPending(DailyTicketRefund refund) {
        refund.setNotifyStatus("PENDING");
        refund.setNotifyTimes(0);
        refundMapper.updateNotifyStatus(refund.getOrderNo(), "PENDING", 0, null, null);
    }

    public void updatePlatformRefundNo(DailyTicketRefund refund, Map<String, Object> refundData) {
        if (refundData == null) {
            return;
        }
        String platformRefundNo = DailyTicketOrderSupport.stringValue(refundData.get("refundOrderNo"), null);
        if (!StringUtils.hasText(platformRefundNo)) {
            platformRefundNo = DailyTicketOrderSupport.stringValue(refundData.get("refundNo"), null);
        }
        if (StringUtils.hasText(platformRefundNo)) {
            refund.setPlatformRefundNo(platformRefundNo);
        }
    }


    public void restorePlatformRefundNoFromPayLog(String orderNo, DailyTicketRefund refund) {
        if (StringUtils.hasText(refund.getPlatformRefundNo())) {
            return;
        }
        String responseBody = payLogMapper.selectLatestRefundResponseBody(orderNo);
        if (!StringUtils.hasText(responseBody)) {
            return;
        }
        try {
            DailyTicketPayGatewayResponse response = JSON.parseObject(responseBody, DailyTicketPayGatewayResponse.class);
            if (response == null || response.getData() == null) {
                return;
            }
            persistPlatformRefundNoIfChanged(refund, response.getData());
        } catch (Exception e) {
            log.warn("日票退款历史网关响应解析失败，无法恢复平台退款单号 orderNo={}", orderNo, e);
        }
    }

    public void persistPlatformRefundNoIfChanged(DailyTicketRefund refund, Map<String, Object> refundData) {
        String before = refund.getPlatformRefundNo();
        updatePlatformRefundNo(refund, refundData);
        if (!StringUtils.hasText(refund.getPlatformRefundNo()) || refund.getPlatformRefundNo().equals(before)) {
            return;
        }
        refund.setUpdateTime(new Date());
        refundMapper.updateResult(refund);
    }

    public Date parseGatewayRefundDate(String refundTime, Date defaultValue) {
        if (!StringUtils.hasText(refundTime)) {
            return defaultValue;
        }
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss").parse(refundTime);
        } catch (Exception e) {
            log.warn("日票退款查询返回的退款时间格式错误 refundTime={}", refundTime);
            return defaultValue;
        }
    }
}
