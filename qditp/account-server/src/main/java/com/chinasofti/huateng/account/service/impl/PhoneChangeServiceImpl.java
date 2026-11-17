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


@Service
public class PhoneChangeServiceImpl implements PhoneChangeService {
    private static final Logger log = LoggerFactory.getLogger(PhoneChangeServiceImpl.class);


    private static final String SIGN_SYNC_PENDING = SyncStatus.PENDING.name();


    private static final int SIGN_SYNC_RESULT_MAX_LENGTH = 1024;


    private static final int SIGN_SYNC_MAX_RETRY = 10;


    private static final int SIGN_SYNC_SCAN_LIMIT = 200;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserPhoneChangeLogMapper userPhoneChangeLogMapper;


    private final UserAccEmployeeCardMapper userAccEmployeeCardMapper;


    private final AccountExceptionTicketMapper accountExceptionTicketMapper;

    private final PaySignClient paySignClient;


    private final TransactionTemplate transactionTemplate;


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


    private enum PhoneChangeOutcome {

        UPDATED,

        UNCHANGED,

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


    private PhoneChangeLocal updatePhoneLocally(String thirdUserId, String newMsisdn) {
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


    private String truncateSyncResult(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= SIGN_SYNC_RESULT_MAX_LENGTH
                ? message
                : message.substring(0, SIGN_SYNC_RESULT_MAX_LENGTH);
    }


    private boolean isDuplicateKeyViolation(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
