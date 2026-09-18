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

    private static final DateTimeFormatter OPTION_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayBlacklistNotifyPort(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /**
     * 向支付宝渠道推送一次黑名单变更。
     *
     * <p>三分支判据：
     * <ul>
     *   <li>应答 {@code retCode == 0000} → {@link RpcOutcome.Ok}，可收口 SUCCESS。</li>
     *   <li>拿到应答但 {@code retCode} 不是 {@code 0000} → {@link RpcOutcome.BizRejected}，
     *       渠道明确拒绝，重推无意义，调用方 MUST 一次即终态（REJECTED）+ 留人工核对。</li>
     *   <li>应答为 {@code null}（空响应）或调用抛异常 → {@link RpcOutcome.Unreachable}，
     *       没拿到业务结论，这才是该进补偿队列的那一支。</li>
     * </ul>
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
        log.error("通知支付宝渠道黑名单变更被业务拒绝，重推无意义、MUST 一次即终态并人工核对, cardId={}, blackListType={}, retCode={}, retMsg={}",
                cardId, blackListType, response.getRetCode(), response.getRetMsg());
        return new RpcOutcome.BizRejected(response.getRetCode(), response.getRetMsg());
    }
}
