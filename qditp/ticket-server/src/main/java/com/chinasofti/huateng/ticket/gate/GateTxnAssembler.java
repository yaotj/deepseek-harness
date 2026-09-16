package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * IF1A-01 落库对象组装器 —— 把闸机报文翻译成 {@link QRCodeTxnDetail}（交易明细）与
 * {@link QRCodeStatus}（下一票卡状态）两个待写实体。
 *
 * <p>2026-09-14 从 {@code GateTicketHandler}（887 行）拆出。拆分判据是**这里只做纯组装**：
 * 入参进、实体出，一条 SQL 都不发（写库在 {@link GateTicketWriter}，事务边界也在那里）。
 * 与之配套的三个脏数据兜底（{@link #resolveTxnDate} / {@link #parseAmount} /
 * {@link #incrementTxnSeq}）也一并搬来 —— 它们全部服务于「一条脏字段 NEVER 放大成整笔检票失败」
 * 这一条不变量，散在编排类里会被误当成可省略的防御性代码删掉。</p>
 */
@Component
class GateTxnAssembler {

    private static final Logger log = LoggerFactory.getLogger(GateTxnAssembler.class);
    private static final DateTimeFormatter TXN_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private GateCodeStatusResolver codeStatusResolver;

    /**
     * 构建交易明细。
     */
    public QRCodeTxnDetail buildTxnDetail(NotifyVerifyResultReqDTO request) {
        QRCodeTxnDetail detail = new QRCodeTxnDetail();
        detail.setDeviceId(request.getDeviceId());
        detail.setItpUserId(request.getItpUserId());
        detail.setTrxType(request.getTrxType());
        detail.setIssueChannelCode(request.getIssueChannelCode());
        detail.setSignChannelCode(request.getSignChannelCode());
        detail.setCardId(request.getCardId());
        detail.setCardType(request.getCardType());
        detail.setHandleDateTime(request.getHandleDateTime());
        detail.setTxnDate(resolveTxnDate(request.getHandleDateTime()));
        detail.setHandleStationCode(request.getHandleStationCode());
        detail.setTrxAmount(parseAmount(request.getTrxAmount()));
        detail.setOvertimeAmount(parseAmount(request.getOvertimeAmount()));
        detail.setLastTicketStatus(request.getLastTicketStatus());
        detail.setHandleResultCode(request.getHandleResultCode());
        detail.setLastHandleStationCode(request.getLastHandleStationCode());
        detail.setLastHandleDateTime(request.getLastHandleDateTime());
        detail.setTicketTransSeq(request.getTicketTransSeq());
        detail.setReserve1(request.getReserve1());
        detail.setReserve2(request.getReserve2());
        detail.setCreateTime(LocalDateTime.now());
        return detail;
    }

    /**
     * 从 {@code handleDateTime}（yyyyMMddHHmmss）取交易日期。
     *
     * <p>此前直接 {@code substring(0, 8)}：IF5A-03 的 {@code optDate} 由 BOM 上送，长度不足 8
     * 会抛 {@code StringIndexOutOfBoundsException}，且 face-pay-server 只校验非空不校验格式。
     * 这里兜底为当天日期，同时打 WARN 留痕，避免一条脏报文把整笔检票打成 500。</p>
     *
     * <p><b>只判长度不够是不够的。</b>长度够但非数字（如 {@code abcdefgh}）原样写进 {@code TXN_DATE}，
     * 会与同行的 {@code HANDLE_DATE_TIME} 一起变成对账时无法解释的脏值，所以这里连内容一起校验，
     * 8 位必须全是数字才采用，否则同样走兜底。</p>
     */
    private String resolveTxnDate(String handleDateTime) {
        if (handleDateTime != null && handleDateTime.length() >= 8) {
            String candidate = handleDateTime.substring(0, 8);
            if (isAllDigits(candidate)) {
                return candidate;
            }
        }
        String fallback = LocalDateTime.now().format(TXN_DATE_FORMATTER);
        log.warn("IF1A-01 handleDateTime 格式异常，txnDate 兜底为当天, handleDateTime={}, txnDate={}",
                handleDateTime, fallback);
        return fallback;
    }

    private boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析金额（分）。
     *
     * <p><b>NEVER 让 {@code NumberFormatException} 逃出本方法。</b>闸机上送的 {@code trxAmount} /
     * {@code overtimeAmount} 是字符串，含空格或字母时 {@code Long.valueOf} 会抛异常，整笔检票退化成 500、
     * 闸机不开门，而同一条脏报文重推多少次都是同样的结果。兜底策略与 {@link #resolveTxnDate} 对齐：
     * 只打 WARN、返回 null 让该字段留空，把「一条脏字段」限制在字段本身，不放大成整笔交易失败。</p>
     */
    private Long parseAmount(String amount) {
        if (!StringUtils.hasText(amount)) {
            return null;
        }
        try {
            return Long.valueOf(amount.trim());
        } catch (NumberFormatException e) {
            log.warn("IF1A-01 金额字段非数字，本字段置空继续处理, amount={}", amount);
            return null;
        }
    }

    /**
     * 构建下一票卡状态。
     *
     * <ul>
     *   <li>useCount = current + 1（首次为 1）</li>
     *   <li>txnSeq = current + 1（首次为 1）</li>
     *   <li>进站交易(01)：更新 gateInTime/gateInStation</li>
     *   <li>出站交易(02/03)：保留 gateInTime/gateInStation，记录 trxAmount</li>
     * </ul>
     */
    public QRCodeStatus buildNextStatus(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus) {
        QRCodeStatus nextStatus = new QRCodeStatus();
        nextStatus.setCardId(request.getCardId());
        nextStatus.setUseCount(currentStatus.getUseCount() == null ? 1 : currentStatus.getUseCount() + 1);
        nextStatus.setChannel(StringUtils.hasText(request.getIssueChannelCode())
                ? request.getIssueChannelCode() : currentStatus.getChannel());
        nextStatus.setCodeStatus(codeStatusResolver.resolveCodeStatus(
                request.getTrxType(), request.getExcessFareType(), request.getAdviceOpt()));
        nextStatus.setLastTxnTime(request.getHandleDateTime());
        nextStatus.setLastTxnStation(request.getHandleStationCode());
        nextStatus.setTxnSeq(incrementTxnSeq(currentStatus.getTxnSeq()));
        nextStatus.setCreateTime(currentStatus.getCreateTime());
        nextStatus.setUpdateTime(LocalDateTime.now());
        nextStatus.setGateStatus(request.getTrxType());

        if (TrxTypeCodeEnum.isEntryTxn(request.getTrxType())) {
            nextStatus.setGateInTime(request.getHandleDateTime());
            nextStatus.setGateInStation(request.getHandleStationCode());
        } else {
            nextStatus.setGateInTime(currentStatus.getGateInTime());
            nextStatus.setGateInStation(currentStatus.getGateInStation());
        }

        if (!TrxTypeCodeEnum.isEntryTxn(request.getTrxType())) {
            nextStatus.setTrxAmount(parseAmount(request.getTrxAmount()));
        } else {
            nextStatus.setTrxAmount(currentStatus.getTrxAmount());
        }

        log.info("IF1A-01 构建下一状态, cardId={}, trxType={}, currentStatus={}, nextStatus={}",
                request.getCardId(), request.getTrxType(), currentStatus, nextStatus);
        warnIfTransitionUnexpected(currentStatus, nextStatus, request);
        return nextStatus;
    }

    /**
     * 迁移白名单观察日志：**只告警、NEVER 拦截**。
     *
     * <p>白名单的权威在 {@code upsertWithCas} 的 CAS 条件与 {@link QRCodeStatusEnum} 的
     * {@code ALLOWED} 文档化定义（见 {@code docs/domain/state-machines.md} §二③ 约束 2）。
     * 这里补一条 WARN 是为了让「库内状态与本次目标态不构成合法迁移」这件事在运营侧可见 ——
     * 此前连日志都没有，异常流转完全无感知。</p>
     *
     * <p><b>NEVER 把这里改成拒绝</b>：调用方手里的 {@code currentStatus} 来自更早一次 select、
     * 随时可能过期，用过期值拦截只会误拦真实过闸；且闸机侧没有「稍后重试」语义，拒绝等于把乘客关在闸机里。</p>
     *
     * <p><b>「闭环状态重复流转」MUST 单独判一次，NEVER 指望 {@code canTransitTo} 报出来</b>：
     * 该方法开头就是 {@code if (this == target || ...) return true}，同态直接短路 ——
     * 于是 {@code 05 -> 05}（已出站的卡又出站）这类明显异常会静默通过，一条日志都没有，
     * 而 {@code ALLOWED} 表里 {@code EXIT} 的合法出边只有 {@code {ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY}}，
     * 本来就该告警。2026-09-14 实测到：卡 {@code ...095} 用离线码在 18:28:44 与 18:30:23 连刷两次出站，
     * 第二次库内已是 {@code 05}，仍照常写明细并推进 {@code 05}，
     * 且 {@code GATE_TXN_PAY} 因票价算不出 + 兜底落单撞 {@code ORA-12899} 而零痕迹 ——
     * 整条链路唯一能留下线索的地方就是本条 WARN。判据用 {@link QRCodeStatusEnum#isClosedLoop()}
     * （覆盖 {@code 02 / 05 / 06 / 80}），**NEVER 只列 {@code 05}** —— 超时出站与自助补出站同样是终态。
     * 开环同态（{@code 04 -> 04} / {@code 81 -> 81}）不在此列：那是闸机侧重复上送同一笔进站，
     * 由 {@code upsertWithCas} 的 {@code isDuplicate} 负责识别，在这里再报一遍只是噪声。</p>
     */
    private void warnIfTransitionUnexpected(QRCodeStatus currentStatus, QRCodeStatus nextStatus,
                                            NotifyVerifyResultReqDTO request) {
        QRCodeStatusEnum from = QRCodeStatusEnum.parseOrNull(currentStatus.getCodeStatus());
        QRCodeStatusEnum to = QRCodeStatusEnum.parseOrNull(nextStatus.getCodeStatus());
        if (from == null || to == null) {
            log.warn("IF1A-01 状态迁移含未登记取值, cardId={}, from={}, to={}",
                    request.getCardId(), currentStatus.getCodeStatus(), nextStatus.getCodeStatus());
            return;
        }
        if (from == to && from.isClosedLoop()) {
            log.warn("IF1A-01 闭环状态重复流转（仅告警不拦截）, cardId={}, status={}({}), trxType={}, adviceOpt={},"
                            + " 本次站={}, 本次时间={}, 库内进站站={}, 库内进站时间={}, 库内末次站={}, 库内末次时间={}",
                    request.getCardId(), from.getCode(), from.getDesc(),
                    request.getTrxType(), request.getAdviceOpt(),
                    request.getHandleStationCode(), request.getHandleDateTime(),
                    currentStatus.getGateInStation(), currentStatus.getGateInTime(),
                    currentStatus.getLastTxnStation(), currentStatus.getLastTxnTime());
            return;
        }
        if (!from.canTransitTo(to)) {
            log.warn("IF1A-01 状态迁移不在白名单内（仅告警不拦截）, cardId={}, from={}({}), to={}({}), trxType={}, adviceOpt={}",
                    request.getCardId(), from.getCode(), from.getDesc(), to.getCode(), to.getDesc(),
                    request.getTrxType(), request.getAdviceOpt());
        }
    }

    /**
     * 交易序号 +1。
     *
     * <p><b>解析失败 MUST 回退到 "1"，NEVER 原样返回。</b>原样返回会让 {@code nextStatus.txnSeq}
     * 等于 {@code currentStatus.txnSeq}，而 {@code upsertWithCas} 的 CAS 条件是
     * {@code T.TXN_SEQ = #{expectedTxnSeq}}——两者相等时 UPDATE 照样命中、却把同一个值写回去，
     * 于是**序号永不推进**，而 {@code TICKET_TRANS_SEQ} 又是明细唯一索引的一部分，幂等语义随之失效。
     * 回退到 "1" 至少能让序号重新开始递增、并留下 ERROR 供人工核对。</p>
     */
    private String incrementTxnSeq(String txnSeq) {
        if (!StringUtils.hasText(txnSeq)) {
            return "1";
        }
        try {
            return String.valueOf(Long.parseLong(txnSeq.trim()) + 1);
        } catch (NumberFormatException e) {
            log.error("IF1A-01 库内 TXN_SEQ 非数字，无法递增，回退为 1 并需人工核对, txnSeq={}", txnSeq);
            return "1";
        }
    }
}
