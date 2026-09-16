package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.service.AccountArchiveService;
import com.chinasofti.huateng.account.service.AccountCancelService;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF8A-42 用户销户实现，见 {@link AccountCancelService}。
 *
 * <p>2026-09-11 第六轮拆分从 {@code AccountApplicationServiceImpl}（已删除）逐行搬来，行为不变。</p>
 */
@Service
public class AccountCancelServiceImpl implements AccountCancelService {
    private static final Logger log = LoggerFactory.getLogger(AccountCancelServiceImpl.class);

    /**
     * {@code USER_ITP_REG_LOG.OPER_TYPE} 取值。该列无字典表也无 DDL 注释，
     * 现存唯一用法是解约写 1（见 {@code PayChannelServiceImpl.requestRemovePayChannel}，
     * 2026-09-11 第四轮拆分后常量 1 也随之搬去那边），销户取 2，销户归档取 3。
     * 新增取值 <b>MUST</b> 在此登记并同步 {@code docs/business/account-employee-card.md}。
     */
    private static final int OPER_TYPE_USER_CANCEL = 2;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserItpRegLogMapper userItpRegLogMapper;

    /**
     * 销户归档的协作者。<b>销户侧只用 tryArchiveAfterCancel</b>（自开短事务、吞异常只 warn），
     * NEVER 换成解绑侧那个不吞异常的入口。
     */
    private final AccountArchiveService accountArchiveService;

    /** 销户前查 IF8A-35 未结清订单用。本类唯一的跨服务调用。 */
    private final GateTxnPayClient gateTxnPayClient;

    /**
     * {@code userCancel} 不带 `@Transactional`（内部先发 RPC），落库部分用它显式开短事务。
     * <b>NEVER</b> 改成给方法加事务注解。
     */
    private final TransactionTemplate transactionTemplate;

