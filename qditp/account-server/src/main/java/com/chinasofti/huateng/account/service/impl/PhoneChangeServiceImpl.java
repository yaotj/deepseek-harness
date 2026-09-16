package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.domain.PhoneChangeRule;
import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserPhoneChangeLog;
import com.chinasofti.huateng.account.mapper.AccountExceptionTicketMapper;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserPhoneChangeLogMapper;
import com.chinasofti.huateng.account.service.PhoneChangeService.SignSyncCompensateResult;
import com.chinasofti.huateng.account.service.PhoneChangeService;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.domain.SyncStatus;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 换号与显示账号同步补偿的实现，2026-09-11 从 {@code AccountApplicationServiceImpl} 原样搬出。
 *
 * <p>搬迁只改所属类，<b>没有改任何行为</b>：方法体、日志文案、事务边界与注释一并保留。
 * 其中三条不变量 <b>NEVER 破坏</b>：①{@link #updatePhone} 不带 {@code @Transactional}；
 * ②{@link #updatePhoneLocally} 内不发起任何 RPC；③员工码手机号同步 MUST 留在同一事务内且不被 catch。</p>
 */
@Service
public class PhoneChangeServiceImpl implements PhoneChangeService {

    private static final Logger log = LoggerFactory.getLogger(PhoneChangeServiceImpl.class);

    /**
     * USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS 的初始态：显示账号变更事实待投递给支付域。
     *
     * <p>取值集合 PENDING / SUCCESS / FAILED，白名单与 CAS 见 UserPhoneChangeLogMapper.xml。
     * 改动取值 MUST 全局 grep，NEVER 只改一处。</p>
     */
    private static final String SIGN_SYNC_PENDING = SyncStatus.PENDING.name();

    /** USER_PHONE_CHANGE_LOG.SIGN_SYNC_RESULT 是 VARCHAR2(1024 CHAR)。 */
    private static final int SIGN_SYNC_RESULT_MAX_LENGTH = 1024;

    /**
     * 补偿重推的次数上限（不含）。达到即留在 FAILED 不再重推，等人工介入。
     *
     * <p>不设上限等于对一个恒定失败的下游无限重试：每轮扫表都会捞到同一批行，
     * RPC 量随时间线性堆积，且真正的故障被淹没在重复日志里。</p>
     */
    private static final int SIGN_SYNC_MAX_RETRY = 10;

    /**
     * 单批扫表条数上限。
     *
     * <p>补偿由 web-admin 的 Quartz 任务同步调用，整批耗时 = 条数 × 单次 RPC 往返；
     * 不限量会让一次调度长时间占住线程（虚拟线程 pin 风险见 AGENTS.md §5.2）。
     * 没处理完的行留给下一次调度，NEVER 靠加大批量来「一次清完」。</p>
     */
    private static final int SIGN_SYNC_SCAN_LIMIT = 200;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserPhoneChangeLogMapper userPhoneChangeLogMapper;

    /**
     * 员工码表，本类只用于「换号时同步员工码手机号」。
     * <p>员工码的状态机与 ACC 交互归 {@code EmployeeCardService}，
     * <b>NEVER 在本类里改 {@code CARD_STATUS}</b>，否则状态机就有了第二个写入方。</p>
     */
    private final UserAccEmployeeCardMapper userAccEmployeeCardMapper;

    /**
     * 异常工单。补偿重推达上限的行在这里留一条 OPEN 记录。
     * 没有它那些行只是静静停在 {@code FAILED} 且不再被扫表捞取 —— 有进无出、无人知晓。
     */
    private final AccountExceptionTicketMapper accountExceptionTicketMapper;

    private final PaySignClient paySignClient;

    /**
     * 换号的落库部分用它显式开短事务。
     * <b>NEVER</b> 给 {@link #updatePhone} 加 {@code @Transactional} —— 它尾部要发 RPC。
     */
    private final TransactionTemplate transactionTemplate;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public PhoneChangeServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper,
                                  UserPhoneChangeLogMapper userPhoneChangeLogMapper,
                                  UserAccEmployeeCardMapper userAccEmployeeCardMapper,
                                  AccountExceptionTicketMapper accountExceptionTicketMapper,
                                  PaySignClient paySignClient,
                                  TransactionTemplate transactionTemplate) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userPhoneChangeLogMapper = userPhoneChangeLogMapper;
        this.userAccEmployeeCardMapper = userAccEmployeeCardMapper;
        this.accountExceptionTicketMapper = accountExceptionTicketMapper;
        this.paySignClient = paySignClient;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 本地事务的产出，决定事务外是否要向支付域投递、投递哪一行。
     */
    private enum PhoneChangeOutcome {
        /** 手机号已更新，需要投递。 */
        UPDATED,
        /** 新旧号相同，无需更新也无需投递。 */
        UNCHANGED,
        /** 未找到有效用户或更新影响 0 行。 */
        FAILED
    }

    private record PhoneChangeLocal(PhoneChangeOutcome outcome, Long changeLogId) {
        static PhoneChangeLocal failed() {
            return new PhoneChangeLocal(PhoneChangeOutcome.FAILED, null);
        }

        static PhoneChangeLocal unchanged() {
            return new PhoneChangeLocal(PhoneChangeOutcome.UNCHANGED, null);
        }
    }

    /**
     * 更换手机号。
     *
     * <p><b>本方法故意不带 {@code @Transactional}</b>：尾部要向 pay-sign 投递「显示账号已变更」
     * 这一事实（一次出网 HTTP）。事务内发起 RPC 会让行级排他锁的持有时长等于对端响应时长，
     * 是 2026-08-26 生产事故的形态（见 AGENTS.md §5.2）。本地写入用 {@code transactionTemplate}
     * 显式包成一个短事务，投递放在事务提交之后。</p>
     *
     * <p>投递失败 NEVER 回滚本地手机号变更 —— 手机号已改是既成事实，支付域的显示账号
     * 只是「待送达」，落 {@code SIGN_SYNC_STATUS = 'FAILED'} 等扫表补偿重推即可。
     * 反之若因投递失败而回滚，用户会看到换号失败，而下次重试仍会遇到同一个不可用的下游。</p>
     */
    @Override
    public boolean updatePhone(String thirdUserId, String newMsisdn) {
        if (!PhoneChangeRule.hasRequiredFields(thirdUserId, newMsisdn)) {
            log.warn("更换手机号参数校验失败, thirdUserId={}, newMsisdn={}", thirdUserId, newMsisdn);
            return false;
        }
        String userId = thirdUserId.trim();
        String phone = newMsisdn.trim();

        PhoneChangeLocal local;
        try {
            local = transactionTemplate.execute(status -> updatePhoneLocally(userId, phone));
        } catch (Exception e) {
            log.error("更换手机号异常, thirdUserId={}", userId, e);
            return false;
        }
        if (local == null || local.outcome() == PhoneChangeOutcome.FAILED) {
            return false;
        }
        if (local.outcome() == PhoneChangeOutcome.UNCHANGED) {
            return true;
        }

        syncDisplayAccountToPayDomain(local.changeLogId(), userId, phone);
        log.info("地铁APP用户更换手机号成功, thirdUserId={}, newMsisdn={}, changeLogId={}",
                userId, phone, local.changeLogId());
        return true;
    }

    /**
     * 本地事务部分：改 {@code USER_ITP_REG_INFO.MSISDN} + 落一条 {@code USER_PHONE_CHANGE_LOG}
     * （{@code SIGN_SYNC_STATUS = 'PENDING'}）。
     *
     * <p><b>本方法内 NEVER 发起任何 RPC / 网络调用</b> —— 它运行在事务里。</p>
     */
    private PhoneChangeLocal updatePhoneLocally(String thirdUserId, String newMsisdn) {
        // 口径：多卡用户在 USER_ITP_REG_INFO 有多行，这里取 selectActiveByThirdUserId 的「最新一条」的
        // MSISDN 当 OLD_MSISDN，而 updateMsisdnByThirdUserId 是按 THIRD_USER_ID 全量改。若该用户名下
        // 各行原本手机号不一致（历史脏数据），日志里的旧号只代表其中一张卡，NEVER 拿它当「所有卡的旧号」
        // 做补偿或回溯依据；真要逐卡留痕，MUST 改成按行记录、而不是在这里换查询方法。
        UserItpRegInfo regInfo = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
        PhoneChangeRule.Precondition precondition = PhoneChangeRule.decide(regInfo, newMsisdn);
        if (precondition == PhoneChangeRule.Precondition.NO_ACTIVE_USER) {
            log.warn("更换手机号未找到有效用户, thirdUserId={}", thirdUserId);
            return PhoneChangeLocal.failed();
        }
        if (precondition == PhoneChangeRule.Precondition.UNCHANGED) {
            log.info("新旧手机号相同，无需更换, thirdUserId={}, msisdn={}", thirdUserId, newMsisdn);
            return PhoneChangeLocal.unchanged();
        }
        String oldMsisdn = regInfo.getMsisdn();
        int updated = userItpRegInfoMapper.updateMsisdnByThirdUserId(thirdUserId, newMsisdn);
        if (updated == 0) {
            log.warn("更换手机号更新失败, thirdUserId={}", thirdUserId);
            return PhoneChangeLocal.failed();
        }
        // 员工码行上也存着手机号，且 selectActiveByPhone 是「按手机号找员工码」的唯一入口，
        // 这里不同步就会出现「用户已是新号、员工码还留着旧号」，之后按新号再也找不到本人的卡。
        // 与上面的 MSISDN 同属账户域本地表，**MUST 放在同一事务内**：失败就一起回滚，
        // NEVER catch 掉——那会留下一个没人会去修的静默不一致。影响 0 行是正常的（该用户没有员工码）。
        int cardsPhoneUpdated = userAccEmployeeCardMapper.updatePhoneByThirdUserId(thirdUserId, newMsisdn);
        if (cardsPhoneUpdated > 0) {
            log.info("员工码手机号已随换号同步, thirdUserId={}, rows={}", thirdUserId, cardsPhoneUpdated);
        }
        LocalDateTime now = LocalDateTime.now();
        UserPhoneChangeLog changeLog = new UserPhoneChangeLog();
        changeLog.setThirdUserId(thirdUserId);
        changeLog.setUserType("ITP");
        changeLog.setOldMsisdn(oldMsisdn);
        changeLog.setNewMsisdn(newMsisdn);
        changeLog.setOperType("CHANGE_PHONE");
        changeLog.setOperTime(now);
        changeLog.setOperator("SYSTEM");
        changeLog.setRemark("地铁APP用户更换手机号");
        changeLog.setCreateTms(now);
        changeLog.setSignSyncStatus(SIGN_SYNC_PENDING);
        userPhoneChangeLogMapper.insert(changeLog);
        return new PhoneChangeLocal(PhoneChangeOutcome.UPDATED, changeLog.getId());
    }

    /**
     * 事务外向支付域投递「显示账号已变更」，成败一律落到 {@code SIGN_SYNC_*}。
     *
     * <p>返回值 MUST 落库，NEVER 只打日志就放行 —— 只打日志会让「手机号已改、支付域仍是旧号」
     * 变成无法自愈的静默不一致（这是改造前的实际行为）。</p>
     *
     * <p><b>2026-09-12 起用 {@link RpcOutcome} 的模式匹配取代 boolean</b>（ADR-D45）。
     * 三个分支的处置<b>刻意不同，NEVER 合并</b>：</p>
     * <ul>
     *   <li>{@link RpcOutcome.Ok} → 置 SUCCESS，终态。</li>
     *   <li>{@link RpcOutcome.BizRejected} → <b>一次即终态 + 立即开工单</b>。支付域答复了但拒绝
     *       （{@code PaySignAppController:159} 在 {@code APP_PAY_SIGN_INFO} UPDATE 影响 0 行时返 FAIL，
     *       即「该用户没有签约记录」），重推一万次也不会成功；旧版把它当可重试，补偿队列要白跑
     *       {@code SIGN_SYNC_MAX_RETRY} 轮才开单，期间真实故障被同一条日志淹没。</li>
     *   <li>{@link RpcOutcome.Unreachable} → 置 FAILED、次数 +1，进补偿队列。这才是该重试的一类。</li>
     * </ul>
     *
     * <p>外层 catch 保留改造前的语义：状态回写自身失败（连接被回收、锁等待超时）时按「本轮失败」处理，
     * 让下一轮补偿再试；<b>NEVER 让它逃出去中断整批补偿</b>。RPC 本身已由包装方法收成三态、不再抛异常。</p>
     */
    private boolean syncDisplayAccountToPayDomain(Long changeLogId, String thirdUserId, String displayAccount) {
        LocalDateTime now = LocalDateTime.now();
        RpcOutcome outcome = paySignClient.updateDisplayAccountOutcome(thirdUserId, displayAccount);
        try {
            return switch (outcome) {
                case RpcOutcome.Ok ignored -> {
                    int affected = userPhoneChangeLogMapper.markSignSyncSuccess(changeLogId, now, null);
                    if (affected == 0) {
                        log.warn("签约展示账号已同步但状态回写影响 0 行，可能已被补偿任务改走, changeLogId={}", changeLogId);
                    } else {
                        log.info("同步更新签约展示账号成功, thirdUserId={}, displayAccount={}, changeLogId={}",
                                thirdUserId, displayAccount, changeLogId);
                    }
                    yield true;
                }
                case RpcOutcome.BizRejected rejected -> {
                    String reason = truncateSyncResult("支付域业务拒绝（不可重试）retCode=" + rejected.retCode()
                            + ", retMsg=" + rejected.retMsg());
                    int affected = userPhoneChangeLogMapper.markSignSyncRejected(
                            changeLogId, now, reason, SIGN_SYNC_MAX_RETRY);
                    if (affected == 0) {
                        log.warn("签约展示账号被业务拒绝但状态回写影响 0 行, changeLogId={}", changeLogId);
                    }
                    log.warn("支付域拒绝更新签约展示账号，已置终态并开工单, thirdUserId={}, changeLogId={}, retCode={}, retMsg={}",
                            thirdUserId, changeLogId, rejected.retCode(), rejected.retMsg());
                    openSignSyncTicketQuietly(changeLogId, thirdUserId, SIGN_SYNC_MAX_RETRY,
                            "支付域业务拒绝更新签约展示账号（不可重试）retCode=" + rejected.retCode()
                                    + "，changeLogId=" + changeLogId
                                    + "，请人工核对该用户在支付域是否存在签约记录后订正并关单");
                    yield false;
                }
                case RpcOutcome.Unreachable unreachable -> {
                    markSignSyncFailedQuietly(changeLogId, now,
                            truncateSyncResult("未获业务答复（可重试）: " + unreachable.cause().getMessage()));
                    log.warn("同步更新签约展示账号未获答复，已置 FAILED 待补偿, thirdUserId={}, displayAccount={}, changeLogId={}",
                            thirdUserId, displayAccount, changeLogId, unreachable.cause());
                    yield false;
                }
            };
        } catch (Exception e) {
            markSignSyncFailedQuietly(changeLogId, now, truncateSyncResult("同步状态回写异常: " + e.getMessage()));
            log.warn("签约展示账号同步的状态回写异常，已按本轮失败处理, changeLogId={}", changeLogId, e);
            return false;
        }
    }

    /**
     * 把失败状态落库，<b>本方法自身 NEVER 向外抛异常</b>。
     *
     * <p>它被 {@code syncDisplayAccountToPayDomain} 的 catch 块调用，而后者跑在
     * {@link #compensateSignSync} 的循环里：若这条 UPDATE 自己抛了 {@code DataAccessException}
     * （连接被回收、锁等待超时等），异常会逃出 catch 块并中断**整批**补偿，与
     * 「单条失败 NEVER 中断整批」的约定相反。落库失败只记 ERROR，让本行下一轮再试。</p>
     */
    private void markSignSyncFailedQuietly(Long changeLogId, LocalDateTime now, String reason) {
        try {
            int affected = userPhoneChangeLogMapper.markSignSyncFailed(changeLogId, now, reason);
            if (affected == 0) {
                log.warn("签约展示账号同步失败且状态回写影响 0 行, changeLogId={}", changeLogId);
            }
        } catch (Exception e) {
            log.error("签约展示账号同步失败状态回写异常，NEVER 因此中断补偿批次, changeLogId={}", changeLogId, e);
        }
    }

    /**
     * 补偿扫表重推，由 web-admin 的 Quartz 任务经 {@code POST /phoneSignSyncCompensate} 触发。
     *
     * <p>NEVER 在本类加 {@code @Scheduled} —— 调度源只有 web-admin 的 {@code sys_job} 一处；
     * 也 NEVER 加 {@code @Transactional} —— 本方法逐条发 RPC。</p>
     *
     * <p><b>循环骨架已收口到 {@link OutboxScan#run}</b>（2026-09-12，ADR-D46）：
     * 「单条失败 NEVER 中断整批」「每行只计一次」「投递与失败处理抛异常都要兜住」三条不变量
     * 在那里有唯一定义，规范见 {@code docs/domain/outbox.md}。
     * <b>NEVER 把循环抄回本方法</b>——它已经是第二次被推导了。</p>
     */
    @Override
    public SignSyncCompensateResult compensateSignSync() {
        List<UserPhoneChangeLog> pending =
                userPhoneChangeLogMapper.selectPendingSignSync(SIGN_SYNC_MAX_RETRY, SIGN_SYNC_SCAN_LIMIT);
        if (pending == null || pending.isEmpty()) {
            log.info("签约展示账号补偿扫表无待处理记录, maxRetry={}, limit={}",
                    SIGN_SYNC_MAX_RETRY, SIGN_SYNC_SCAN_LIMIT);
            return new SignSyncCompensateResult(0, 0, 0);
        }
        OutboxScan.Result scan = OutboxScan.run(pending,
                row -> syncDisplayAccountToPayDomain(row.getId(), row.getThirdUserId(), row.getNewMsisdn()),
                this::openTicketIfRetryExhausted,
                (row, e) -> log.error("签约展示账号补偿单条异常，NEVER 因此中断整批, changeLogId={}", row.getId(), e));
        log.info("签约展示账号补偿扫表完成, scanned={}, success={}, failed={}",
                scan.scanned(), scan.success(), scan.failed());
        return new SignSyncCompensateResult(scan.scanned(), scan.success(), scan.failed());
    }

    /**
     * 本次重推也失败后，若重试次数已达上限就开一张异常工单。
     *
     * <p>判据是 {@code 本次扫到的次数 + 1 >= SIGN_SYNC_MAX_RETRY}：{@code markSignSyncFailed} 已在
     * {@code syncDisplayAccountToPayDomain} 里把次数 +1，而这里拿到的 {@code row} 是**扫表那一刻的快照**，
     * 所以要自己补上这个 +1。<b>NEVER 改成 {@code >} 或不补 +1</b> —— 前者永远开不出单
     * （达上限的行下一轮就被 {@code selectPendingSignSync} 的 {@code < maxRetry} 过滤掉、再也扫不到），
     * 后者会提前一轮开单。</p>
     *
     * <p><b>整个方法 NEVER 向外抛异常</b>：它跑在补偿循环里，抛出去会中断整批、让后面的行一起失联。
     * 重复开单属于正常路径（同一笔第二次达上限），只记 debug；判重 MUST 走
     * {@link #isDuplicateKeyViolation}，<b>NEVER 直接 catch (DuplicateKeyException)</b> ——
     * mapper 异常已被 {@code MapperAspectToTrace} 包成 {@code RuntimeException}，单层类型判断捕不到。
     * 开单因其它原因失败时 MUST 把原因写回 {@code SIGN_SYNC_RESULT}：该行已达上限、下一轮不再被
     * {@code selectPendingSignSync} 扫到，只留一条 ERROR 日志会让它彻底失联。</p>
     */
    private void openTicketIfRetryExhausted(UserPhoneChangeLog row) {
        int retriedAfterThisRound = (row.getSignSyncRetryCount() == null ? 0 : row.getSignSyncRetryCount()) + 1;
        if (retriedAfterThisRound < SIGN_SYNC_MAX_RETRY) {
            return;
        }
        openSignSyncTicketQuietly(row.getId(), row.getThirdUserId(), retriedAfterThisRound,
                "签约展示账号同步重推达上限 " + SIGN_SYNC_MAX_RETRY
                        + " 次仍失败，changeLogId=" + row.getId()
                        + "，请人工核对支付域签约展示账号后手工订正并关单");
    }

    /**
     * 开一张「签约展示账号未同步」工单，<b>本方法自身 NEVER 向外抛异常</b>。
     *
     * <p>两个调用方共用：{@link #openTicketIfRetryExhausted}（重试耗尽）与
     * {@code syncDisplayAccountToPayDomain} 的 {@code BizRejected} 分支（一次即终态）。
     * 两者<b>刻意共用同一个工单类型</b> {@code TYPE_SIGN_SYNC_RETRY_EXHAUSTED}：类型是唯一键
     * {@code UK_ACCT_EXC_TICKET_TYPE_KEY} 的一部分，新增类型要连带改运营后台的类型口径；
     * 而两类对运维是同一个动作（核对支付域签约记录后订正并关单），区别写在 {@code DETAIL} 里就够。
     * <b>NEVER 为「业务拒绝」新增工单类型</b>。</p>
     *
     * <p>幂等靠该唯一索引，同一 {@code changeLogId} 重复触发只会有一张；判重 MUST 走
     * {@link #isDuplicateKeyViolation}，<b>NEVER 直接 catch (DuplicateKeyException)</b>。
     * 开单因其它原因失败时 MUST 把原因写回 {@code SIGN_SYNC_RESULT}：该行已是终态、
     * 不再被 {@code selectPendingSignSync} 扫到，只留一条 ERROR 日志会让它彻底失联。</p>
     */
    private void openSignSyncTicketQuietly(Long changeLogId, String thirdUserId, int retryCount, String detail) {
        try {
            AccountExceptionTicket ticket = new AccountExceptionTicket();
            ticket.setTicketType(AccountExceptionTicket.TYPE_SIGN_SYNC_RETRY_EXHAUSTED);
            ticket.setBizKey(String.valueOf(changeLogId));
            ticket.setThirdUserId(thirdUserId);
            ticket.setTicketStatus(AccountExceptionTicket.STATUS_OPEN);
            ticket.setRetryCount(retryCount);
            ticket.setDetail(detail);
            ticket.setCreateTms(LocalDateTime.now());
            accountExceptionTicketMapper.insert(ticket);
            log.warn("签约展示账号同步已开异常工单, changeLogId={}, thirdUserId={}, retryCount={}",
                    changeLogId, thirdUserId, retryCount);
        } catch (Exception e) {
            if (isDuplicateKeyViolation(e)) {
                log.debug("签约展示账号同步异常工单已存在，跳过, changeLogId={}", changeLogId);
                return;
            }
            log.error("开异常工单失败，NEVER 因此中断补偿批次, changeLogId={}", changeLogId, e);
            markSignSyncFailedQuietly(changeLogId, LocalDateTime.now(),
                    truncateSyncResult("需人工介入但异常工单开立失败: " + e.getMessage()));
        }
    }

    /** SIGN_SYNC_RESULT 为 VARCHAR2(1024 CHAR)，超长在此截断，NEVER 让落状态因超长而失败。 */
    private String truncateSyncResult(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= SIGN_SYNC_RESULT_MAX_LENGTH
                ? message
                : message.substring(0, SIGN_SYNC_RESULT_MAX_LENGTH);
    }

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     *
     * <p><b>MUST</b> 逐层遍历 cause，<b>NEVER</b> 直接 {@code catch (DuplicateKeyException)}：
     * {@code MapperAspectToTrace}（{@code resource/micro/web/src/main/java/com/chinasofti/huateng/
     * micro/monitor/trace/MapperAspectToTrace.java:51}）把 mapper 抛出的任何异常统一包成
     * {@code RuntimeException}，单层类型判断在本项目里捕不到。</p>
     *
     * <p>与 {@code AccountApplicationServiceImpl} 里的同名方法是**有意的重复**：它只有 6 行、
     * 不依赖任何成员，为它新建工具类违反 AGENTS.md §5.1「NEVER 主动创建新的工具类」。</p>
     */
    private boolean isDuplicateKeyViolation(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
