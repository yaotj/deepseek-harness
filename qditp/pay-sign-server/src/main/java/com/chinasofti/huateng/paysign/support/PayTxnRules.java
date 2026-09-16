package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;
import static com.chinasofti.huateng.paysign.support.PaySignValues.resolveTxnDate;

import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 免密扣款的**业务规则与本地实体装配**（2026-09-16 由 {@code PaymentDomainServiceImpl} 逐字搬出，ADR-D98）。
 *
 * <p>与 {@link PayRefundRules} 同一条判据：只收**不持有任何协作者**的逻辑。
 * <b>本类 MUST 保持纯函数、零状态、零依赖，NEVER 注入 mapper / client / properties</b>。
 *
 * <p><b>刻意留在 {@code PaymentDomainServiceImpl} 里没搬的三个</b>（NEVER 因为「看着也是纯的」补搬）：
 * <ul>
 *   <li>{@code applyAccountUserView} —— 方法体内有 3 处 {@code log.warn}。搬过来 logger 名就从
 *       业务类变成本类，按类名检索日志的排查路径会断；而它记的正是「account-server 少返了哪个字段」，
 *       是线上定位钱包扣款失败的第一手线索。</li>
 *   <li>{@code shouldStopRetry} —— 与另一处日志共用 {@code MAX_PAY_CALLBACK_PUSH}。只搬方法要把常量
 *       也搬出来再 import 回去，硬限次的值与它的说明就被拆到两个文件，得不偿失。</li>
 *   <li>{@code resolvePaySignInfoFromAccount} —— 走 {@code accountDomainPort}，本来就不是纯函数。</li>
 * </ul>
 */
public final class PayTxnRules {

    /*
     * 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108）：
     * 渠道判定统一走同包的 PaymentChannels.isWallet(...)。NEVER 在此重新声明。
     */

    private PayTxnRules() {
    }

    /**
     * 校验过闸扣费发起支付所需的签约信息是否已透传。
     *
     * <p>现状（2026-08-26 生产实测）：调用方 gate-txn-pay-server 并**不**透传
     * paymentVendor / requestSignSeq，两个字段每次都是 null，实际靠 requestPay 里的
     * {@code selectByOrderNo} 或 {@code resolvePaySignInfoFromAccount} 补齐后才走到这里。
     * 因此这里到达时字段为空只说明「本地和 account-server 都查不到签约信息」，
     * 可以判定未签约。让调用方真正透传属于跨模块改动，另行安排。</p>
     *
     * <p><b>钱包（{@code 0B}）两个字段都要校验</b>：自 2026-09-15 起钱包也走支付中心签约，
     * 只校验 {@code payUserId} 会掩盖「该用户其实没签约」，见
     * {@code PaymentDomainServiceImpl.applyAccountUserView} 的方法头注释。</p>
     */
    public static boolean validatePaySignInfo(RequestPayReqDTO request, RequestPayResult response) {
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "paymentVendor不能为空");
            return false;
        }
        // 入口级路径选择 MUST 穷尽（ADR-D109）：两个类别要校验的字段本就不同，
        // 新增类别时编译器会在这里报错。NEVER 退回 if (isWallet(...)) + 尾部兜底 return。
        return switch (PaymentChannels.classify(request.getPaymentVendor())) {
            case PaymentChannel.Wallet ignored -> {
                if (!StringUtils.hasText(request.getPayUserId())) {
                    fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "钱包支付账户标识不能为空");
                    yield false;
                }
                yield true;
            }
            case PaymentChannel.Contracted ignored -> {
                if (!StringUtils.hasText(request.getRequestSignSeq())) {
                    fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, "requestSignSeq不能为空");
                    yield false;
                }
                yield true;
            }
        };
    }

    /** 把落库的 {@code PAY_STATUS} 归一成回给闸机侧的扣费请求结果。 */
    public static String resolveDebitRequestResult(String payStatus) {
        if ("PROCESSING".equals(payStatus)) return "PROCESSING";
        if ("SUCCESS".equals(payStatus)) return "SUCCESS";
        return "FAIL";
    }

    /** 装配 {@code PAY_CALLBACK_LOG} 一行（回调留证据用，落库由调用方负责）。 */
    public static PayCallbackLog buildPayCallbackLog(ReceivePayResultReqDTO request, String rawBody) {
        PayCallbackLog logRecord = new PayCallbackLog();
        logRecord.setOrderNo(request.getOrderNo());
        logRecord.setCallbackType("PAY");
        logRecord.setCallbackStatus(request.getStatus());
        logRecord.setMerchantOrderNo(request.getMerchantOrderNo());
        logRecord.setChannelOrderNo(request.getChannelOrderNo());
        logRecord.setPayTime(request.getPayTime());
        logRecord.setTotalAmount(request.getTotalAmount());
        logRecord.setCashAmount(request.getCashAmount());
        logRecord.setCouponAmount(request.getCouponAmount());
        logRecord.setPayUserId(request.getPayUserId());
        logRecord.setPaymentVendor(request.getPaymentVendor());
        logRecord.setTxnDate(resolveTxnDate());
        logRecord.setRawBody(rawBody);
        logRecord.setHandleStatus("SUCCESS");
        logRecord.setCreateTime(LocalDateTime.now());
        return logRecord;
    }
}
