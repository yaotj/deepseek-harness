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

/**
 * 退款结果收口：把支付中心给出的退款结论落到退款单、原订单、票实例，并把 IF8B-04 通知置为待发。
 *
 * <p>退款域按「发起」与「收口」两段拆分，本类是**收口段**。它的六个入口被三类调用方共用：
 * 退款发起（同步应答）、退款回查 / 重试 / 重提交、退款回调、以及小程序票同步 {@code syncOrder}。
 * 因此收口段先于发起段独立出来 —— 发起段搬家时只需注入本类。
 *
 * <p><b>本类零 {@code @Transactional}、NEVER 加</b>：四个终态入口在最后都要
 * {@code refundNotifyService.deliverOne} 出网推 APP，加事务会同时踩
 * AGENTS.md §5.2「事务内 NEVER 发通知」与「事务内 NEVER 发 RPC」两条。
 * 若将来确实要加，MUST 同批把那四处投递改成 afterCommit 触发。
 *
 * <p><b>以下两段是从拆分前宿主 {@code DailyTicketServiceImpl.markRefundNotifyPending}
 * 的 javadoc 原样迁入的生产事故记录（2026-09-20），NEVER 删</b>：
 *
 * <p>四个调用点（{@code markRefunded} / {@code markTravelRefunded} / {@code markRefundFailed} /
 * {@code markTravelRefundFailed}）在 {@code markRefundNotifyPending} 之后都紧跟一次 {@code deliverOne}，
 * <b>NEVER 删掉那一行、也 NEVER 把首次投递只挂在回调路径上</b>：
 * 同步退款链路（{@code requestRefundTicket} → 网关同步应答 {@code code=0} → 本类收口）
 * 比支付中心的异步回调早到约 100ms，回调随后必然落进「已是 REFUNDED」分支，
 * 于是 2026-09-20 实测连续四笔退款的 {@code NOTIFY_STATUS} 全部停在 {@code PENDING}、
 * {@code NOTIFY_TIMES=0}，APP 一次都没收到退款结果。
 *
 * <p>本类零 {@code @Transactional}（每条 SQL 自动提交），因此在这里出网不违反
 * AGENTS.md §5.2「事务内不发通知 / 事务内不发 RPC」两条；
 * <b>若将来给本类加上事务注解，MUST 同批把这四处投递改成 afterCommit 触发。</b>
 *
 * <p>方法名与拆分前的私有方法**逐字保持一致**，为的是让「拆分前后行为一致」这件事可以逐行对照，
 * 改名留给后续独立一轮。
 */
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

    /** 将支付平台退款单号、完成结果同步到退款单和原日票订单。 */
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

    /** 明确失败时保留原退款单，后续重试必须继续使用该退款单号。 */
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

    /** 重试提交成功后回到处理中，等待支付平台异步或人工查询结果。 */
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

    /**
     * 旅游票退款明细逐子单落库，{@code DAILY_TICKET_REFUND_DETAIL} 的**唯一写入方**。
     *
     * <p>整单退款（{@code TRAVEL_FULL}）传全部子单、子单退款（{@code TRAVEL_SUB}）传一张，
     * 两条链路共用本方法 —— 明细的 {@code REFUND_STATUS} 后续由本类的
     * {@code markTravelRefunded} / {@code markTravelRefundFailed} 按 {@code REFUND_ORDER_NO} 整批推进，
     * 所以插入时**一律先写 {@code REFUNDING}**，NEVER 在这里按调用方的预期直接写终态。
     *
     * <p>{@code children == null} 直接返回、不抛：调用方已在上一步校验过主子关系，
     * 这里抛异常只会把已插入的退款单留成孤儿。
     */
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

    /**
     * 退款进入终态后把 IF8B-04 通知置为待发。
     *
     * <p>四个调用点（{@code markRefunded} / {@code markTravelRefunded} / {@code markRefundFailed} /
     * {@code markTravelRefundFailed}）在本方法之后都紧跟一次 {@code deliverOne}，
     * <b>NEVER 删掉那一行、也 NEVER 把首次投递只挂在回调路径上</b>：
     * 同步退款链路（发起 → 网关同步应答 {@code code=0} → 本方法）比支付中心的异步回调早到约 100ms，
     * 回调随后必然落进「已是 REFUNDED」分支，于是 2026-09-20 实测连续四笔退款的
     * {@code NOTIFY_STATUS} 全部停在 {@code PENDING}、{@code NOTIFY_TIMES=0}，APP 一次都没收到退款结果。
     */
    public void markRefundNotifyPending(DailyTicketRefund refund) {
        refund.setNotifyStatus("PENDING");
        refund.setNotifyTimes(0);
        refundMapper.updateNotifyStatus(refund.getOrderNo(), "PENDING", 0, null, null);
    }

    /** 支付平台版本的字段名存在 refundOrderNo/refundNo 两种实现，优先读取文档字段，兼容旧实现。 */
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

    /**
     * 对旧退款数据从最近一笔退款网关响应中恢复平台退款单号。
     * 恢复失败时不降级为单字段查询，防止错误关联到其他退款单。
     */
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

    /** 仅在响应实际带回新平台退款单号时更新，避免查询处理中覆盖退款完成时间。 */
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
