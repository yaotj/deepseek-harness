package com.chinasofti.huateng.blacklist.port;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 支付宝渠道黑名单变更通知的出网端口。
 *
 * <p><b>存在的唯一理由是把三种结果分开</b>：{@link AlipayPaySignClient#notifyBlackListChange} 的形态是
 * 「空响应返 {@code null}、网络异常与 JSON 解析异常原样上抛」，调用方拿到的既不是 boolean 也不是
 * {@link RpcOutcome}，于是<b>「渠道明确拒绝」与「压根没打通」在调用点长得一模一样</b>。
 * 而这两件事的处置完全相反：前者重推一万次也不会成功、MUST 一次即终态；后者才该进补偿队列。
 * 本端口把它翻成 sealed {@code RpcOutcome}，调用点用穷尽 {@code switch} 处置，<b>少写一支直接编译失败</b>。
 *
 * <p><b>刻意不拆成「接口 + 实现」两层</b>：本模块只有这一个实现，单测直接 mock 本类即可。
 * <b>NEVER 为了对称再造一个 interface</b> —— 那只会让人以为还有第二种实现。
 *
 * <p><b>NEVER 在本类里读写数据库、NEVER 在这里做重试</b>：状态回写与补偿节奏归
 * {@code BlacklistChannelSyncService}，本类只负责「发一次、如实报告结果」。
 */
@Component
public class AlipayBlacklistNotifyPort {

    private static final Logger log = LoggerFactory.getLogger(AlipayBlacklistNotifyPort.class);

    /** 支付宝渠道业务成功的 retCode。 */
    private static final String SUCCESS = "0000";

    /**
     * 支付宝渠道「没拿到支付中心业务结论」的 retCode，<b>MUST 按不可达处置、NEVER 当业务拒绝</b>。
     *
     * <p>成因：{@code alipay-pay-sign-server} 的 {@code PaymentNotifyAdapter.notifyBlackListChange}
     * 只在两种情形返 {@code 9001}（{@code FepAppErrorCodeEnum.SYSTEM_ERROR}）—— 支付中心应答为
     * {@code null}（{@code PayCenterClient} 把 HTTP 5xx / 超时 / 连接失败一律吞成 null）或它自己
     * catch 到异常；<b>两者都表示「这一次压根没问出结论」</b>。真正的业务拒绝走
     * {@code 9999}（拿到应答但判不成功）与 {@code 8001}（卡号为空），那两个才该一次即终态。
     *
     * <p><b>为什么必须区分</b>：两阶段解除下，通知推达前那行仍留在 BLACKLIST（{@code STATUS='RELEASING'}、
     * 判黑仍命中），只有 {@code Ok} 才搬历史 + 删行。而 {@code BizRejected} 会把
     * {@code CHANNEL_SYNC_STATUS} 打成终态 {@code REJECTED}，<b>而 REJECTED 不在补偿扫表白名单
     * {@code IN ('PENDING','FAILED')} 里</b> —— 于是支付中心网关抖动一次（哪怕只是 502），
     * 那张卡就永久卡在 RELEASING、判黑恒命中、补偿再也扫不到，<b>乘客再也解不了黑</b>。
     * 2026-09-18 端到端实测到：支付中心返 HTTP 502 Bad Gateway，alipay 翻成 9001，
     * 本端口原按 BizRejected 处置，卡 TEST2P0000000001 当场被锁死。
     *
     * <p><b>这是「在本端口按码值兜」的有意取舍</b>（用户 2026-09-18 裁决）：治根做法是让
     * alipay 侧把传输层失败与业务拒绝分开上报，但那会牵动所有走 {@code PayCenterClient} 的通知。
     * 本处只覆盖黑名单这一条，代价是<b>与 alipay 侧的码分配形成隐式耦合</b> —— alipay 若把
     * {@code SYSTEM_ERROR} 挪去表达别的语义，本判断即失效且编译期发现不了。
     * <b>改动 {@code PaymentNotifyAdapter} 的返码分配时 MUST 同步看齐本常量。</b>
     */
    private static final String CHANNEL_UNREACHABLE = "9001";

    private static final DateTimeFormatter OPTION_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayBlacklistNotifyPort(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /**
     * 向支付宝渠道推送一次黑名单变更。
     *
     * <p>四分支判据：
     * <ul>
     *   <li>应答 {@code retCode == 0000} → {@link RpcOutcome.Ok}，可收口 SUCCESS。</li>
     *   <li>应答 {@code retCode == 9001} → {@link RpcOutcome.Unreachable}，渠道自己也没拿到支付中心结论
     *       （详见 {@link #CHANNEL_UNREACHABLE}），进补偿队列。</li>
     *   <li>应答 {@code retCode} 是其余非 {@code 0000} 值（{@code 9999} / {@code 8001} 等）→
     *       {@link RpcOutcome.BizRejected}，渠道明确拒绝，重推无意义，调用方 MUST 一次即终态（REJECTED）+ 留人工核对。</li>
     *   <li>应答为 {@code null}（空响应）或调用抛异常 → {@link RpcOutcome.Unreachable}，
     *       没拿到业务结论，这也是该进补偿队列的那一支。</li>
     * </ul>
     *
     * <p><b>NEVER 把「非 0000 一律当 BizRejected」写回来</b> —— 那会让支付中心的一次网关抖动
     * 把卡永久锁在 {@code RELEASING}，理由见 {@link #CHANNEL_UNREACHABLE}。</p>

     *
     * <p><b>不上送 expireTime</b>：业务上黑名单没有有效期，历史实现填的是当前时刻、语义等于「立即失效」。
     * <b>NEVER 填回当前时刻。</b>
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param cardType 卡类型编码
     * @param blackListType 1：加入黑名单，2：移除黑名单
     * @param reason 变更原因
     * @return 三分支结果，NEVER 返回 null
     */
    public RpcOutcome notifyBlackListChange(String cardId, String thirdUserId, String cardType,
                                            String blackListType, String reason) {
        AlipayBlackListNotifyReqDTO request = new AlipayBlackListNotifyReqDTO();
        request.setCardId(cardId);
        request.setThirdUserId(thirdUserId);
        request.setCardType(cardType);
        request.setBlackListType(blackListType);
        request.setOptionDate(LocalDateTime.now().format(OPTION_DATE));
        request.setReason(reason);

        AlipayCommonResponse response;
        try {
            response = alipayPaySignClient.notifyBlackListChange(request);
        } catch (RuntimeException e) {
            log.error("通知支付宝渠道黑名单变更抛异常，按不可达处置、进补偿队列, cardId={}, blackListType={}",
                    cardId, blackListType, e);
            return new RpcOutcome.Unreachable(e);
        }

        if (response == null) {
            log.error("通知支付宝渠道黑名单变更拿到空响应，按不可达处置、进补偿队列, cardId={}, blackListType={}",
                    cardId, blackListType);
            return new RpcOutcome.Unreachable(new IllegalStateException("支付宝渠道黑名单通知返回空响应"));
        }
        if (SUCCESS.equals(response.getRetCode())) {
            log.info("通知支付宝渠道黑名单变更完成, cardId={}, blackListType={}", cardId, blackListType);
            return new RpcOutcome.Ok();
        }
        if (CHANNEL_UNREACHABLE.equals(response.getRetCode())) {
            log.error("通知支付宝渠道黑名单变更未拿到支付中心结论（retCode={}），按不可达处置、进补偿队列, cardId={}, blackListType={}, retMsg={}",
                    CHANNEL_UNREACHABLE, cardId, blackListType, response.getRetMsg());
            return new RpcOutcome.Unreachable(
                    new IllegalStateException("支付宝渠道未拿到支付中心结论: " + response.getRetMsg()));
        }
        log.error("通知支付宝渠道黑名单变更被业务拒绝，重推无意义、MUST 一次即终态并人工核对, cardId={}, blackListType={}, retCode={}, retMsg={}",
                cardId, blackListType, response.getRetCode(), response.getRetMsg());
        return new RpcOutcome.BizRejected(response.getRetCode(), response.getRetMsg());
    }
}
