package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.domain.ChannelBindingRule;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserItpRegLog;
import com.chinasofti.huateng.account.entity.UserPayChannel;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import com.chinasofti.huateng.account.mapper.UserItpRegLogMapper;
import com.chinasofti.huateng.account.mapper.UserPayChannelMapper;
import com.chinasofti.huateng.account.service.AccountArchiveService;
import com.chinasofti.huateng.account.service.PayChannelService;

/**
 * 支付通道的增删改查实现，见 {@link PayChannelService}。
 *
 * <p>2026-09-11 第四轮拆分从 {@code AccountApplicationServiceImpl} 整段搬来，<b>逐行照搬、行为不变</b>：
 * IF8A-23 / 24 / 75 / 77 与钱包 {@code requestAgreeRelease}、按签约号反查通道，共 6 个入口。</p>
 *
 * <p><b>NEVER 把开户发号或销户逻辑挪进本类</b>：本类只管 {@code APP_USER_PAY_CHANNEL} 与
 * {@code USER_ITP_REG_INFO} 上的默认通道字段；「最后一个渠道解绑后归档」这一派生规则仍由
 * {@link AccountArchiveServiceImpl} 负责，本类只在解绑成功后调它。</p>
 */
@Service
public class PayChannelServiceImpl implements PayChannelService {
    private static final Logger log = LoggerFactory.getLogger(PayChannelServiceImpl.class);

    /**
     * {@code USER_ITP_REG_LOG.OPER_TYPE} 的解约取值。取值表登记在
     * {@code AccountApplicationServiceImpl} 的同名常量注释里，新增取值 <b>MUST</b> 两处同步。
     */
    private static final int OPER_TYPE_REMOVE_PAY_CHANNEL = 1;

    /** IF8A-77 反查签约信息取 PAY_ACCOUNT_ID 用；本类唯一的跨服务调用。 */
    private final PaySignClient paySignClient;

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    private final UserItpRegLogMapper userItpRegLogMapper;

    private final UserPayChannelMapper userPayChannelMapper;