    /** 销户前是否调 IF8A-35 校验未结清订单。关掉即退化为「只登记不校验」。 */
    @Value("${app.user-cancel.check-unsettled:true}")
    private boolean checkUnsettledBeforeCancel;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public AccountCancelServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper,
                                    UserItpRegLogMapper userItpRegLogMapper,
                                    AccountArchiveService accountArchiveService,
                                    GateTxnPayClient gateTxnPayClient,
                                    TransactionTemplate transactionTemplate) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userItpRegLogMapper = userItpRegLogMapper;
        this.accountArchiveService = accountArchiveService;
        this.gateTxnPayClient = gateTxnPayClient;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * IF8A-42 用户销户。<b>本方法故意不带 `@Transactional`</b>：内部要先调 IF8A-35（RPC），
     * 事务包住网络调用会把行锁持有时长拉长到对端响应时长（AGENTS.md §5.2 已有 8 分钟锁等待事故）。
     * 落库部分用 {@link #transactionTemplate} 单独开事务。
     */
    @Override
    public UserCancelResult userCancel(UserCancelReqDTO request) {
        UserCancelResult response = new UserCancelResult();
        String rawThirdUserId = request == null ? null : request.getThirdUserId();
        String thirdUserId = StringUtils.hasText(rawThirdUserId) ? rawThirdUserId.trim() : null;
        if (thirdUserId == null) {
            response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("thirdUserId不能为空");
            log.warn("IF8A-42销户参数校验失败, request={}", JSON.toJSONString(request));
            return response;
        }
        log.info("开始处理IF8A-42用户销户, thirdUserId={}", thirdUserId);

        List<UserItpRegInfo> activeCards = userItpRegInfoMapper.selectActiveListByThirdUserId(thirdUserId);
        if (activeCards == null || activeCards.isEmpty()) {
            // 幂等：已注销用户重复调用返回成功。APP 对失败会重试，此处报错会让注销流程永久卡住
            // 同时补一次归档尝试：若该用户的通道早于销户就已解绑完，75 那条归档路径不会再被触发，
            // 重复调本接口即可把残留收口（归档内部三条件不满足时原样返回，不动数据）
            accountArchiveService.tryArchiveAfterCancel(thirdUserId);
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-42销户无有效开户记录, 按已注销幂等返回成功, thirdUserId={}", thirdUserId);
            return response;
        }

        if (checkUnsettledBeforeCancel) {
            String rejectCode = checkUnsettledOrder(thirdUserId);
            if (rejectCode != null) {
                response.setRetCode(rejectCode);
                response.setRetMsg(rejectCode.equals(AccountErrorCodeEnum.UNSETTLED_ORDER_EXISTS.getCode())
                        ? AccountErrorCodeEnum.UNSETTLED_ORDER_EXISTS.getMsg()
                        : AccountErrorCodeEnum.ACC_INFO_QUERY_FAIL.getMsg());
                return response;
            }
        } else {
            log.warn("IF8A-42销户已关闭未结清订单校验(app.user-cancel.check-unsettled=false), thirdUserId={}", thirdUserId);
        }

        try {
            Integer canceled = transactionTemplate.execute(status -> doCancelUserCards(thirdUserId, activeCards));
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-42销户成功, thirdUserId={}, 本次注销票卡数={}, 支付通道未删除(留给IF8A-75解绑)",
                    thirdUserId, canceled);
            // 销户提交后再试一次归档：正常顺序（35→42→75）下此时通道还在，归档会自行跳过；
            // 但若通道在销户前就已全部解绑，这里就是唯一的归档时点
            accountArchiveService.tryArchiveAfterCancel(thirdUserId);
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-42用户销户异常, thirdUserId={}", thirdUserId, e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 销户前调 IF8A-35 查未支付 / 扣费失败订单数。
     *
     * <p><b>只校验账务，NEVER 加「进行中行程 / 未出站」校验</b>：用户 2026-09-11 明确裁决
     * 「销户不校验进行中行程」，见 {@code docs/domain/decisions.md} ADR-D20。
     * 进站未出站的场景由本方法的 {@code 8023} 欠费分支间接兜住；
     * 账户域也不应反向直读行程域的 {@code QRCODE_STATUS}。</p>
     *
     * @return {@code null} 表示允许销户；否则返回应拒绝的错误码
     */
    private String checkUnsettledOrder(String thirdUserId) {
        RequestUserAccInfoResult accInfo;
        try {
            RequestUserAccInfoReqDTO accInfoReq = new RequestUserAccInfoReqDTO();
            accInfoReq.setThirdUserId(thirdUserId);
            accInfo = gateTxnPayClient.requestUserAccInfo(accInfoReq);
        } catch (Exception e) {
            // fail-closed：查不到账务状态时 NEVER 放行销户，否则欠费用户注销后无法追缴
            log.error("IF8A-42销户前查询账务信息异常, 拒绝销户, thirdUserId={}", thirdUserId, e);
            return AccountErrorCodeEnum.ACC_INFO_QUERY_FAIL.getCode();
        }
        if (accInfo == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(accInfo.getRetCode())) {
            // IF8A-35 查询未执行时两个数量恒为 0，MUST 先判 retCode，NEVER 直接当成「无欠费」
            log.warn("IF8A-42销户前查询账务信息未成功, 拒绝销户, thirdUserId={}, result={}",
                    thirdUserId, JSON.toJSONString(accInfo));
            return AccountErrorCodeEnum.ACC_INFO_QUERY_FAIL.getCode();
        }
        if (accInfo.getUnpaidCount() > 0 || accInfo.getFailureCount() > 0) {
            log.warn("IF8A-42销户被拒绝, 存在未结清订单, thirdUserId={}, unpaidCount={}, failureCount={}",
                    thirdUserId, accInfo.getUnpaidCount(), accInfo.getFailureCount());
            return AccountErrorCodeEnum.UNSETTLED_ORDER_EXISTS.getCode();
        }
        log.info("IF8A-42销户前账务校验通过, thirdUserId={}", thirdUserId);
        return null;
    }

    /**
     * 销户落库：注销开户记录 + 每张卡写一条操作日志。由 {@link #transactionTemplate} 包事务调用。
     *
     * <p><b>NEVER 在此删 USER_PAY_CHANNEL</b>：APP 顺序是 35 → 42 → 75，
     * 支付渠道要留给随后的 IF8A-75 逐个解绑。</p>
     *
     * <p><b>注销影响 0 行 MUST 抛异常</b>：调用方刚查到非空 {@code activeCards}，0 行只可能是这一瞬被
     * 并发销户。抛出会让 {@code transactionTemplate} 连同下面 N 条 {@code OPER_TYPE=2} 日志一起回滚，
     * 外层 catch 把它翻成 9999 —— 上游重推时会走到「无有效票卡」分支拿到确定答复。
     * <b>NEVER 改成只记日志后继续</b>：那会留下「日志说销过户、{@code DEL_YN} 却没变」的痕迹，
     * 且对外仍返 0000，事后连排查线索都是错的。</p>
     */
    private int doCancelUserCards(String thirdUserId, List<UserItpRegInfo> activeCards) {
        LocalDateTime now = LocalDateTime.now();
        int canceled = userItpRegInfoMapper.updateCancelByThirdUserId(thirdUserId, now);
        if (canceled == 0) {
            throw new IllegalStateException("销户注销影响0行（并发销户？）, thirdUserId=" + thirdUserId);
        }
        for (UserItpRegInfo card : activeCards) {
            UserItpRegLog regLog = new UserItpRegLog();
            regLog.setCardId(card.getCardId());
            regLog.setCardType(card.getCardType());
            regLog.setThirdUserId(thirdUserId);
            regLog.setMsisdn(card.getMsisdn());
            regLog.setOperDateTime(now);
            regLog.setOperType(OPER_TYPE_USER_CANCEL);
            userItpRegLogMapper.insert(regLog);
        }
        return canceled;
    }
}
