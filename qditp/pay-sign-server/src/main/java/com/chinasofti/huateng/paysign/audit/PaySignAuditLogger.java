package com.chinasofti.huateng.paysign.audit;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@code APP_PAY_SIGN_REQUEST} 的**唯一**写入点：每次调用往表里 INSERT 一行。
 *
 * <p>这张表承载**两类语义不同的行**，2026-09-17（ADR-D127）把两类都收口进本类，
 * 此前后一类是 {@code SignResultCallbackHandler} / {@code TerminationResultCallbackHandler}
 * 各自 new 一个 {@code PaySignRequest} 直接 {@code paySignRequestMapper.insert}，
 * 即「同一张表两条写入通路」。**NEVER 再在别处 insert 这张表。**
 *
 * <ul>
 *   <li>{@link #write} 系列 —— **接口流水快照**（失败即容忍）：整段包在 try/catch 里只记 ERROR，
 *       因为它只是留痕，不该把主业务带崩；{@code OPERATION_TYPE} 经
 *       {@link #convertOperationType} 归并成 {@code SIGN} / {@code UNSIGNED} 两值。</li>
 *   <li>{@code writeXxxNotifyPending} 系列 —— **通知状态行**（失败即抛）：调用点在事务内，
 *       抛出去才能连主表写入一起回滚，因此 **NEVER 加 try/catch**；
 *       {@code OPERATION_TYPE} **逐字落库、NEVER 走归并**。
 *       <b>两侧的下游消费方不同，NEVER 混为一谈</b>（2026-09-17 联调实测）：
 *       签约侧那行**就是补偿队列本身**（{@code selectCompensableNotify} 靠它重投）；
 *       解约侧那行**只是留痕**，解约通知的状态机在 {@code APP_TERMINATION_REQUEST.NOTIFY_STATUS} 上，
 *       本表这行的 {@code NOTIFY_STATUS} 落 {@code PENDING} 后**永远不会被推进**。</li>
 * </ul>
 */
@Component
public class PaySignAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(PaySignAuditLogger.class);

    /**
     * 签约结果回调的载体行 {@code OPERATION_TYPE}，**逐字落库**。
     *
     * <p>{@code PaySignRequestMapper.selectCompensableNotify} 的 WHERE 里硬编码
     * {@code OPERATION_TYPE = 'RECEIVE_SIGN_RESULT'}，**改这个字面量等于让补偿扫表恒扫 0 行**
     * （不报错、日志一片绿、通知永久不重投）。改一处 MUST 同时改那条 SQL。
     */
    private static final String OP_RECEIVE_SIGN_RESULT = "RECEIVE_SIGN_RESULT";

    /** 解约结果回调的载体行 {@code OPERATION_TYPE}，**逐字落库**（解约侧补偿走 {@code APP_TERMINATION_REQUEST}，不扫本表）。 */
    private static final String OP_RECEIVE_TERMINATION_RESULT = "RECEIVE_TERMINATION_RESULT";

    /**
     * 通知状态行的初始值。**只有签约侧会被推进**（首轮异步投递或补偿扫表）；
     * 解约侧这行没有消费方，落下即停在 {@code PENDING}，见
     * {@link #writeTerminationResultNotifyPending}。
     */
    private static final String NOTIFY_STATUS_PENDING = "PENDING";

    /** RESULT_CODE 列宽（{@code pay-sign-schema.sql} 里是 {@code VARCHAR2(64 CHAR)}）。 */
    private static final int RESULT_CODE_MAX = 64;

    /** RESULT_MSG 列宽（{@code VARCHAR2(1024 CHAR)}）。 */
    private static final int RESULT_MSG_MAX = 1024;

    /** 结果码的候选键名，按优先级排列。 */
    private static final String[] CODE_KEYS = {"retCode", "resultCode", "code"};

    /** 结果文案的候选键名，与 {@link #CODE_KEYS} 一一对应、顺序 MUST 保持一致。 */
    private static final String[] MSG_KEYS = {"retMsg", "resultMsg", "msg"};

    private final PaySignRequestMapper paySignRequestMapper;

    public PaySignAuditLogger(PaySignRequestMapper paySignRequestMapper) {
        this.paySignRequestMapper = paySignRequestMapper;
    }

    /** 记录接口流水，不带签约状态。 */
    public void write(String operationType, String thirdUserId, String requestSignSeq, String paymentVendor,
                      String signChannel, Object request, Object response) {
        write(operationType, thirdUserId, requestSignSeq, paymentVendor, signChannel, request, response, null);
    }

    /**
     * 记录接口流水，并落 {@code SIGN_STATUS}。
     *
     * @param signStatus 取值来自 {@code SignStatus}，<b>NEVER 传解约流程的状态</b> ——
     */
    public void write(String operationType, String thirdUserId, String requestSignSeq, String paymentVendor,
                      String signChannel, Object request, Object response, String signStatus) {
        if (!StringUtils.hasText(requestSignSeq)) {
            return;
        }
        try {
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(requestSignSeq);
            logRecord.setThirdUserId(thirdUserId);
            logRecord.setPaymentVendor(paymentVendor);
            logRecord.setSignChannel(signChannel);
            logRecord.setDisplayAccount(extractDisplayAccount(request));
            logRecord.setOperationType(convertOperationType(operationType));
            String responseBody = response == null ? null : JSON.toJSONString(response);
            logRecord.setRequestBody(request == null ? null : JSON.toJSONString(request));
            logRecord.setResponseBody(responseBody);
            logRecord.setResultCode(pick(responseBody, CODE_KEYS, RESULT_CODE_MAX));
            logRecord.setResultMsg(pick(responseBody, MSG_KEYS, RESULT_MSG_MAX));
            logRecord.setSignStatus(signStatus);
            logRecord.setCreateTms(LocalDateTime.now());
            paySignRequestMapper.insert(logRecord);
        } catch (Exception e) {
            log.error("记录流水日志异常, requestSignSeq={}, 不影响主事务", requestSignSeq, e);
        }
    }

    /**
     * 落一行「签约结果已收口、APP 通知待投递」的载体流水（2026-09-17，ADR-D127）。
     *
     * <p>这不是留痕、是**补偿队列的入队**：{@code SignNotifyServiceImpl.compensateSignNotify}
     * 靠 {@code selectCompensableNotify} 捞 {@code NOTIFY_STATUS} 为 {@code PENDING}（滞留超时）
     * 或 {@code FAILED} 的行来重投。因此三条 **NEVER 改**：
     * <ol>
     *   <li>**NEVER 包 try/catch** —— 调用点在 {@code @Transactional} 内，抛出去才能连签约主表一起回滚，
     *       让渠道重推；吞掉异常等于「签约已落库、APP 永远收不到通知、也没人补」。</li>
     *   <li>{@code OPERATION_TYPE} 走 {@link #OP_RECEIVE_SIGN_RESULT} **逐字落库**，
     *       NEVER 改成 {@link #convertOperationType} 归并后的 {@code SIGN}（补偿 SQL 认的是细分值）。</li>
     *   <li>{@code PAY_ACCOUNT_ID} / {@code PAY_AGREEMENT_NO} / {@code SIGN_STATUS} 三列**必填**：
     *       补偿重投时 {@code REQUEST_BODY} 是空的（本方法刻意不落报文），
     *       {@code doNotifySignResult} 正是靠这三列兜底组装 bizData。</li>
     * </ol>
     */
    public void writeSignResultNotifyPending(ReceiveSignResultReqDTO request, String signChannel, String signStatus) {
        PaySignRequest carrier = newNotifyPendingCarrier(OP_RECEIVE_SIGN_RESULT, request.getRequestSignSeq(),
                request.getThirdUserId(), request.getPaymentVendor(), signChannel, signStatus);
        carrier.setPayAccountId(request.getPayUserId());
        carrier.setPayAgreementNo(request.getPayAgreementNo());
        paySignRequestMapper.insert(carrier);
    }

    /**
     * 落一行「解约结果已收口」的留痕流水（2026-09-17，ADR-D127）。
     *
     * <p>与上一条方法**只共用两条 NEVER**：
     * <ol>
     *   <li>**NEVER 包 try/catch** —— 调用点在 {@code TransactionTemplate.execute} 内，
     *       抛出才能连「删签约主表 + 推进解约状态」一起回滚。</li>
     *   <li>{@code OPERATION_TYPE} 走 {@link #OP_RECEIVE_TERMINATION_RESULT} **逐字落库**，
     *       NEVER 走 {@link #convertOperationType} 归并。</li>
     * </ol>
     *
     * <p><b>但它不是补偿队列的入队，NEVER 与签约侧混为一谈</b>（2026-09-17 联调实测）：
     * 解约通知的状态机在 {@code APP_TERMINATION_REQUEST.NOTIFY_STATUS} 上，
     * {@code selectCompensableNotify} 硬过滤 {@code RECEIVE_SIGN_RESULT}、根本捞不到本行，
     * 因此这行的 {@code NOTIFY_STATUS} 落 {@code PENDING} 后**永远停在 PENDING**
     * —— 那不是丢通知，只是本表不承载解约侧的投递状态。
     * 随行列也不同：带 {@code CARD_ID} / {@code CARD_TYPE} / {@code TERMINATION_TIME}，
     * 不带支付账号与协议号（签约侧那三列是给补偿重投兜底组装用的，本行无此用途）。
     */
    public void writeTerminationResultNotifyPending(ReceiveTerminationResultReqDTO request, String signChannel,
                                                    String signStatus) {
        PaySignRequest carrier = newNotifyPendingCarrier(OP_RECEIVE_TERMINATION_RESULT, request.getRequestSignSeq(),
                request.getThirdUserId(), request.getPaymentVendor(), signChannel, signStatus);
        carrier.setCardId(request.getCardId());
        carrier.setCardType(request.getCardType());
        carrier.setTerminationTime(request.getDismissalTime());
        paySignRequestMapper.insert(carrier);
    }

    /** 两类载体行的公共列：键、渠道、状态，外加 {@code PENDING} + 重试次数 0。 */
    private PaySignRequest newNotifyPendingCarrier(String operationType, String requestSignSeq, String thirdUserId,
                                                   String paymentVendor, String signChannel, String signStatus) {
        PaySignRequest carrier = new PaySignRequest();
        carrier.setRequestSignSeq(requestSignSeq);
        carrier.setThirdUserId(thirdUserId);
        carrier.setPaymentVendor(paymentVendor);
        carrier.setSignChannel(signChannel);
        carrier.setOperationType(operationType);
        carrier.setSignStatus(signStatus);
        carrier.setCreateTms(LocalDateTime.now());
        carrier.setNotifyStatus(NOTIFY_STATUS_PENDING);
        carrier.setNotifyRetryCount(0);
        return carrier;
    }

    /** 从**已序列化的应答 JSON** 里按候选键名取第一个有值的字段，并按列宽截断。 */
    private String pick(String responseBody, String[] keys, int maxLength) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        JSONObject json;
        try {
            json = JSON.parseObject(responseBody);
        } catch (RuntimeException e) {
            return null;
        }
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.hasText(value)) {
                return value.length() > maxLength ? value.substring(0, maxLength) : value;
            }
        }
        return null;
    }

    /** 将内部细分操作归并成流水表设计中的 SIGN / UNSIGN。 */
    private String convertOperationType(String operationType) {
        if (!StringUtils.hasText(operationType)) {
            return "SIGN";
        }
        String upper = operationType.toUpperCase();
        if (upper.contains("TERMINATION") || upper.contains("UNSIGN")) {
            return "UNSIGN";
        }
        return "SIGN";
    }

    /** 从请求对象中提取 {@code displayAccount}。 */
    private String extractDisplayAccount(Object request) {
        if (request == null) {
            return null;
        }
        if (request instanceof RequestSignInfoReqDTO dto) {
            return dto.getDisplayAccount();
        }
        if (request instanceof ReceiveSignResultReqDTO dto) {
            return dto.getDisplayAccount();
        }
        return null;
    }
}
