package com.chinasofti.huateng.paysign.audit;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 支付签约域接口流水的**唯一**写入点：每次调用往 {@code APP_PAY_SIGN_REQUEST} INSERT 一行快照。
 *
 * <p><b>由来</b>（2026-09-14 拆分批次 1）：本类的两个 {@code write} 方法原是
 * {@code PaySignWorkflow} 的私有 / 包级 {@code writeLog}，被本模块 52 处调用，
 * 其中 {@code TerminationProcessor} 与 {@code TerminationInternalServiceImpl} 是
 * **反向**调用回 {@code PaySignWorkflow} 的（业务类被当公共工具库使用）。抽出来之后那条反向边消失，
 * 三个调用方各自依赖本类。<b>行为一字未改</b>：字段装配、归并规则、异常处理、日志文案全部原样搬迁。
 *
 * <p><b>NEVER 在本类里加业务判断</b>。它只做「把已经定好的一行审计流水落库」这一件事；
 * 谁该记、记什么状态由调用方决定。一旦这里开始按 {@code operationType} 分支做业务动作，
 * 就又回到了「一个类什么都干」。
 *
 * <p><b>异常一律吞掉、只记 ERROR 日志（NEVER 改成往外抛）</b>：审计流水失败不该让主链路失败 ——
 * 这是搬迁前的既有语义。注意两点连带事实：①调用方多数带 {@code @Transactional}，
 * 这里的 INSERT 会随主事务回滚，所以「日志一定留得下」在事务内并不成立
 * （已发生：2026-08-26 事务被 Druid 强杀，`PAY_CALLBACK_LOG` 零条落库）；
 * ②{@code requestSignSeq} 为空时**直接不记**，因为该列是流水表的业务主键。
 *
 * <p><b>与「通知补偿队列」的边界</b>：本类**NEVER 写 {@code NOTIFY_STATUS} / {@code NOTIFY_RETRY_COUNT}}。
 * 那两列只属于真正的通知队列行（签约结果由 {@code PaySignWorkflow.receiveSignResult} 自己插、
 * 解约结果在 {@code APP_TERMINATION_REQUEST}）。审计流水多写一次通知列，就等于凭空造出
 * 第二个补偿队列且永远收不到结果回写 —— 生产上已因此留下 ID=745 / 749 两条幽灵行
 * （2026-08-26 修复，详见 {@code docs/business/pay-sign.md} §幂等与重试）。
 */
@Component
public class PaySignAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(PaySignAuditLogger.class);

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
     *                   两台状态机取值撞车纯属巧合（见 {@code PaySignWorkflow} 顶部
     *                   {@code STATUS_FAILED} 与 {@code SIGN_LOG_STATUS_FAILED} 的注释）
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
            logRecord.setRequestBody(request == null ? null : JSON.toJSONString(request));
            logRecord.setResponseBody(response == null ? null : JSON.toJSONString(response));
            if (response instanceof BaseRespDTO baseRespDTO) {
                logRecord.setResultCode(baseRespDTO.getRetCode());
                logRecord.setResultMsg(baseRespDTO.getRetMsg());
            }
            logRecord.setSignStatus(signStatus);
            logRecord.setCreateTms(LocalDateTime.now());
            paySignRequestMapper.insert(logRecord);
        } catch (Exception e) {
            log.error("记录流水日志异常, requestSignSeq={}, 不影响主事务", requestSignSeq, e);
        }
    }

    /**
     * 将内部细分操作归并成流水表设计中的 SIGN / UNSIGN。
     *
     * <p><b>落库只有这两个值</b>，调用方传的 13 个细分名（{@code REQUEST_SIGN_INFO} /
     * {@code RECEIVE_TERMINATION_RESULT_WALLET_IGNORED} …）只影响这次归并结果。
     * 运营按 {@code OPERATION_TYPE} 查流水的口径就是这两个值，
     * <b>NEVER 改成落细分名</b>（会让既有查询与统计全部错口径）。
     */
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

    /**
     * 从请求对象中提取 {@code displayAccount}。
     *
     * <p>只认这两个 DTO，其余一律 {@code null} —— 这是搬迁前的原样行为。
     * 要新增类型 MUST 确认那个字段确实是「支付账号展示值」，NEVER 拿别的字段凑。
     */
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
