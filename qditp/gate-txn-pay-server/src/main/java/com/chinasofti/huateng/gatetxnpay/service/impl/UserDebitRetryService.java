package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateRetryQueue;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateRetryQueueMapper;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.model.app.RequestPayFailOrderReqDTO;
import com.chinasofti.huateng.model.app.RequestPayFailOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户主动发起免密失败订单重试扣费（APP 接口 requestPayFailOrder）。
 *
 * <p><b>方案 A：队列解耦（2026-10-09 改造）</b>：
 * 用户触发后<b>仅入队</b>，不直接调支付中心，立即返回受理成功。
 * 由 {@link RetryQueueConsumer} 定时扫描队列表，控制速率消费，
 * 避免瞬时 TPS 冲击钱包等渠道的 TPA 限制。
 *
 * <p><b>与定时补偿 {@link DebitRetryProcessor} 的关系</b>：
 * 两条链路并行不替代。定时任务按次数上限 + 退避窗口慢跑，
 * 用户主动触发走队列表，绕过次数限制但受队列速率控制。
 *
 * <p><b>两道安全闸 MUST 保留、NEVER 因「用户主动」而去掉</b>：
 * <ul>
 *   <li>抢占 CAS（{@code prepareUserRetry} 一条 UPDATE 把单归一成 {@code RETRY}）——
 *       防并发双扣、防与定时任务撞车；</li>
 *   <li>排除 {@code DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'}——离线码金额未算准，
 *       扣下去就是扣错钱。</li>
 * </ul>
 *
 * <p><b>本类带 {@code @Transactional}</b>（批量入队需原子性），
 * 与 DebitRetryProcessor 不同口径——后者每笔独立调支付中心不能事务化。
 */
@Service
public class UserDebitRetryService {

    private static final Logger log = LoggerFactory.getLogger(UserDebitRetryService.class);

    /** 单批最多入队笔数，防止一次请求对队列表产生过大压力。 */
    private static final String IN_QUEUE_REMARK = "用户主动发起重试扣费";

    private final GateTxnPayMapper gateTxnPayMapper;
    private final GateRetryQueueMapper gateRetryQueueMapper;
    private final int userBatchSize;
    private final int maxQueueSize;

    public UserDebitRetryService(GateTxnPayMapper gateTxnPayMapper,
                                 GateRetryQueueMapper gateRetryQueueMapper,
                                 @Value("${gate.debitRetry.user.batchSize:200}") int userBatchSize,
                                 @Value("${gate.debitRetry.user.maxQueueSize:1000}") int maxQueueSize) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.gateRetryQueueMapper = gateRetryQueueMapper;
        this.userBatchSize = userBatchSize;
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * 用户主动发起免密失败订单重试扣费。
     *
     * <p><b>仅入队，不起调支付中心</b>。终态由 {@link RetryQueueConsumer} 消费队列表后收敛。
     *
     * @return 应答：{@code retCode=0000} 表示受理成功（已入队），
     *         {@code retMsg} 带本次入队的笔数；参数缺失返 {@code 8001}。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestPayFailOrderResult requestPayFailOrder(RequestPayFailOrderReqDTO request) {
        RequestPayFailOrderResult response = new RequestPayFailOrderResult();
        if (request == null || !StringUtils.hasText(request.getThirdUserId())) {
            response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
            response.setRetMsg("thirdUserId不能为空");
            log.warn("用户主动重试扣费参数缺失, request={}", request);
            return response;
        }
        String thirdUserId = request.getThirdUserId().trim();
        List<String> cardIdList = parseCardNums(request.getCardNums());

        // 1. 扫候选（不限次数、不限退避，只排除离线码待重算态）
        List<GateTxnPay> candidates;
        try {
            candidates = gateTxnPayMapper.selectUserRetryCandidates(thirdUserId, cardIdList, userBatchSize);
        } catch (RuntimeException e) {
            log.error("用户主动重试扣费扫表失败, thirdUserId={}, cardIdList={}", thirdUserId, cardIdList, e);
            response.setRetCode(GateTxnPayRetCode.QUERY_FAILED);
            response.setRetMsg("查询待重试订单失败");
            return response;
        }

        if (candidates == null || candidates.isEmpty()) {
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("无待重试的失败订单");
            log.info("用户主动重试扣费无候选, thirdUserId={}, cardIdList={}", thirdUserId, cardIdList);
            return response;
        }

        // 2. 抢占 CAS：把单归一成 RETRY（绕过 maxTimes，但排除 OFFLINE_FARE_PENDING）
        int claimed = 0;
        for (GateTxnPay order : candidates) {
            int c = gateTxnPayMapper.prepareUserRetry(order.getOrderNo(), order.getTxnDate(), IN_QUEUE_REMARK);
            if (c > 0) {
                claimed++;
            }
        }

        if (claimed == 0) {
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("无待重试的失败订单（已被处理）");
            log.info("用户主动重试扣费抢占失败，所有候选已被处理, thirdUserId={}", thirdUserId);
            return response;
        }

        // 3. 批量入队（幂等，已存在的跳过）
        List<GateRetryQueue> queueItems = new ArrayList<>();
        for (GateTxnPay order : candidates) {
            if (gateTxnPayMapper.prepareUserRetry(order.getOrderNo(), order.getTxnDate(), IN_QUEUE_REMARK) > 0) {
                GateRetryQueue item = new GateRetryQueue();
                item.setOrderNo(order.getOrderNo());
                item.setTxnDate(order.getTxnDate());
                item.setThirdUserId(order.getThirdUserId());
                item.setCardId(order.getCardId());
                item.setRetryCount(0);
                item.setMaxRetries(3);
                queueItems.add(item);
            }
        }

        if (queueItems.isEmpty()) {
            response.setRetCode(GateTxnPayRetCode.SUCCESS);
            response.setRetMsg("无待重试的失败订单（全部已入队）");
            log.info("用户主动重试扣费无新入队, thirdUserId={}", thirdUserId);
            return response;
        }

        // 4. 批量插入（超过 maxQueueSize 截断）
        if (queueItems.size() > maxQueueSize) {
            log.warn("用户主动重试扣费入队数量超限, 请求={}, 限制={}, 截断到{}",
                    thirdUserId, queueItems.size(), maxQueueSize);
            queueItems = queueItems.subList(0, maxQueueSize);
        }

        int inserted = gateRetryQueueMapper.batchInsertIgnoreDuplicate(queueItems);
        log.info("用户主动重试扣费入队完成, thirdUserId={}, 候选={}, 新入队={}",
                thirdUserId, candidates.size(), inserted);

        response.setRetCode(GateTxnPayRetCode.SUCCESS);
        response.setRetMsg("已加入重试队列，共" + inserted + "笔，将由系统自动发起扣款");
        return response;
    }

    /**
     * 把逗号拼接的逻辑卡号拆成去重、去空、去首尾空格的列表。
     */
    private List<String> parseCardNums(String cardNums) {
        if (!StringUtils.hasText(cardNums)) {
            return new ArrayList<>();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String raw : cardNums.split(",")) {
            String trimmed = raw.trim();
            if (StringUtils.hasText(trimmed)) {
                set.add(trimmed);
            }
        }
        return new ArrayList<>(set);
    }
}
