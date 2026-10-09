package com.chinasofti.huateng.dailyticket.service.lifecycle;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketUsageLogMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketUsageLog;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.DailyTicketAccActiveNotifyService;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.dailyticket.service.travel.TravelParentSummaryWriter;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 日票实例生命周期服务：激活、首次使用通知、进站校验、可用性判定、出站扣次、扣次明细。
 *
 * <p>零 {@code @Transactional}：所有方法只做本地 DB 读写（每条 SQL 自动提交），
 * 不调支付中心网关，不需要也不允许事务包住。
 */
@Service
public class DailyTicketInstanceLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketInstanceLifecycleService.class);

    private static final String RET_SUCCESS = DailyTicketOrderSupport.RET_SUCCESS;
    private static final String RET_FAIL = DailyTicketOrderSupport.RET_FAIL;
    private static final String TICKET_STATUS_ACTIVATED = DailyTicketInstanceStatus.ACTIVATED;
    private static final String TICKET_STATUS_USED = DailyTicketInstanceStatus.USED;
    private static final String TICKET_STATUS_EXPIRED = DailyTicketInstanceStatus.EXPIRED;

    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketUsageLogMapper usageLogMapper;
    private final DailyTicketAccActiveNotifyService accActiveNotifyService;
    private final TravelParentSummaryWriter travelParentSummaryWriter;

    public DailyTicketInstanceLifecycleService(
            DailyTicketOrderMapper orderMapper,
            TravelTicketOrderMapper travelOrderMapper,
            DailyTicketInstanceMapper instanceMapper,
            DailyTicketUsageLogMapper usageLogMapper,
            DailyTicketAccActiveNotifyService accActiveNotifyService,
            TravelParentSummaryWriter travelParentSummaryWriter) {
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.instanceMapper = instanceMapper;
        this.usageLogMapper = usageLogMapper;
        this.accActiveNotifyService = accActiveNotifyService;
        this.travelParentSummaryWriter = travelParentSummaryWriter;
    }

    /** 激活日票（IF8A-32）。 */
    public DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return fail(result, "orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getCardNum())) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (!canActivate(order)) {
            return fail(result, "订单未支付，不能激活");
        }

        DailyTicketInstance exists = instanceMapper.selectByOrderNo(request.getOrderNo());
        if (exists != null && !TICKET_STATUS_ACTIVATED.equals(exists.getTicketStatus())) {
            log.info("日票已开始使用或已终态，激活请求短路返回 orderNo={} ticketStatus={} cardNum={}",
                    request.getOrderNo(), exists.getTicketStatus(), exists.getCardNum());
            return success(result);
        }
        if (exists != null && !request.getCardNum().equals(exists.getCardNum())) {
            log.warn("日票重复激活但卡号与首次不一致 orderNo={} 原cardNum={} 本次cardNum={}",
                    request.getOrderNo(), exists.getCardNum(), request.getCardNum());
            return fail(result, "卡号与已激活车票不一致");
        }
        if (exists != null && "SUCCESS".equals(exists.getAccNoticeStatus())) {
            log.info("日票重复激活且ACC发售通知已成功，短路返回 orderNo={} cardNum={}",
                    request.getOrderNo(), exists.getCardNum());
            return success(result);
        }

        Date now = new Date();
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId(exists != null ? exists.getId() : nextId());
        ticket.setOrderNo(request.getOrderNo());
        ticket.setThirdUserId(request.getThirdUserId());
        ticket.setCardNum(request.getCardNum());
        ticket.setCardIssue(request.getCardIssue());
        ticket.setAppCardType(order.getCardType());
        ticket.setCodeTicketType(CardTypeCodeEnum.QR_POSTPAID.getCode());
        ticket.setTicketType(request.getTicketType());
        ticket.setShowType(request.getShowType());
        ticket.setTicketCode(request.getTicketCode());
        ticket.setTicketName(request.getTicketName());
        ticket.setPeriod(request.getPeriod());
        ticket.setActualTimes(request.getActualTimes());
        ticket.setTransSeq(request.getTransSeq());
        ticket.setTransAmount(request.getTransAmount());
        ticket.setDiscountAmount(request.getDiscountAmount());
        ticket.setPayChannel(request.getPayChannel());
        ticket.setCountingStart(request.getCountingStart());
        ticket.setTicketStatus(TICKET_STATUS_ACTIVATED);
        ticket.setActivateTime(now);
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        accActiveNotifyService.preparePending(ticket, request, now);
        instanceMapper.upsert(ticket);
        accActiveNotifyService.deliverAsync(ticket.getOrderNo());
        return success(result);
    }

    /**
     * 激活支付口径：
     * <ul>
     *     <li>独立日票沿用原规则：子单自身 ORDER_STATUS=PAID 且 PAY_STATUS=PAID；</li>
     *     <li>旅游票子单不写独立支付流水，子单保持 CREATED/INIT，回看 PARENT_ORDER_NO
     *         对应主单，主单两个支付字段都为 PAID 才允许激活。</li>
     * </ul>
     */
    private boolean canActivate(DailyTicketOrder order) {
        if (order == null) {
            return false;
        }
        if (StringUtils.hasText(order.getParentOrderNo())) {
            TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(order.getParentOrderNo());
            return parent != null
                    && "PAID".equals(parent.getOrderStatus())
                    && "PAID".equals(parent.getPayStatus());
        }
        return "PAID".equals(order.getOrderStatus())
                && "PAID".equals(order.getPayStatus());
    }

    /**
     * APP 首次使用通知（IF8A-33）：写入有效期截止时间并置「已开始使用」。
     * <p>
     * 同 {@link #markUsed}：<b>NEVER 在这里写 ACC_NOTICE_*</b>，APP 说「开始用了」不等于 ACC 已受理发售。
     */
    public DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getCardNum())) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance ticket = instanceMapper.selectByCardNum(request.getCardNum());
        if (ticket == null) {
            return fail(result, "车票不存在");
        }
        if (isTicketLockedForRefund(ticket.getTicketStatus())) {
            log.warn("日票首次使用通知：车票处于退款占用态，拒绝置已使用, cardNum={}, ticketStatus={}",
                    request.getCardNum(), ticket.getTicketStatus());
            return fail(result, "车票已申请退款，不允许使用");
        }
        Date now = new Date();
        ticket.setCountingEnd(request.getCountingEnd());
        ticket.setTicketStatus(TICKET_STATUS_USED);
        ticket.setFirstUseTime(ticket.getFirstUseTime() == null ? now : ticket.getFirstUseTime());
        ticket.setUpdateTime(now);
        int marked = instanceMapper.markUsed(ticket);
        if (marked == 0) {
            log.error("日票首次使用通知：实例状态回写影响 0 行, cardNum={}, instanceId={}",
                    request.getCardNum(), ticket.getId());
        }
        travelParentSummaryWriter.refreshBySubOrder(ticket.getOrderNo());
        log.info("日票首次使用通知完成, cardNum={}, countingEnd={}, ticketStatus={}",
                request.getCardNum(), request.getCountingEnd(), TICKET_STATUS_USED);
        return success(result);
    }

    public DailyTicketBaseResult validateEntryCheck(String cardNum) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance instance = instanceMapper.selectForEntryCheck(cardNum);
        if (instance == null) {
            log.info("日票进站校验：无有效日票实例, cardNum={}", cardNum);
            return fail(result, "无有效日票记录");
        }
        long now = System.currentTimeMillis();
        if (instance.getCountingStart() != null && now < instance.getCountingStart()) {
            log.warn("日票进站校验：未激活, cardNum={}, countingStart={}", cardNum, instance.getCountingStart());
            return fail(result, "日票尚未激活");
        }
        if (instance.getCountingEnd() != null && now > instance.getCountingEnd()) {
            log.warn("日票进站校验：已过期, cardNum={}, countingEnd={}", cardNum, instance.getCountingEnd());
            return fail(result, "日票已过期");
        }
        Integer actualTimes = instance.getActualTimes();
        if (actualTimes != null && actualTimes == 0) {
            log.warn("日票进站校验：计次票次数已用完, cardNum={}", cardNum);
            return fail(result, "计次票次数已用完");
        }
        return success(result);
    }

    /**
     * 拉码（IF8A-03）前置的只读可用性判定。
     * 判据与 {@link #validateEntryCheck} 同源（有效期 / 次数 / 退款占用），但只读、不推进状态，
     * 且异常一律吞掉转成 retCode，因为调用方按「不可达即降级放行」处置。
     */
    public DailyTicketBaseResult checkRideAvailability(String cardNum) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        try {
            if (!StringUtils.hasText(cardNum)) {
                return fail(result, "卡号不能为空");
            }
            DailyTicketInstance instance = instanceMapper.selectForEntryCheck(cardNum);
            if (instance == null) {
                log.info("日票可用性校验：无可用日票, cardNum={}", cardNum);
                return fail(result, "无可用日票");
            }
            if (isTicketLockedForRefund(instance.getTicketStatus())) {
                log.warn("日票可用性校验：车票处于退款占用态, cardNum={}, ticketStatus={}",
                        cardNum, instance.getTicketStatus());
                return fail(result, "车票已申请退款，不允许使用");
            }
            long now = System.currentTimeMillis();
            if (instance.getCountingStart() != null && now < instance.getCountingStart()) {
                log.warn("日票可用性校验：未激活, cardNum={}, countingStart={}",
                        cardNum, instance.getCountingStart());
                return fail(result, "日票尚未激活");
            }
            if (instance.getCountingEnd() != null && now > instance.getCountingEnd()) {
                log.warn("日票可用性校验：已过期, cardNum={}, countingEnd={}", cardNum, instance.getCountingEnd());
                return fail(result, "日票已过期");
            }
            Integer actualTimes = instance.getActualTimes();
            if (actualTimes != null && actualTimes == 0) {
                log.warn("日票可用性校验：计次票次数已用完, cardNum={}", cardNum);
                return fail(result, "日票次数已用完");
            }
            log.info("日票可用性校验通过, cardNum={}, ticketStatus={}, actualTimes={}",
                    cardNum, instance.getTicketStatus(), actualTimes);
            return success(result);
        } catch (Exception e) {
            log.error("日票可用性校验异常, cardNum={}", cardNum, e);
            return fail(result, "日票可用性校验异常");
        }
    }

    /**
     * 出站处理：计次票扣次、写入出站时间。
     * <p>
     * 只推进票状态与有效期，<b>NEVER 在这里写 ACC_NOTICE_*</b>：出站与 ACC 发售通知是两件事，
     * 把它置成 SUCCESS 会让一条从未成功的上报再也进不了补偿扫表（只捞 PENDING / FAIL / INIT）。
     */
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd) {
        return markUsed(cardNum, countingEnd, null, null, null);
    }

    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd,
                                          String orderNo, String inStation, String outStation) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance instance = instanceMapper.selectByCardNum(cardNum);
        if (instance == null) {
            log.warn("日票出站：无有效日票实例, cardNum={}", cardNum);
            return fail(result, "无有效日票记录");
        }
        if (isTicketLockedForRefund(instance.getTicketStatus())) {
            log.warn("日票出站：车票处于退款占用态，拒绝扣次, cardNum={}, ticketStatus={}",
                    cardNum, instance.getTicketStatus());
            return fail(result, "车票已申请退款，不允许使用");
        }
        Date now = new Date();
        Integer actualTimes = instance.getActualTimes();
        int remainTimes = actualTimes == null ? -1 : actualTimes;
        if (actualTimes != null && actualTimes > 0) {
            int updated = instanceMapper.decreaseActualTimes(instance.getId(), now);
            remainTimes = actualTimes - (updated > 0 ? 1 : 0);
            log.info("日票出站：计次票扣次, cardNum={}, 剩余次数={}", cardNum, remainTimes);
        }
        String nextStatus = remainTimes == 0 ? TICKET_STATUS_EXPIRED : TICKET_STATUS_USED;
        instance.setTicketStatus(nextStatus);
        instance.setCountingEnd(countingEnd == null ? instance.getCountingEnd() : countingEnd);
        instance.setFirstUseTime(instance.getFirstUseTime() == null ? now : instance.getFirstUseTime());
        instance.setUpdateTime(now);
        int marked = instanceMapper.markUsed(instance);
        if (marked == 0) {
            log.error("日票出站：实例状态回写影响 0 行, cardNum={}, instanceId={}", cardNum, instance.getId());
        }

        insertUsageLog(cardNum, orderNo, inStation, outStation, actualTimes, remainTimes, nextStatus);
        travelParentSummaryWriter.refreshBySubOrder(instance.getOrderNo());

        log.info("日票出站处理完成, cardNum={}, ticketStatus={}, 剩余次数={}, countingEnd={}, orderNo={}",
                cardNum, nextStatus, remainTimes, instance.getCountingEnd(), orderNo);
        return success(result);
    }

    /**
     * INSERT 扣次明细，UK_DTUL_ORDER 做幂等。
     * daily-ticket-server 已开 tracing，切面可能把异常包一层，所以沿 cause 链判定。
     * 明细落库失败不中断出站流程，只记 ERROR 留证据。
     */
    private void insertUsageLog(String cardNum, String orderNo, String inStation,
                                String outStation, Integer timesBefore, int timesAfter,
                                String ticketStatus) {
        try {
            DailyTicketUsageLog usageLog = new DailyTicketUsageLog();
            usageLog.setCardNum(cardNum);
            usageLog.setOrderNo(orderNo);
            usageLog.setTxnDate(new SimpleDateFormat("yyyyMMdd").format(new Date()));
            usageLog.setInStation(inStation);
            usageLog.setOutStation(outStation);
            usageLog.setTimesBefore(timesBefore);
            usageLog.setTimesAfter(timesAfter);
            usageLog.setTicketStatus(ticketStatus);
            usageLogMapper.insert(usageLog);
            log.info("日票扣次明细已记录, cardNum={}, orderNo={}, timesBefore={}, timesAfter={}",
                    cardNum, orderNo, timesBefore, timesAfter);
        } catch (Exception e) {
            if (isIntegrityViolation(e)) {
                log.info("日票扣次明细重复插入（幂等命中）, cardNum={}, orderNo={}", cardNum, orderNo);
            } else {
                log.error("日票扣次明细插入失败, cardNum={}, orderNo={}", cardNum, orderNo, e);
            }
        }
    }

    /** 沿 cause 链判定是否为唯一索引冲突（兼容 tracing 切面包装）。 */
    private static boolean isIntegrityViolation(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof org.springframework.dao.DuplicateKeyException
                    || cur instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
        }
        return false;
    }

    public DailyTicketBaseResult queryUsageLog(String cardNum) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        List<DailyTicketUsageLog> logs = usageLogMapper.selectByCardNum(cardNum);
        result.setRetCode(RET_SUCCESS);
        result.setRetMsg("成功，共" + logs.size() + "条");
        result.setData(logs);
        log.info("查询日票扣次明细完成, cardNum={}, count={}", cardNum, logs.size());
        return result;
    }

    public QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request) {
        QueryDailyTicketInfoResult result = new QueryDailyTicketInfoResult();
        DailyTicketInstance instance = null;
        if (StringUtils.hasText(request.getCardId())) {
            instance = instanceMapper.selectByCardNum(request.getCardId());
        } else if (StringUtils.hasText(request.getOrderNo())) {
            instance = instanceMapper.selectByOrderNo(request.getOrderNo());
        }
        if (instance == null) {
            result.setRetCode("0000");
            result.setRetMsg("成功");
            return result;
        }
        result.setRetCode("0000");
        result.setRetMsg("成功");
        result.setTicketCode(instance.getTicketCode());
        result.setActualTimes(instance.getActualTimes());
        return result;
    }

    /** 票是否处于退款占用态：{@code REFUND_LOCKED} 观察期内或 {@code REFUNDED} 已放款。 */
    boolean isTicketLockedForRefund(String ticketStatus) {
        return DailyTicketInstanceStatus.isLockedForRefund(ticketStatus);
    }

    private DailyTicketBaseResult success(DailyTicketBaseResult result) {
        result.setRetCode(RET_SUCCESS);
        result.setRetMsg("成功");
        return result;
    }

    private DailyTicketBaseResult fail(DailyTicketBaseResult result, String msg) {
        result.setRetCode(RET_FAIL);
        result.setRetMsg(msg);
        return result;
    }

    private String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
