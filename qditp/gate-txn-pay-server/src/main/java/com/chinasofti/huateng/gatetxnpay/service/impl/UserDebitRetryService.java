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


@Service
public class UserDebitRetryService {

    private static final Logger log = LoggerFactory.getLogger(UserDebitRetryService.class);

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
