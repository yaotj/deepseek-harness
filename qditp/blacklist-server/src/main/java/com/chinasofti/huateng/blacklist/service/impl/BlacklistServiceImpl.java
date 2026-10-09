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

/**
 * 黑名单业务服务实现。
 *
 * <p>幂等靠 BLACKLIST 的 UK_BLACKLIST_CARD_ID 唯一约束，不做「读后写」；
 * 解除是<b>两阶段</b>：本方法只把行 CAS 成 STATUS='RELEASING'（仍算黑名单），
 * 通知推达渠道后才由 {@link BlacklistReleaseFinalizer} 搬历史并删主表行。
 * <b>NEVER 退回「同事务内插历史 + 删主表」</b> —— 那样通知没推出去用户就已被放行。</p>
 *
 * <p>渠道通知一律在 afterCommit 发出：事务内发起 RPC 会把行锁持有时长拉成对端响应时长，
 * 已有生产事故（虚拟线程 pin + 行锁 287 秒）。NEVER 把通知挪回事务内。</p>
 */
@Service
public class BlacklistServiceImpl implements BlacklistService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BlacklistServiceImpl.class);

    /** 渠道同步初值，供后续 outbox 扫表补偿使用。 */
    private static final String CHANNEL_SYNC_PENDING = "PENDING";

    /**
     * 新增黑名单落库时的初始状态。
     *
     * <p><b>MUST 显式赋值，NEVER 依赖列上的 DEFAULT 'ACTIVE'</b>：insert 语句把 STATUS 写进了列清单，
     * Oracle 的列默认值只在「该列不出现在 INSERT 列表里」时才生效，传 null 就真的落 null。
     * 而 selectPendingChannelSync / markReleasing / selectForInspect 三条语句都带 STATUS = 'ACTIVE'
     * 谓词 —— 落成 null 的行会同时失去「加黑通知补偿」「被解除」「进盘点清单」三种能力，
     * 且编译、单测、xmllint 全都发现不了（2026-09-18 端到端实测到）。</p>
     */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * 三个 NOT NULL 分类列的兜底值，取值与列上的 DEFAULT 一致（CHANNEL_CODE '99' / BLACK_SOURCE、BLACK_CAUSE '09'）。
     *
     * <p><b>MUST 在 Java 侧兜，NEVER 指望列 DEFAULT</b>：与 {@link #STATUS_ACTIVE} 同一个机理 —— insert 语句
     * 把这三列都写进了列清单，传 null 就落 null 而不是走默认值，Oracle 直接 ORA-01400。
     * 上游（运营页 / APP）不传这些分类字段是常态，因此**兜底是必需的、不是防御性冗余**：
     * 2026-09-18 实测运营页新增黑名单必踩 ORA-01400，且曾被幂等兜底误吞成「已在黑名单」+ 返 0000。</p>
     */
    private static final String CHANNEL_CODE_UNKNOWN = "99";
    private static final String BLACK_SOURCE_MANUAL = "09";
    private static final String BLACK_CAUSE_OTHER = "09";

    /** 渠道同步的投递与补偿，NEVER 在事务方法体内直接调它的 deliver* —— 那两个方法会出网。 */
    @Autowired
    private BlacklistChannelSyncService blacklistChannelSyncService;

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private BlacklistOperateLogMapper blacklistOperateLogMapper;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${app.notify.blacklist-url:}")
    private String appNotifyBlacklistUrl;

    @Value("${alipay.notify.blacklist-url:}")
    private String alipayNotifyBlacklistUrl;

    /** 运营管理页查询不触发外部渠道通知。 */
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
     * 解除黑名单的阶段一：把命中的行 CAS 成 STATUS='RELEASING' 并落操作日志，本事务内不删行、不搬历史。
     *
     * <p>提交后才发解除通知；只有渠道确认收到，才进阶段二搬历史 + 删主表行。
     * 推不动时行留在 RELEASING，判黑仍命中，等扫表补偿重入。</p>
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
        List<String> markedCardIds = new ArrayList<>(existsRecords.size());
        for (Blacklist record : existsRecords) {
            if (blacklistMapper.markReleasing(record.getCardId(), releaseReason, releaseBy) == 0) {
                log.info("卡号不在生效中状态（不存在或已在解除中），本次解除按幂等命中处理, cardId={}", record.getCardId());
                continue;
            }
            markedCardIds.add(record.getCardId());
            // 操作类型沿用 DELETE：运营页字典与历史数据都是这个值，改它要连带动前台。
            // 语义上这里只是「发起解除」，真正删行在通知推成功之后的阶段二。
            insertOperateLog(record.getCardId(), record.getThirdUserId(), "DELETE", releaseReason, releaseBy);
        }
        // 提交后才出网。此刻这些行是 RELEASING + PENDING，仍算黑名单；
        // 只有通知推成功才进阶段二搬历史 + 删行，推不动就留给扫表补偿。
        afterCommit(() -> markedCardIds.forEach(blacklistChannelSyncService::deliverReleaseByCardId));
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
            if (!isDuplicateKey(e)) {
                throw e;
            }
            return false;
        }
    }

    /**
     * 沿 cause 链判定是否为「唯一约束冲突」。
     *
     * <p><b>只认 {@link DuplicateKeyException}，NEVER 把父类 DataIntegrityViolationException 也算进来</b>：
     * 父类还涵盖 NOT NULL 违反、外键违反、检查约束违反、列长超限等，把它当成「幂等命中」会让这些真错误
     * 被静默吞掉 —— 表现是行根本没落库、却照样写操作日志并对上游返 0000。2026-09-18 端到端实测到：
     * 请求漏送 channelCode 时 Oracle 报 ORA-01400，Spring 翻成 DataIntegrityViolationException，
     * 于是日志打「卡号已在黑名单中，本次新增按幂等命中处理」，而 BLACKLIST 里一行都没有。</p>
     *
     * @param throwable 异常
     * @return true 表示是唯一约束冲突
     */
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
     * 空值兜底：入参为空白时返回兜底值。
     *
     * @param value 入参
     * @param fallback 兜底值
     * @return 去空格后的入参，或兜底值
     */
    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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
}
