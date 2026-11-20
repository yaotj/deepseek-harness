package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.constant.BlacklistErrorCodeEnum;
import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.BlacklistOperateLog;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.mapper.BlacklistOperateLogMapper;
import com.chinasofti.huateng.blacklist.service.BlacklistService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class BlacklistServiceImpl implements BlacklistService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BlacklistServiceImpl.class);

    private static final String CHANNEL_SYNC_PENDING = "PENDING";


    private static final String STATUS_ACTIVE = "ACTIVE";


    private static final String CHANNEL_CODE_UNKNOWN = "99";
    private static final String BLACK_SOURCE_MANUAL = "09";
    private static final String BLACK_CAUSE_OTHER = "09";

    @Autowired
    private BlacklistChannelSyncService blacklistChannelSyncService;

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private BlacklistOperateLogMapper blacklistOperateLogMapper;

    @Autowired
    private com.chinasofti.huateng.rpc.pay.GateTxnPayClient gateTxnPayClient;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${app.notify.blacklist-url:}")
    private String appNotifyBlacklistUrl;

    @Value("${alipay.notify.blacklist-url:}")
    private String alipayNotifyBlacklistUrl;

    @Override
    public ResultVO<PageInfo<Blacklist>> page(String cardId, String thirdUserId, String status,
                                              String channelSyncStatus, String createTimeBegin,
                                              String createTimeEnd, Integer pageNum, Integer pageSize) {
        PageInfo<Blacklist> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> blacklistMapper.selectPage(trimToNull(cardId), trimToNull(thirdUserId),
                        trimToNull(status), trimToNull(channelSyncStatus),
                        trimToNull(createTimeBegin), trimToNull(createTimeEnd)));
        return ResultMapper.ok(page);
    }


    @Override
    public QueryBlackListResult queryBlackList(QueryBlackListReqDTO request) {
        QueryBlackListResult result = new QueryBlackListResult();
        result.setFailedCount(0);

        if (request == null || !StringUtils.hasText(request.getCardId())) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            result.setInBlack("0");
            return result;
        }

        List<String> cardIds = parseCardIds(request.getCardId());
        if (cardIds.isEmpty()) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            result.setInBlack("0");
            return result;
        }

        int blackCount = blacklistMapper.countByCardIds(cardIds);
        boolean inBlack = blackCount > 0;
        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");
        result.setInBlack(inBlack ? "1" : "0");
        // IT-087：仅 inBlack=1 时回填该卡扣费失败订单数（按 cardId 全量 FAIL/RETRY，经 rpc 查 gate-txn-pay）；
        // 非黑名单卡 failedCount 保持 0。查询失败/异常 fail-safe 回落 0，NEVER 让黑名单查询因此报错。
        if (inBlack) {
            result.setFailedCount(queryFailedCount(cardIds));
        }
        return result;
    }

    /**
     * 经 rpc 向 gate-txn-pay 查该卡扣费失败订单数（FAIL/RETRY）。
     * 仅 inBlack=1 时调用；查询无成功应答 / 异常 → 回落 0（fail-safe），MUST NOT 抛给上层。
     */
    private int queryFailedCount(List<String> cardIds) {
        int total = 0;
        for (String cardId : cardIds) {
            try {
                com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO req =
                        new com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO();
                req.setCardId(cardId);
                com.chinasofti.huateng.model.app.CardFailedCountRespDTO resp = gateTxnPayClient.countFailedOrderByCard(req);
                if (resp != null && "0000".equals(resp.getResultCode())) {
                    total += resp.getFailedCount();
                } else {
                    log.warn("查扣费失败数：gate-txn-pay 未答成功，按 0 处理, cardId={}, resp={}",
                            cardId, resp == null ? "null" : resp.getResultCode());
                }
            } catch (Exception e) {
                log.error("查扣费失败数：rpc 异常，按 0 处理, cardId={}", cardId, e);
            }
        }
        return total;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlackListOperateResult addBlackList(AddBlackListReqDTO request) {
        BlackListOperateResult result = new BlackListOperateResult();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }

        String cardId = request.getCardId().trim();
        String thirdUserId = trimToNull(request.getThirdUserId());
        boolean inserted = insertIgnoreDuplicate(buildBlacklist(cardId, thirdUserId, request));
        if (!inserted) {
            log.info("卡号已在黑名单中，本次新增按幂等命中处理, cardId={}", cardId);
        }
        insertOperateLog(cardId, thirdUserId, "ADD", request.getReason(), request.getCreateBy());

        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");

        afterCommit(() -> deliverAddNotify(cardId));

        return result;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlackListOperateResult deleteBlackList(DeleteBlackListReqDTO request) {
        BlackListOperateResult result = new BlackListOperateResult();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }

        List<String> cardIds = parseCardIds(request.getCardId());
        if (cardIds.isEmpty()) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }

        String releaseReason = trimToNull(request.getReleaseReason());
        String releaseBy = trimToNull(request.getReleaseBy());
        List<Blacklist> existsRecords = blacklistMapper.selectByCardIds(cardIds);
        List<String> markedCardIds = new ArrayList<>(existsRecords.size());
        for (Blacklist record : existsRecords) {
            if (blacklistMapper.markReleasing(record.getCardId(), releaseReason, releaseBy) == 0) {
                log.info("卡号不在生效中状态（不存在或已在解除中），本次解除按幂等命中处理, cardId={}", record.getCardId());
                continue;
            }
            markedCardIds.add(record.getCardId());
            insertOperateLog(record.getCardId(), record.getThirdUserId(), "DELETE", releaseReason, releaseBy);
        }
        afterCommit(() -> markedCardIds.forEach(blacklistChannelSyncService::deliverReleaseByCardId));
        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");

        return result;
    }


    private Blacklist buildBlacklist(String cardId, String thirdUserId, AddBlackListReqDTO request) {
        Blacklist blacklist = new Blacklist();
        blacklist.setCardId(cardId);
        blacklist.setThirdUserId(thirdUserId);
        blacklist.setCardType(trimToNull(request.getCardType()));
        blacklist.setChannelCode(defaultIfBlank(request.getChannelCode(), CHANNEL_CODE_UNKNOWN));
        blacklist.setBlackSource(defaultIfBlank(request.getBlackSource(), BLACK_SOURCE_MANUAL));
        blacklist.setBlackCause(defaultIfBlank(request.getBlackCause(), BLACK_CAUSE_OTHER));
        blacklist.setBizNo(trimToNull(request.getBizNo()));
        blacklist.setReason(request.getReason());
        blacklist.setCreateBy(trimToNull(request.getCreateBy()));
        blacklist.setChannelSyncStatus(CHANNEL_SYNC_PENDING);
        blacklist.setStatus(STATUS_ACTIVE);
        return blacklist;
    }


    private boolean insertIgnoreDuplicate(Blacklist record) {
        try {
            blacklistMapper.insert(record);
            return true;
        } catch (RuntimeException e) {
            if (!isDuplicateKey(e)) {
                throw e;
            }
            return false;
        }
    }


    private boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DuplicateKeyException) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }


    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }


    private List<String> parseCardIds(String cardId) {
        String[] items = cardId.split(",");
        List<String> cardIds = new ArrayList<>();
        for (String item : items) {
            if (StringUtils.hasText(item)) {
                cardIds.add(item.trim());
            }
        }
        return cardIds;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }


    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }


    private void insertOperateLog(String cardId, String thirdUserId, String operateType,
                                  String reason, String operator) {
        BlacklistOperateLog operateLog = new BlacklistOperateLog();
        operateLog.setCardId(cardId);
        operateLog.setThirdUserId(trimToNull(thirdUserId));
        operateLog.setOperateType(operateType);
        operateLog.setReason(reason);
        operateLog.setOperator(trimToNull(operator));
        blacklistOperateLogMapper.insert(operateLog);
    }


    private void notifyAppBlacklistAsync(String cardId, String thirdUserId, String cardType, String blackListType, String reason) {
        if (StringUtils.hasText(appNotifyBlacklistUrl)) {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("thirdUserId", thirdUserId);
            requestBody.put("cardId", cardId);
            requestBody.put("cardType", cardType);
            requestBody.put("blackListType", blackListType);
            requestBody.put("optionDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            requestBody.put("signType", "00");
            requestBody.put("sign", "");

            Mono<String> mono = webClientBuilder.build()
                    .post()
                    .uri(appNotifyBlacklistUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.error("异步通知地铁APP黑名单变更异常, cardId={}", cardId, e))
                    .doOnNext(response -> log.info("异步通知地铁APP黑名单变更完成, cardId={}, response={}", cardId, response))
                    .onErrorResume(e -> {
                        log.error("异步通知地铁APP黑名单变更失败, cardId={}", cardId, e);
                        return Mono.empty();
                    });

            mono.subscribe();
        } else {
            log.warn("地铁APP通知服务地址未配置，跳过黑名单变更通知, cardId={}", cardId);
        }
    }


    private void notifyAlipayBlacklistAsync(String cardId, String thirdUserId, String cardType, String blackListType, String reason) {
        if (StringUtils.hasText(alipayNotifyBlacklistUrl)) {
            Map<String, Object> blackList = new HashMap<>();
            blackList.put("thirdUserId", thirdUserId);
            blackList.put("cardId", cardId);
            blackList.put("cardType", cardType);
            blackList.put("blackListType", blackListType);
            blackList.put("optionDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("blackList", List.of(blackList));

            Mono<String> mono = webClientBuilder.build()
                    .post()
                    .uri(alipayNotifyBlacklistUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.error("异步通知内部支付宝服务黑名单变更异常, cardId={}", cardId, e))
                    .doOnNext(response -> log.info("异步通知内部支付宝服务黑名单变更完成, cardId={}, response={}", cardId, response))
                    .onErrorResume(e -> {
                        log.error("异步通知内部支付宝服务黑名单变更失败, cardId={}", cardId, e);
                        return Mono.empty();
                    });

            mono.subscribe();
        } else {
            log.warn("内部支付宝服务地址未配置，跳过黑名单变更通知, cardId={}", cardId);
        }
    }


    private void deliverAddNotify(String cardId) {
        List<Blacklist> rows = blacklistMapper.selectByCardIds(List.of(cardId));
        if (rows.isEmpty()) {
            log.warn("加黑提交后回查不到该卡，跳过渠道通知快速路径, cardId={}", cardId);
            return;
        }
        blacklistChannelSyncService.deliverAdd(rows.get(0));
    }

    @Override
    public List<BlacklistOperateLog> getOperateLogs(String cardId) {
        return blacklistOperateLogMapper.selectByCardId(cardId);
    }
}