    /**
     * 销户归档的协作者。<b>只用同事务入口 archiveIfLastChannelRemoved</b>：
     * 解绑侧要求归档失败即整单回滚，NEVER 换成吞异常的 tryArchiveAfterCancel。
     */
    private final AccountArchiveService accountArchiveService;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public PayChannelServiceImpl(PaySignClient paySignClient,
                                 UserItpRegInfoMapper userItpRegInfoMapper,
                                 UserItpRegLogMapper userItpRegLogMapper,
                                 UserPayChannelMapper userPayChannelMapper,
                                 AccountArchiveService accountArchiveService) {
        this.paySignClient = paySignClient;
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userItpRegLogMapper = userItpRegLogMapper;
        this.userPayChannelMapper = userPayChannelMapper;
        this.accountArchiveService = accountArchiveService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestAddPayChannelResult requestAddPayChannel(RequestAddPayChannelReqDTO request) {
        RequestAddPayChannelResult response = new RequestAddPayChannelResult();
        try {
            log.info("开始处理IF8A-23请求添加支付通道, request={}", JSON.toJSONString(request));
            String validMsg = validateAddPayChannelRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-23参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String issueCardType = CardTypeMapping.toIssueCardType(request.getCardType().trim());
            String thirdUserId = request.getThirdUserId().trim();
            String channel = request.getChannel().trim();

            // 钱包渠道要求请求里的卡与开户信息严格一致，且同一 thirdUserId 可能有多条有效开户记录（多卡），
            // 因此 MUST 按 cardId + cardType 精确定位，NEVER 取「最新一条」再比对。
            // 非钱包渠道的卡信息比对历史上是关闭的（见下方注释块），这里保持原有的用户维度判断，不收紧校验。
            UserItpRegInfo regInfo;
            if (ChannelBindingRule.isWallet(channel)) {
                regInfo = userItpRegInfoMapper.selectActiveByThirdUserIdAndCardIdAndCardType(
                        thirdUserId, request.getCardId().trim(), issueCardType);
                if (regInfo == null || !regInfo.isActive()) {
                    response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                    response.setRetMsg("钱包 cardId 或 cardType 与开户信息不匹配");
                    log.warn("IF8A-23钱包卡信息不匹配, request={}, issueCardType={}",
                            JSON.toJSONString(request), issueCardType);
                    return response;
                }
            } else {
                regInfo = userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId);
                if (regInfo == null || !regInfo.isActive()) {
                    response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                    log.warn("IF8A-23未找到有效用户账户, thirdUserId={}", request.getThirdUserId());
                    return response;
                }
                log.info("请求转换过后的cardType:{},已注册信息cardType:{},比对结果:{}", issueCardType,
                        regInfo.getCardType(), issueCardType.equals(regInfo.getCardType()));
            }
//            if (!issueCardType.equals(regInfo.getCardType())
//                    || !request.getCardId().trim().equals(regInfo.getCardId())) {
//                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
//                response.setRetMsg("cardId或cardType与开户信息不匹配");
//                log.warn("IF8A-23卡信息不匹配, request={}, regInfoCardId={}, regInfoCardType={}",
//                        JSON.toJSONString(request), regInfo.getCardId(), regInfo.getCardType());
//                return response;
//            }

            UserPayChannel existed = userPayChannelMapper.selectByThirdUserIdAndCardTypeAndChannel(
                    thirdUserId,
                    issueCardType,
                    channel);
            if (existed != null) {
                response.setRetCode(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getMsg());
                log.warn("IF8A-23支付通道已存在, thirdUserId={}, cardType={}, channel={}",
                        request.getThirdUserId(), issueCardType, request.getChannel());
                return response;
            }

            UserPayChannel record = buildUserPayChannel(request);
            record.setCardType(issueCardType);
            userPayChannelMapper.insert(record);

            // 钱包开户后直接添加支付渠道，不经过 requestSignInfo/requestSetDefaultPayChannel。
            // 传统签约渠道继续保持原有的显式设置默认通道流程。
            if (ChannelBindingRule.isWallet(channel)) {
                regInfo.setThirdPayId(record.getThirdPayId());
                regInfo.setChannel(channel);
                // 2026-09-15 起 NEVER 再把钱包的 REQ_CONTRACT_NO 置 null。
                // 原实现在这里写 null，前提是「钱包不签约、扣款只靠 thirdPayId」；该前提已被支付中心实测推翻
                // （§1.1 requestPay 的 withholding 场景强制要求 requestSignSeq，只送 payUserId 时网关返
                // code=9999「代扣签约请求流水号不能为空」），而支付域取 requestSignSeq 的路径就是
                // account-server 回答的 reqContractNo（PaymentDomainServiceImpl.applyAccountUserView）。
                // 置 null 等于把钱包扣款所需的签约流水号在源头擦掉。
                regInfo.setReqContractNo(record.getReqContractNo());
                if (userItpRegInfoMapper.updateDefaultPayChannelById(regInfo) <= 0) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
                    response.setRetMsg("钱包默认支付通道设置失败");
                    return response;
                }
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-23添加支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}, thirdPayId={}, reqContractNo={}",
                    record.getThirdUserId(), record.getCardId(), record.getCardType(),
                    record.getChannel(), record.getThirdPayId(), record.getReqContractNo());
            scheduleWalletContractSignup(record);
            scheduleBackfillPayAccountId(record.getReqContractNo());
            return response;
        } catch (Exception e) {
            if (isDuplicateKeyViolation(e)) {
                response.setRetCode(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getCode());
                response.setRetMsg(AccountErrorCodeEnum.ADD_PAY_CHANNEL_DUPLICATE.getMsg());
                log.warn("IF8A-23支付通道唯一约束冲突, request={}", JSON.toJSONString(request), e);
                return response;
            }
            log.error("处理IF8A-23请求添加支付通道异常, request={}", JSON.toJSONString(request), e);
            // catch 掉异常后 rollbackFor 不会触发，MUST 显式标记回滚：通道行已 insert（148 行）而
            // 钱包默认通道 update（156 行）抛异常时，不标记就会提交「通道已加、默认通道未设」的半成品，
            // 同时对上游报 SYSTEM_ERROR；上游重推只会拿到「通道已存在」，该用户永久停在半成品状态。
            markRollbackOnly();
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     *
     * <p><b>MUST</b> 逐层遍历 cause，<b>NEVER</b> 直接 {@code catch (DuplicateKeyException)}：
     * {@code MapperAspectToTrace}（{@code resource/micro/web/src/main/java/com/chinasofti/huateng/
     * micro/monitor/trace/MapperAspectToTrace.java:51}）把 mapper 抛出的任何异常统一包成
     * {@code RuntimeException}，单层类型判断在本项目里捕不到。</p>
     */
    private boolean isDuplicateKeyViolation(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(RequestSetDefaultPayChannelReqDTO request) {
        RequestSetDefaultPayChannelResult response = new RequestSetDefaultPayChannelResult();
        try {
            log.info("开始处理IF8A-24请求设置默认支付通道, request={}", JSON.toJSONString(request));
            String validMsg = validateSetDefaultPayChannelRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-24参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardId = request.getCardId().trim();
            String cardType = CardTypeMapping.toIssueCardType(request.getCardType().trim());
            String channel = request.getChannel().trim();

            // 同一 thirdUserId 允许存在多条有效开户记录（多卡），MUST 按 cardId + cardType 精确定位，
            // NEVER 用 selectActiveByThirdUserId 取「最新一条」再比对——多卡用户必然张冠李戴
            UserItpRegInfo regInfo = userItpRegInfoMapper
                    .selectActiveByThirdUserIdAndCardIdAndCardType(thirdUserId, cardId, cardType);
            if (regInfo == null || !regInfo.isActive()) {
                // 区分「该用户没有任何有效账户」与「有账户但这张卡不属于他」，保持原有错误码语义
                if (userItpRegInfoMapper.selectActiveByThirdUserId(thirdUserId) == null) {
                    response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                    log.warn("IF8A-24未找到有效用户账户, thirdUserId={}", thirdUserId);
                } else {
                    response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                    response.setRetMsg("cardId或cardType与开户信息不匹配");
                    log.warn("IF8A-24卡信息不匹配, request={}, cardId={}, cardType={}",
                            JSON.toJSONString(request), cardId, cardType);
                }
                return response;
            }

            UserPayChannel payChannel = userPayChannelMapper.selectByThirdUserIdAndCardTypeAndChannel(thirdUserId, cardType, channel);
            if (payChannel == null) {
                response.setRetCode(AccountErrorCodeEnum.PAY_CHANNEL_NOT_FOUND.getCode());
                response.setRetMsg(AccountErrorCodeEnum.PAY_CHANNEL_NOT_FOUND.getMsg());
                log.warn("IF8A-24支付通道不存在, thirdUserId={}, cardType={}, channel={}", thirdUserId, cardType, channel);
                return response;
            }

            regInfo.setThirdPayId(payChannel.getThirdPayId());
            regInfo.setChannel(payChannel.getChannel());
            regInfo.setReqContractNo(payChannel.getReqContractNo());
            int updated = userItpRegInfoMapper.updateDefaultPayChannelById(regInfo);
            if (updated <= 0) {
                response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
                response.setRetMsg(AccountErrorCodeEnum.FAIL.getMsg());
                log.warn("IF8A-24更新默认支付通道失败, thirdUserId={}, regInfoId={}", thirdUserId, regInfo.getId());
                return response;
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-24设置默认支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}, thirdPayId={}, reqContractNo={}",
                    thirdUserId, cardId, cardType, payChannel.getChannel(), payChannel.getThirdPayId(), payChannel.getReqContractNo());
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-24请求设置默认支付通道异常, request={}", JSON.toJSONString(request), e);
            // 与本类另外两个事务方法（requestAddPayChannel / requestRemovePayChannel）保持同一条规则：
            // catch 掉异常后 rollbackFor 不再触发，MUST 显式标记回滚。
            // 本方法当前只有一条业务 UPDATE，标记与不标记的可观察差异极小，但语义上必须对齐——
            // 「对上游报 SYSTEM_ERROR」与「库里已提交改动」NEVER 同时成立。
            // 这里是 ADR-D38 的落点：不标记时，一旦将来在 260 行之后追加第二条写，
            // 失败就会静默提交前半段，且编译与单测都发现不了。
            markRollbackOnly();
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * IF8A-77。<b>本方法故意不带 `@Transactional`</b>：方法体内要调 pay-sign 的
     * {@code querySignInfoBySeq}（RPC），而 AGENTS.md §5.2 禁止事务包住网络调用——
     * 事务内调远端会把行锁持有时长拉长到对端响应时长，2026-08-26 生产已因此出过 8 分钟锁等待事故。
     *
     * <p>去掉事务是安全的：写操作只有 {@code updateChannelDefaultContractById} <b>一条业务 UPDATE</b>，
     * 单语句自身就是原子的，不存在需要一起回滚的第二个写。<b>NEVER 因为「看起来该有事务」把注解加回来</b>；
     * 若将来这里真的要写第二张表<b>且两条写必须一起成立</b>，MUST 用 {@code TransactionTemplate}
     * 只把两条写包进去，并把 RPC 留在事务之外。</p>
     *
     * <p><b>2.0.63 新增的 {@code PAY_ACCOUNT_ID} 回写不属于上述情形</b>：它把支付域返回的付款账号
     * 落到 {@code APP_USER_PAY_CHANNEL} 上，纯粹是为了让运营页面本地可读（ADR-D30），
     * <b>允许失败、允许影响 0 行</b>，因此**故意放在业务 UPDATE 成功之后、且不与之同事务**。
     * 把它包进事务反而更糟：一个展示列写失败会连带回滚已经成立的默认支付方式变更。</p>
     */
    @Override
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(RequestUpdateChannelDefaultContractReqDTO request) {
        RequestUpdateChannelDefaultContractResult response = new RequestUpdateChannelDefaultContractResult();
        try {
            log.info("开始处理IF8A-77更换第三方渠道码默认支付方式, request={}", JSON.toJSONString(request));
            
            // 1. 参数校验
            String validMsg = validateUpdateChannelDefaultContractRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("IF8A-77参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String channel = request.getChannel().trim();
            String cardIssueCode = request.getCardIssueCode().trim();
            String regSignSeq = request.getRegSignSeq().trim();

            // 2. 根据 thirdUserId、cardIssueCode 和 COMPANION_FLAG='C' 查询用户注册信息
            UserItpRegInfo regInfo = userItpRegInfoMapper.selectByThirdUserIdAndCardIssueCodeAndCompanionFlag(
                    thirdUserId, cardIssueCode, "C");
            if (regInfo == null || !regInfo.isActive()) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("IF8A-77未找到有效第三方渠道用户, thirdUserId={}, cardIssueCode={}", thirdUserId, cardIssueCode);
                return response;
            }

            // 3. 根据 regSignSeq 查询签约信息表，获取 PAY_ACCOUNT_ID
            PaySignInfoDTO signInfo = paySignClient.querySignInfoBySeq(regSignSeq);
            if (signInfo == null || !StringUtils.hasText(signInfo.getPayAccountId())) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_SIGN_DATA.getCode());
                response.setRetMsg("签约信息不存在或PAY_ACCOUNT_ID为空");
                log.warn("IF8A-77签约信息不存在, regSignSeq={}", regSignSeq);
                return response;
            }

            // 4. 更新 USER_ITP_REG_INFO：THIRD_PAY_ID / CHANNEL / REQ_CONTRACT_NO
            regInfo.setThirdPayId(signInfo.getPayAccountId());
            regInfo.setChannel(channel);
            regInfo.setReqContractNo(regSignSeq);
            int updated = userItpRegInfoMapper.updateChannelDefaultContractById(regInfo);
            if (updated <= 0) {
                response.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
                response.setRetMsg(AccountErrorCodeEnum.FAIL.getMsg());
                log.warn("IF8A-77更新用户默认支付方式失败, thirdUserId={}, regInfoId={}", thirdUserId, regInfo.getId());
                return response;
            }

            syncPayAccountIdToChannelQuietly(regSignSeq, signInfo.getPayAccountId());

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("IF8A-77更换第三方渠道码默认支付方式成功, thirdUserId={}, channel={}, cardIssueCode={}, regSignSeq={}, payAccountId={}",
                    thirdUserId, channel, cardIssueCode, regSignSeq, signInfo.getPayAccountId());
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-77更换第三方渠道码默认支付方式异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /** IF8A-75 删除支付通道。事务边界在本方法，实现见 {@link #doRemovePayChannel}。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestRemovePayChannelResult requestRemovePayChannel(RequestRemovePayChannelReqDTO request) {
        return doRemovePayChannel(request);
    }

    /**
     * 钱包协议 requestAgreeRelease：解绑语义与 IF8A-75 删除支付通道完全一致，共用同一段实现。
     *
     * <p><b>本方法 MUST 保留自己的 {@code @Transactional}</b>：它与
     * {@link #requestRemovePayChannel} 是两个<b>平级入口</b>，各自开事务、各自委托给
     * {@link #doRemovePayChannel}。这是 ADR-D39 消除自调用隐患后的形态 ——
     * 此前本方法直接调 {@code requestRemovePayChannel(request)}，那是类内自调用、不过 AOP 代理，
     * 被调方法上的注解在该路径上被完全忽略，正确性只靠「两处注解参数一字不差」这个巧合维持。
     * <b>NEVER 退回让两个 public 入口互相调用</b>。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestRemovePayChannelResult requestAgreeRelease(RequestRemovePayChannelReqDTO request) {
        return doRemovePayChannel(request);
    }

    /**
     * 删除支付通道的唯一实现（ADR-D39 由 {@code requestRemovePayChannel} 原地抽出，逻辑一字未改）。
     *
     * <p><b>本方法 NEVER 加 {@code @Transactional}</b>：它是 private、只可能被同类的两个 public
     * 入口调用，加注解也不会经过代理、纯属误导。事务由调用方开，本方法只负责在其中执行。
     * 内部 {@code markRollbackOnly()} 作用于<b>调用方开的那个事务</b>，两个入口都成立。</p>
     *
     * <p>连带约束：两个入口的事务配置 MUST 保持一致（当前都是 {@code rollbackFor = Exception.class}）。
     * 与改造前的区别在于，现在这是一条<b>可以被违反、且违反后行为会真实改变</b>的约定，
     * 而不是「改了其中一个就静默失效」的陷阱。</p>
     */
    private RequestRemovePayChannelResult doRemovePayChannel(RequestRemovePayChannelReqDTO request) {
        RequestRemovePayChannelResult response = new RequestRemovePayChannelResult();
        try {
            log.info("开始处理删除支付通道, request={}", JSON.toJSONString(request));
            // 先校验再做卡类型映射：cardType 为 null 时 toIssueCardType(cardType.trim()) 会抛 NPE，
            // 冒到全局异常处理器后 retCode 退化成 UUID，调用方（解约回调）只能判定为失败并回滚。
            // 已发生事故：2026-09-09 解约回调 cardId/cardType 均为 null，此处 NPE 导致支付宝已解约、
            // 本地 APP_USER_PAY_CHANNEL 与 APP_PAY_SIGN_INFO 全部回滚，且每次重试都在同一行炸。
            String validMsg = validateRemovePayChannelRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("删除支付通道参数校验失败, msg={}, request={}", validMsg, JSON.toJSONString(request));
                return response;
            }
            request.setCardType(CardTypeMapping.toIssueCardType(request.getCardType().trim()));

            String thirdUserId = request.getThirdUserId().trim();
            String cardId = request.getCardId().trim();
            String cardType = request.getCardType().trim();
            String channel = request.getChannel().trim();

            // 同一 thirdUserId 允许存在多条有效开户记录（多卡），MUST 按 cardId + cardType 精确定位，
            // NEVER 用 selectActiveByThirdUserId 取「最新一条」再比对——多卡用户必然张冠李戴
            UserItpRegInfo regInfo = userItpRegInfoMapper
                    .selectActiveByThirdUserIdAndCardIdAndCardType(thirdUserId, cardId, cardType);
            if (regInfo == null || !regInfo.isActive()) {
                // IF8A-42 销户把 DEL_YN 置 0（0=已注销，1=有效，极性反直觉），而 APP 顺序是 35→42→75：
                // 走到 IF8A-75 强制解绑、由解约成功分支回调本接口清理支付通道时，开户记录已是注销态。
                // 此时若直接返回 8004，支付渠道已解约而本地 APP_USER_PAY_CHANNEL 残留，且无法自愈，
                // 因此 MUST 忽略 DEL_YN 再查一次，把清理动作放行。
                UserItpRegInfo canceledRegInfo = userItpRegInfoMapper
                        .selectAnyByThirdUserIdAndCardIdAndCardType(thirdUserId, cardId, cardType);
                if (canceledRegInfo == null) {
                    response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                    response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                    log.warn("删除支付通道未找到该卡的开户记录, thirdUserId={}, cardId={}, cardType={}",
                            thirdUserId, cardId, cardType);
                    return response;
                }
                regInfo = canceledRegInfo;
                log.info("删除支付通道命中已注销的开户记录，按销户后清理处理, thirdUserId={}, cardId={}, cardType={}, delYn={}",
                        thirdUserId, cardId, cardType, regInfo.getDelYn());
            }

            int removed = userPayChannelMapper.deleteByThirdUserIdAndCardTypeAndChannel(thirdUserId, cardType, channel);
            // 「0 行且该用户仍有通道行」= 键不匹配、本地残留。收口日志 MUST 据此改口径，
            // NEVER 在这种情况下还打「删除支付通道成功」—— 2026-09-14 实测过原写法：
            // 同一次调用里 :467 的 ERROR 说「本地将残留通道」、紧接着收口又说「成功」，
            // 排查的人先看到后者就会判定没事，等于把这条 ERROR 的价值抵消掉。
            boolean channelLeftUncleaned = false;
            if (removed == 0) {
                // MUST 接住影响行数、NEVER 只发语句就返 0000：0 行有两种成因 —— ①本地本来就没有这条
                // 通道行（pay-sign 解约重推的第二次调用，属正常幂等）；②cardType 经 CardTypeMapping
                // 映射后与库里不一致、或 channel 取值不同，属真缺陷、本地会残留通道行且无补偿路径。
                //
                // 这里 MUST 仍返 0000、NEVER 改成返错：调用方 pay-sign 的解约成功分支会重推，
                // 返错会让它反复重试并把已解约的签约卡在非终态。
                //
                // 但「0 行必留一条 WARN」这个判据太弱：它要求人去数「同一 thirdUserId 反复出现」，
                // 而成因②在正常业务下只发生一次（解约成功即终态、不会再来），永远凑不出「反复」。
                // 因此 MUST 当场再查一次该用户的通道条数把两种成因分开 —— 删除语句的 WHERE 是
                // (THIRD_USER_ID, CARD_TYPE, CHANNEL) 精确等值（本表主键），删完还剩行就只能是键不匹配。
                // 这一次多余的 SELECT 只发生在 0 行分支，正常路径零开销。
                int remaining = userPayChannelMapper.countByThirdUserId(thirdUserId);
                if (remaining > 0) {
                    // 成因②：可能的形态之一是历史数据里 CARD_TYPE 存了 APP 的 2 位码（如 02），
                    // 而本方法在 415 行已把入参归一成 4 位发卡码（0441），精确等值必然错开。
                    // 2026-09-14 实测：库里确实有 1 行 CARD_TYPE='02' 的历史残留，但那一行在
                    // USER_ITP_REG_INFO 里没有任何开户记录，因此它会在上面两次 select 处就返 8004、
                    // 走不到这个分支 —— 也就是说本分支至今 NEVER 被真实数据触发过，属预防性护栏。
                    // MUST 打 ERROR：真触发时该用户的支付通道已经清不掉了，且上游会收到 0000、不会重试。
                    log.error("删除支付通道影响0行但该用户仍有{}条通道行，判定为键不匹配（疑似 CARD_TYPE 口径混用），"
                                    + "本地将残留通道且无补偿路径, thirdUserId={}, cardId={}, cardType={}, channel={}",
                            remaining, thirdUserId, cardId, cardType, channel);
                    channelLeftUncleaned = true;
                } else {
                    // 成因①：该用户名下已无任何通道行，属解约重推的第二次调用，正常幂等。
                    log.warn("删除支付通道影响0行且该用户已无通道行，按幂等成功返回, "
                                    + "thirdUserId={}, cardId={}, cardType={}, channel={}",
                            thirdUserId, cardId, cardType, channel);
                }
            }
            if (channel.equals(regInfo.getChannel())) {
                // 这里与上面的 removed 刻意解耦：APP_USER_PAY_CHANNEL 的行与 USER_ITP_REG_INFO 上的
                // 默认通道字段是两处状态，只剩字段没有行时清掉字段才是自愈，NEVER 因为 removed==0 就跳过。
                if (userItpRegInfoMapper.clearDefaultPayChannelById(regInfo.getId()) == 0) {
                    // WHERE 是主键 ID，上面刚查到这一行，0 行只可能是这一瞬被并发销户归档删除。
                    log.warn("清空注册信息默认支付通道影响0行（并发归档？）, thirdUserId={}, regInfoId={}",
                            thirdUserId, regInfo.getId());
                }
                log.info("删除支付通道时清空注册信息默认支付通道, thirdUserId={}, cardType={}, channel={}",
                        thirdUserId, cardType, channel);

                // 仅当清空默认支付通道时，才写入 ITP 解约日志
                UserItpRegLog regLog = new UserItpRegLog();
                regLog.setCardId(cardId);
                regLog.setCardType(cardType);
                regLog.setThirdUserId(thirdUserId);
                regLog.setMsisdn(regInfo.getMsisdn());
                regLog.setOperDateTime(LocalDateTime.now());
                regLog.setOperType(OPER_TYPE_REMOVE_PAY_CHANNEL);
                userItpRegLogMapper.insert(regLog);
            }

            accountArchiveService.archiveIfLastChannelRemoved(thirdUserId);

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            if (channelLeftUncleaned) {
                // 仍返 0000（上游 pay-sign 不能重试，理由见 removed==0 分支的说明），
                // 但收口日志 MUST 与上面那条 ERROR 口径一致，NEVER 说「成功」。
                log.warn("删除支付通道按幂等返回0000，但本地通道未清理（见上条 ERROR）, "
                                + "thirdUserId={}, cardId={}, cardType={}, channel={}",
                        thirdUserId, cardId, cardType, channel);
            } else {
                log.info("删除支付通道成功, thirdUserId={}, cardId={}, cardType={}, channel={}",
                        thirdUserId, cardId, cardType, channel);
            }
            return response;
        } catch (Exception e) {
            log.error("处理删除支付通道异常, request={}", JSON.toJSONString(request), e);
            // catch 掉异常后 rollbackFor 不会触发，MUST 显式标记回滚，否则「删了通道但归档失败」会被提交。
            // 用户 2026-09-08 裁决：归档失败即整体回滚返错误码，让上游重试，NEVER 留半成品状态。
            markRollbackOnly();
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /** catch 内显式标记事务回滚；不在事务上下文里时静默跳过。 */
    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // 非事务调用（如单测直接调 service），无需回滚
        }
    }

    /**
     * 把支付域返回的 {@code PAY_ACCOUNT_ID} 回写到 {@code APP_USER_PAY_CHANNEL}（ADR-D30）。
     *
     * <p><b>本方法 NEVER 抛异常、NEVER 影响 IF8A-77 的返回码</b>：这一列只服务于运营页面的
     * 「支付账号」展示列，写不进去的后果仅是页面显示 {@code -}，而默认支付方式的变更**已经成立**。
     * 把它升级成失败会让一个展示问题冒充业务失败。</p>
     *
     * <p>影响 0 行是**正常情形**（该签约流水在通道表没有对应行），只记 INFO 不告警。</p>
     */
    private void syncPayAccountIdToChannelQuietly(String reqContractNo, String payAccountId) {
        try {
            int updated = userPayChannelMapper.updatePayAccountIdByReqContractNo(
                    reqContractNo, payAccountId, LocalDateTime.now());
            if (updated <= 0) {
                log.info("IF8A-77按签约流水未命中支付通道行，PAY_ACCOUNT_ID未回写, regSignSeq={}", reqContractNo);
            } else if (updated > 1) {
                // REQ_CONTRACT_NO 上没有唯一索引，一条签约流水理论上可能落在多行通道上。
                // 真发生说明数据已经不干净（同一签约流水被复用），MUST 告警而不是当成正常。
                log.warn("IF8A-77按签约流水回写PAY_ACCOUNT_ID命中多行, regSignSeq={}, updated={}", reqContractNo, updated);
            }
        } catch (Exception e) {
            log.warn("IF8A-77回写支付通道PAY_ACCOUNT_ID失败，不影响本次变更结果, regSignSeq={}", reqContractNo, e);
        }
    }

    /**
     * IF8A-23 建出通道行后，安排一次「向支付域反查 {@code PAY_ACCOUNT_ID} 并回写」。
     *
     * <p><b>为什么必须在 afterCommit、NEVER 直接在 {@code requestAddPayChannel} 里同步调</b>：
     * 该方法带 {@code @Transactional}，而 AGENTS.md §5.2 明令「`@Transactional` 方法内 NEVER 发起
     * 任何 RPC / 网络调用」—— 2026-08-26 生产事故就是这么来的（事务内调远端 ⇒ 行锁被 HTTP 往返时长
     * 占住 ⇒ 上游重推全堆在同一行 ⇒ 连接被 Druid 强杀 ⇒ 事务连证据一起回滚）。放到 afterCommit
     * 时通道行已提交、行锁已释放，这次 RPC 不再压住任何锁。</p>
     *
     * <p><b>为什么需要这一步</b>：支付域的主动回写（{@code /internal/payChannel/syncPayAccountId}）
     * 在时序上**必然早于**本方法建出通道行 —— APP 要先签约拿到 {@code reqContractNo} 才能开户加通道，
     * 于是那次 UPDATE 恒命中 0 行、返 {@code 8004}，而它**没有任何重试或补偿**（account-server 全模块
     * 无 {@code @Scheduled}，Quartz 侧也只有改手机号那一个补偿端点）。2026-09-14 实测 {@code 00522955}：
     * 回写比建行早 0.9 秒，`PAY_ACCOUNT_ID` 因此永久为 NULL，而 {@code ItpUserQueryServiceImpl}
     * 的运营页面「支付账号」列正是读这一列。</p>
     *
     * <p>钱包渠道 {@code reqContractNo} 为空（本方法 158 行显式置 null），直接跳过。</p>
     *
     * <p><b>无事务时降级为同步执行</b>：单测直接调 service 不带事务，若只注册同步器就会**静默不执行**
     * （AGENTS.md 记过 {@code fallbackExecution=false} 的同款坑）。因此这里显式判断，保证行为可被单测覆盖。</p>
     */
    private void scheduleBackfillPayAccountId(String reqContractNo) {
        if (!StringUtils.hasText(reqContractNo)) {
            return;
        }
        String seq = reqContractNo.trim();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            backfillPayAccountIdFromPayDomain(seq);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                backfillPayAccountIdFromPayDomain(seq);
            }
        });
    }

    /**
     * IF8A-23 钱包渠道建出通道行后，安排一次「向支付中心发起代扣签约」。
     *
     * <p><b>为什么需要这一步</b>：支付中心 §1.1 requestPay 的 {@code withholding} 场景**强制要求**
     * {@code requestSignSeq}（2026-09-15 实测：只送 {@code payUserId} 时网关返
     * {@code code=9999「代扣签约请求流水号不能为空」}），而钱包用户此前从不在支付中心签约
     * —— 于是钱包渠道的免密扣款从上线起一次都没成功过（`PAY_TXN_DETAIL` 里 `0B` 渠道零条 SUCCESS）。
     * 本方法把「加通道」与「支付中心签约」接上：加通道成功即代用户发起一次 §2.2 contract。</p>
     *
     * <p><b>为什么由 account-server 代发起、而不是让 APP 显式调 IF8A-16</b>：那样要改 APP 的对外契约、
     * 需甲方配合排期；钱包的授权本来就已在钱包侧完成（{@code thirdPayId} 即凭证），
     * 对 APP 再要一次交互没有业务意义。<b>按用户 2026-09-15 的明确选择落地。</b></p>
     *
     * <p><b>为什么必须在 afterCommit</b>：与 {@link #scheduleBackfillPayAccountId} 同一条铁律 ——
     * 本方法的调用点 {@code requestAddPayChannel} 带 {@code @Transactional}，而 AGENTS.md §5.2
     * 明令「`@Transactional` 方法内 NEVER 发起任何 RPC」（2026-08-26 生产事故：事务内调远端 ⇒
     * 行锁被 HTTP 往返占住 ⇒ 上游重推堆积 ⇒ 连接被 Druid 强杀 ⇒ 事务连证据一起回滚）。</p>
     *
     * <p><b>签约失败 NEVER 影响本次加通道的成功应答</b>：通道行已提交、APP 已经能看到通道；
     * 签约只是让后续扣款可用。失败时留 WARN 日志，由人工或后续补偿处理 ——
     * <b>NEVER 在这里把已成功的 IF8A-23 改成失败</b>，那会让 APP 重推、而重推只会拿到 8021。</p>
     */
    private void scheduleWalletContractSignup(UserPayChannel record) {
        if (record == null || !ChannelBindingRule.isWallet(record.getChannel())) {
            return;
        }
        UserPayChannel snapshot = record;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务（单测直接调 service）时降级为同步执行，否则只注册同步器会静默不执行。
            signWalletContractAtPayCenter(snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                signWalletContractAtPayCenter(snapshot);
            }
        });
    }

    /**
     * 向支付域发起钱包代扣签约（IF8A-16 → 支付中心 §2.2 contract）。
     *
     * <p><b>MUST catch 住一切、NEVER 抛出</b>：本方法运行在 afterCommit，事务已提交、通道行已生效，
     * 抛异常回滚不了任何东西，只会让本已成功的 IF8A-23 对上游报错、引来重推（AGENTS.md §5.2）。</p>
     */
    private void signWalletContractAtPayCenter(UserPayChannel record) {
        try {
            RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
            request.setThirdUserId(record.getThirdUserId());
            request.setPayChannelCode(record.getChannel());
            request.setRequestSignSeq(record.getReqContractNo());
            request.setPayUserId(record.getThirdPayId());
            // displayAccount 是网关 §2.2 的必填项，而钱包侧我方手上只有 thirdPayId。
            // 按用户 2026-09-15 的选择：用 thirdPayId 脱敏后填，NEVER 回显全量（AGENTS.md §5.2 敏感信息）。
            request.setDisplayAccount(maskPayId(record.getThirdPayId()));
            RequestSignInfoResult result = paySignClient.requestSignInfo(request);
            if (result == null || !AccountErrorCodeEnum.SUCCESS.getCode().equals(result.getRetCode())) {
                log.warn("IF8A-23钱包代扣签约未成功，通道已建但扣款尚不可用, thirdUserId={}, reqContractNo={}, result={}",
                        record.getThirdUserId(), record.getReqContractNo(),
                        result == null ? "null" : result.getRetCode() + "/" + result.getRetMsg());
                return;
            }
            log.info("IF8A-23钱包代扣签约已发起, thirdUserId={}, reqContractNo={}",
                    record.getThirdUserId(), record.getReqContractNo());
        } catch (Throwable e) {
            log.warn("IF8A-23钱包代扣签约异常，不影响已提交的加通道结果, thirdUserId={}, reqContractNo={}",
                    record.getThirdUserId(), record.getReqContractNo(), e);
        }
    }

    /** 钱包支付账户标识脱敏：保留前 4 后 4，中间固定 4 个星号；长度不足 8 位时整串打星。 */
    private String maskPayId(String payId) {
        if (!StringUtils.hasText(payId)) {
            return null;
        }
        String trimmed = payId.trim();
        if (trimmed.length() < 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 向支付域按签约流水反查 {@code PAY_ACCOUNT_ID} 并回写本地通道行。
     *
     * <p><b>MUST catch 住一切、NEVER 抛出</b>：本方法运行在 afterCommit，此时事务已提交、
     * 通道行已经生效；抛异常回滚不了任何东西，只会让本已成功的 IF8A-23 对上游报错、引来重推
     * （AGENTS.md §5.2 对 AFTER_COMMIT 的同一条要求）。</p>
     */
    private void backfillPayAccountIdFromPayDomain(String reqContractNo) {
        try {
            PaySignInfoDTO signInfo = paySignClient.querySignInfoBySeq(reqContractNo);
            if (signInfo == null || !StringUtils.hasText(signInfo.getPayAccountId())) {
                // 支付域查不到或该签约还没拿到付款账号：只是运营页面少一列，NEVER 升级成业务失败。
                log.info("IF8A-23反查支付域未取到PAY_ACCOUNT_ID，通道行该列留空, reqContractNo={}", reqContractNo);
                return;
            }
            syncPayAccountIdToChannelQuietly(reqContractNo, signInfo.getPayAccountId());
        } catch (Throwable e) {
            log.warn("IF8A-23反查支付域回写PAY_ACCOUNT_ID异常，不影响已提交的加通道结果, reqContractNo={}",
                    reqContractNo, e);
        }
    }

    /**
     * IF8A-23 添加通道的入参校验。
     *
     * <p>「四要素 + 钱包分支」这组不变量已抽到 {@link ChannelBindingRule}（纯函数、可单测），
     * 本方法只剩「空报文」与「取字段喂给规则」两件事。<b>NEVER 把判断逻辑搬回来</b>。</p>
     */
    private String validateAddPayChannelRequest(RequestAddPayChannelReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        return ChannelBindingRule.validateAddBinding(request.getThirdUserId(), request.getCardId(),
                request.getCardType(), request.getChannel(), request.getThirdPayId());
    }

    private String validateSetDefaultPayChannelRequest(RequestSetDefaultPayChannelReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        return ChannelBindingRule.validateBindingFields(request.getThirdUserId(), request.getCardId(),
                request.getCardType(), request.getChannel());
    }

    /**
     * IF8A-77 的字段集合是 {@code cardIssueCode} / {@code regSignSeq}，与四要素不是同一组，
     * <b>刻意不走 {@link ChannelBindingRule}</b>——硬凑会得到一个带开关的四不像。
     */
    private String validateUpdateChannelDefaultContractRequest(RequestUpdateChannelDefaultContractReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getChannel())) {
            return "channel不能为空";
        }
        if (!StringUtils.hasText(request.getCardIssueCode())) {
            return "cardIssueCode不能为空";
        }
        if (!StringUtils.hasText(request.getRegSignSeq())) {
            return "regSignSeq不能为空";
        }
        return null;
    }

    private String validateRemovePayChannelRequest(RequestRemovePayChannelReqDTO request) {
        if (request == null) {
            return ChannelBindingRule.REQUEST_BODY_REQUIRED;
        }
        return ChannelBindingRule.validateBindingFields(request.getThirdUserId(), request.getCardId(),
                request.getCardType(), request.getChannel());
    }

    /**
     * 组装 {@code APP_USER_PAY_CHANNEL} 行。
     *
     * <p><b>该表没有状态机，`STATUS` 恒为 {@code 'ACTIVE'}，这是有意保留的现状</b>：全表生命周期只有
     * 「插入」与「物理删除」（解绑走 {@code deleteByThirdUserIdAndCardTypeAndChannel}），
     * mapper 里也**故意没有任何 UPDATE 语句**，因此 {@code UPDATE_TMS} 恒等于 {@code CREATE_TMS}。</p>
     *
     * <p>2026-09-11 评估过「改成软删（解绑置 {@code INACTIVE} 保留行）」，<b>结论是不改</b>：本表所有读取点
     * 目前都不带 {@code STATUS} 过滤，一旦改软删，解绑后的通道仍会被查出来当有效通道用——那是比
     * 「少一列可用状态」严重得多的静默缺陷。**MUST 先把读取侧全部加上过滤条件、再引入软删**，
     * <b>NEVER 只在写入侧单方面改状态</b>。</p>
     * <p><b>CARD_TYPE 在本表只存 4 位发卡码（044X），NEVER 存 APP 的 2 位码</b>。这里直接归一，
     * 不依赖调用方在 insert 前补一次 {@code setCardType(issueCardType)} —— 那种写法下，
     * 「本方法返回的对象已经是对的」是**假的**，谁少写那一行都不会报错，但删除侧
     * （{@code deleteByThirdUserIdAndCardTypeAndChannel}）是按 4 位码**精确等值**匹配的，
     * 于是这条通道行永远删不掉、上游还会收到 {@code 0000}。</p>
     *
     * <p>2026-09-14 实测库里有 1 行 2 位码残留（{@code THIRD_USER_ID=00522888} /
     * {@code CARD_TYPE=02}，2026-06-24 落库）。<b>但它不是「删除侧漏删」的证据</b>：
     * 该用户在 {@code USER_ITP_REG_INFO} 里没有任何开户记录（含忽略 {@code DEL_YN} 的兜底查询），
     * 解绑会在两次 select 处就返 {@code 8004}、根本走不到 DELETE。它证明的是**另一件事** ——
     * 这张表历史上确实被写进过 2 位码，所以本方法的归一 MUST 保留。
     * 那一行本身归属不明（通道在、开户记录不在），<b>NEVER 擅自把它改成 0441</b>：
     * 没有开户记录就无法确定它真实的票种，猜一个值等于把「归属不明」这个事实抹掉。</p>
     */
    private UserPayChannel buildUserPayChannel(RequestAddPayChannelReqDTO request) {
        UserPayChannel record = new UserPayChannel();
        record.setThirdUserId(request.getThirdUserId().trim());
        record.setCardId(request.getCardId().trim());
        record.setCardType(CardTypeMapping.toIssueCardType(request.getCardType().trim()));
        record.setChannel(request.getChannel().trim());
        record.setThirdPayId(request.getThirdPayId());
        record.setReqContractNo(request.getReqContractNo());
        record.setStatus("ACTIVE");
        record.setCreateTms(LocalDateTime.now());
        record.setUpdateTms(LocalDateTime.now());
        return record;
    }
}
