package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.constant.BlacklistErrorCodeEnum;
import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.BlacklistOperateLog;
import com.chinasofti.huateng.blacklist.entity.BlacklistReleased;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.mapper.BlacklistOperateLogMapper;
import com.chinasofti.huateng.blacklist.mapper.BlacklistReleasedMapper;
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
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

/**
 * 黑名单业务服务实现。
 *
 * <p>幂等靠 BLACKLIST 的 UK_BLACKLIST_CARD_ID 唯一约束，不做「读后写」；
 * 解除是「取快照 → 插 BLACKLIST_RELEASED → 删主表」三步同事务，主表只留当前生效记录。</p>
 *
 * <p>渠道通知一律在 afterCommit 发出：事务内发起 RPC 会把行锁持有时长拉成对端响应时长，
 * 已有生产事故（虚拟线程 pin + 行锁 287 秒）。NEVER 把通知挪回事务内。</p>
 */
@Service
public class BlacklistServiceImpl implements BlacklistService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BlacklistServiceImpl.class);

    /** 渠道同步初值，供后续 outbox 扫表补偿使用。 */
    private static final String CHANNEL_SYNC_PENDING = "PENDING";

    /** 渠道同步的投递与补偿，NEVER 在事务方法体内直接调它的 deliver* —— 那两个方法会出网。 */
    @Autowired
    private BlacklistChannelSyncService blacklistChannelSyncService;

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private BlacklistReleasedMapper blacklistReleasedMapper;

    @Autowired
    private BlacklistOperateLogMapper blacklistOperateLogMapper;

    @Autowired
    private com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient alipayPaySignClient;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${app.notify.blacklist-url:}")
    private String appNotifyBlacklistUrl;

    @Value("${alipay.notify.blacklist-url:}")
    private String alipayNotifyBlacklistUrl;

    @Value("${alipay.external.blacklist-url:}")
    private String alipayExternalBlacklistUrl;

    /** 运营管理页查询不触发外部渠道通知。 */
    @Override
    public ResultVO<PageInfo<Blacklist>> page(String cardId, String thirdUserId, String createTimeBegin,
                                              String createTimeEnd, Integer pageNum, Integer pageSize) {
        PageInfo<Blacklist> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> blacklistMapper.selectPage(trimToNull(cardId), trimToNull(thirdUserId),
                        trimToNull(createTimeBegin), trimToNull(createTimeEnd)));
        return ResultMapper.ok(page);
    }

    /**
     * 查询卡号是否命中黑名单。
     *
     * @param request 查询黑名单请求参数
     * @return 查询黑名单结果
     */
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
        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");
        result.setInBlack(blackCount > 0 ? "1" : "0");
        return result;
    }

    /**
     * 新增黑名单。已在黑名单中时视为成功（唯一约束冲突即幂等命中），不覆盖原有分类字段。
     *
     * @param request 新增黑名单请求参数
     * @return 黑名单操作结果
     */
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

        // TODO 先注释掉地铁APP和内部支付宝通知，只保留支付宝外部通知
        // NEVER 取消注释恢复下面两行调用 —— 属行为变更，需另行评审。
        // notifyAppBlacklistAsync(cardId, thirdUserId, request.getCardType(), "1", request.getReason());
        // notifyAlipayBlacklistAsync(cardId, thirdUserId, request.getCardType(), "1", request.getReason());
        // 提交后再推渠道：事务内发 RPC 会让行锁持有时长等于对端响应时长（pay-sign 侧已出过 287 秒事故）。
        // 这里只触发快速路径；行已带 CHANNEL_SYNC_STATUS='PENDING'，推不动会被扫表补偿捞回来。
        afterCommit(() -> deliverAddNotify(cardId));

        return result;
    }

    /**
     * 解除黑名单：整行搬至 BLACKLIST_RELEASED 后删除主表记录，三步同事务。
     *
     * @param request 删除黑名单请求参数
     * @return 黑名单操作结果
     */
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
        List<BlacklistReleased> releasedRecords = new ArrayList<>(existsRecords.size());
        for (Blacklist record : existsRecords) {
            BlacklistReleased released = toReleased(record, releaseReason, releaseBy);
            // 解黑通知的 outbox 载体是本行，不是主表那行（主表行随后就被删了）。
            released.setChannelSyncStatus(CHANNEL_SYNC_PENDING);
            blacklistReleasedMapper.insert(released);
            releasedRecords.add(released);
        }
        blacklistMapper.deleteByCardIds(cardIds);
        for (Blacklist record : existsRecords) {
            insertOperateLog(record.getCardId(), record.getThirdUserId(), "DELETE", releaseReason, releaseBy);
            // TODO 先注释掉内部支付宝通知，只保留支付宝外部通知
            // NEVER 取消注释恢复下面这行调用 —— 属行为变更，需另行评审。
            // notifyAlipayBlacklistAsync(record.getCardId(), record.getThirdUserId(), record.getCardType(), "2", releaseReason);
        }
        // 同加黑侧：提交后才出网。每行都已是 PENDING，快速路径推不动就留给扫表补偿。
        afterCommit(() -> releasedRecords.forEach(blacklistChannelSyncService::deliverRelease));
        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");

        return result;
    }

    /**
     * 按入参装配待落库的黑名单记录。
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param request 新增黑名单请求参数
     * @return 黑名单记录
     */
    private Blacklist buildBlacklist(String cardId, String thirdUserId, AddBlackListReqDTO request) {
        Blacklist blacklist = new Blacklist();
        blacklist.setCardId(cardId);
        blacklist.setThirdUserId(thirdUserId);
        blacklist.setCardType(trimToNull(request.getCardType()));
        blacklist.setChannelCode(trimToNull(request.getChannelCode()));
        blacklist.setBlackSource(trimToNull(request.getBlackSource()));
        blacklist.setBlackCause(trimToNull(request.getBlackCause()));
        blacklist.setBizNo(trimToNull(request.getBizNo()));
        blacklist.setReason(request.getReason());
        blacklist.setCreateBy(trimToNull(request.getCreateBy()));
        blacklist.setChannelSyncStatus(CHANNEL_SYNC_PENDING);
        return blacklist;
    }

    /**
     * 按当前生效记录装配解除快照，保留原拉黑时间与全部分类字段。
     *
     * @param record 当前生效的黑名单记录
     * @param releaseReason 解除原因
     * @param releaseBy 解除操作者
     * @return 解除快照
     */
    private BlacklistReleased toReleased(Blacklist record, String releaseReason, String releaseBy) {
        BlacklistReleased released = new BlacklistReleased();
        released.setOriginId(record.getId());
        released.setCardId(record.getCardId());
        released.setThirdUserId(record.getThirdUserId());
        released.setCardType(record.getCardType());
        released.setChannelCode(record.getChannelCode());
        released.setBlackSource(record.getBlackSource());
        released.setBlackCause(record.getBlackCause());
        released.setBizNo(record.getBizNo());
        released.setReason(record.getReason());
        released.setCreateBy(record.getCreateBy());
        released.setCreateTime(record.getCreateTime());
        released.setReleaseReason(releaseReason);
        released.setReleaseBy(releaseBy);
        return released;
    }

    /**
     * 直接 INSERT，唯一约束冲突即视为已在黑名单。
     *
     * <p>MUST 沿 cause 链判定：观测切面会把异常重新包一层，只看最外层类名的裸 catch 会落空。</p>
     *
     * @param record 黑名单记录
     * @return true 表示本次真正插入，false 表示已存在
     */
    private boolean insertIgnoreDuplicate(Blacklist record) {
        try {
            blacklistMapper.insert(record);
            return true;
        } catch (RuntimeException e) {
            if (!isConflict(e)) {
                throw e;
            }
            return false;
        }
    }

    /**
     * 沿 cause 链判定是否为唯一约束或完整性约束冲突。
     *
     * @param throwable 异常
     * @return true 表示是约束冲突
     */
    private boolean isConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /**
     * 把动作推迟到事务提交之后执行；无事务时立即执行。
     *
     * <p>动作内部 MUST 自行吞掉异常：afterCommit 抛异常回滚不了已提交的事务，
     * 只会让本已成功的操作对上游报错、引来重推。</p>
     *
     * @param action 待执行动作
     */
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

    /**
     * 将逗号分隔的卡号字符串解析为卡号列表。
     *
     * @param cardId 卡号字符串
     * @return 卡号列表
     */
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

    /**
     * 写入黑名单操作记录。
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param operateType 操作类型，ADD 或 DELETE
     * @param reason 本次操作原因，ADD 记拉黑备注，DELETE 记解除原因
     * @param operator 操作者
     */
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

    /**
     * 异步通知地铁APP黑名单状态变更。
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param cardType 卡类型编码
     * @param blackListType 黑名单类型，1：加入黑名单，2：移除黑名单
     * @param reason 变更原因
     */
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

    /**
     * 异步通知内部支付宝服务（fep-alipay-server）。
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param cardType 卡类型编码
     * @param blackListType 黑名单类型，1：加入黑名单，2：移除黑名单
     * @param reason 变更原因
     */
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

    /**
     * 通知支付宝外部黑名单状态变更，由 afterCommit 触发。
     *
     * <p>不再上送 expireTime：业务上黑名单没有有效期，原实现填的是当前时刻，
     * 语义等于「立即失效」。NEVER 填回当前时刻。</p>
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param cardType 卡类型编码
     * @param blackListType 黑名单类型，1：加入黑名单，2：移除黑名单
     * @param reason 变更原因
     */
    /**
     * 加黑提交后触发快速路径：按卡号回查拿到刚落库那行（含自增主键），再交给渠道同步服务投递。
     *
     * <p><b>为什么要回查、不直接用 insert 时那个对象</b>：加黑靠 {@code UK_BLACKLIST_CARD_ID} 做幂等，
     * <b>幂等命中那一支 INSERT 抛的是约束冲突、根本没拿到自增 ID</b>，而 outbox 要按 ID 回写状态。
     * 回查发生在事务提交之后、不在锁内，且加黑不是热路径，这一次 SELECT 换来的是两条分支走同一段代码。
     * <b>NEVER 改成「只有真插入那支才通知」</b> —— 幂等命中往往正是上一次通知没推成功、上游在重推。
     *
     * @param cardId 卡ID，本表对它有唯一约束、最多一行
     */
    private void deliverAddNotify(String cardId) {
        List<Blacklist> rows = blacklistMapper.selectByCardIds(List.of(cardId));
        if (rows.isEmpty()) {
            log.warn("加黑提交后回查不到该卡，跳过渠道通知快速路径, cardId={}", cardId);
            return;
        }
        blacklistChannelSyncService.deliverAdd(rows.get(0));
    }

    private void notifyAlipayExternalBlacklistAsync(String cardId, String thirdUserId, String cardType, String blackListType, String reason) {
        try {
            AlipayBlackListNotifyReqDTO request = new AlipayBlackListNotifyReqDTO();
            request.setCardId(cardId);
            request.setThirdUserId(thirdUserId);
            request.setCardType(cardType);
            request.setBlackListType(blackListType);
            request.setOptionDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            request.setReason(reason);

            com.chinasofti.huateng.common.response.AlipayCommonResponse notifyResponse = alipayPaySignClient.notifyBlackListChange(request);
            if (notifyResponse != null && "0000".equals(notifyResponse.getRetCode())) {
                log.info("通知支付宝外部黑名单变更完成, cardId={}", cardId);
            } else {
                log.warn("通知支付宝外部黑名单变更失败, cardId={}, response={}", cardId, notifyResponse);
            }
        } catch (Exception e) {
            log.error("通知支付宝外部黑名单变更异常, cardId={}", cardId, e);
        }
    }
}
