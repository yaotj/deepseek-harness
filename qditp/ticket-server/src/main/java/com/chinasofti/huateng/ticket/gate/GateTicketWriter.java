package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * IF1A-01 闸机检票的落库组件，只承载「交易明细入库 + 票卡状态推进」这一个事务边界。
 *
 * <p>单独拆成组件而不是留在 {@link GateTicketHandler} 里，原因与 gate-txn-pay-server 的
 * {@code GateTxnPayWriter} 相同：Spring 代理对同类自调用不生效，事务边界必须落在跨 bean 调用上。</p>
 *
 * <p><b>NEVER 在本类里加入任何 RPC / 网络调用 / Thread.sleep。</b>本组件存在的唯一目的就是
 * 把事务收窄到两条本地 SQL：{@code QRCODE_STATUS} 的 upsert 按 {@code CARD_ID} 命中单行，
 * 同一张卡连续进出站、AGM 超时重推都会撞在这一行上。一旦事务里夹进网络调用，行锁持有时长
 * 就等于对端响应时长，并发上送会串行堆积，超过 Druid {@code remove-abandoned-timeout}
 * （{@code resource/micro/sql-datasource/src/main/resources/sql.properties:44}，60 秒）后连接被强杀、
 * {@code commit} 抛 {@code connection closed}，整个事务被丢弃——参见 AGENTS.md §5.2 记录的
 * 2026-08-26 生产事故（pay-sign-server 支付回调循环重推 8 分钟）。</p>
 */
@Component
class GateTicketWriter {

    private static final Logger log = LoggerFactory.getLogger(GateTicketWriter.class);

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;
    private final QRCodeStatusStore qrCodeStatusStore;

    public GateTicketWriter(QRCodeTxnDetailMapper qrCodeTxnDetailMapper,
                            QRCodeStatusStore qrCodeStatusStore) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
        this.qrCodeStatusStore = qrCodeStatusStore;
    }

    /**
     * 交易明细入库 + 票卡状态推进（CAS 版），两条 SQL 在同一事务内完成。
     *
     * <p>幂等由数据库唯一索引 {@code UK_QRCODE_TXN_DETAIL_BIZ} 保证：
     * 明细重复上送时 {@code DuplicateKeyException} 在此吞掉，跳过状态 upsert。</p>
     *
     * <p>状态推进优先使用 CAS 版 {@code upsertWithCas}：UPDATE 分支要求
     * {@code TXN_SEQ = expectedTxnSeq}（调用方读到的当前序号），只有序号匹配才写入。
     * CAS 返回 0 行时说明序号已被其他请求推进（AGM 超时重发 / 并发），
     * 此时回查库内状态并记 WARN，降级返回库内已持久化的状态——
     * <b>不拒绝请求、不中断流程</b>，对外行为与改造前一致。</p>
     *
     * <p>降级分支使用旧的无条件 {@code upsert} 作为兜底：
     * 首次开卡（NOT MATCHED 分支）不受 CAS 影响，但如果 CAS 失败且回查也为 null
     * （状态行被并发删除的极端情况），仍走旧 upsert 保证状态行存在。</p>
     *
     * @param detail          交易明细
     * @param nextStatus      目标状态（USE_COUNT / TXN_SEQ 是「当前值 + 1」的相对增量）
     * @param expectedTxnSeq  CAS 条件：调用方读到的当前 TXN_SEQ；首次开卡时传 null
     * @return 落库结果；{@link WriteResult#status()} 是本次落库后生效的票卡状态
     */
    @Transactional(rollbackFor = Exception.class)
    public WriteResult saveTxnAndAdvanceStatus(QRCodeTxnDetail detail, QRCodeStatus nextStatus,
                                                String expectedTxnSeq) {
        boolean duplicate = false;
        try {
            qrCodeTxnDetailMapper.insert(detail);
        } catch (Exception e) {
            if (!isDuplicateKeyViolation(e)) throw e;
            duplicate = true;
        }
        if (duplicate) {
            QRCodeStatus persisted = qrCodeStatusStore.findByCardId(nextStatus.getCardId());
            if (persisted != null) {
                return new WriteResult(true, 0, persisted);
            }
        }

        // CAS 优先：只有 TXN_SEQ 匹配才推进状态
        int updateCount = qrCodeStatusStore.upsertWithCas(nextStatus, expectedTxnSeq);
        if (updateCount > 0) {
            return new WriteResult(duplicate, updateCount, nextStatus);
        }

        // CAS 返回 0 行：序号已被其他请求推进，回查库内真实状态
        QRCodeStatus actual = qrCodeStatusStore.findByCardId(nextStatus.getCardId());
        if (actual != null) {
            log.warn("IF1A-01 CAS upsert 未命中: cardId={}, expectedTxnSeq={}, actualTxnSeq={}, actualCodeStatus={}. "
                            + "序号已被其他请求推进（AGM 超时重发或并发），降级使用库内状态。",
                    nextStatus.getCardId(), expectedTxnSeq, actual.getTxnSeq(), actual.getCodeStatus());
            return new WriteResult(false, 0, actual);
        }

        // actual 为 null：极端情况（状态行被并发删除），降级走旧的无条件 upsert 兜底
        log.warn("IF1A-01 CAS upsert 未命中且回查为空, cardId={}, expectedTxnSeq={}. 降级走无条件 upsert。",
                nextStatus.getCardId(), expectedTxnSeq);
        updateCount = qrCodeStatusStore.upsert(nextStatus);
        return new WriteResult(duplicate, updateCount, nextStatus);
    }

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     *
     * <p><b>MUST</b> 逐层遍历 cause，<b>NEVER</b> 直接 {@code catch (DuplicateKeyException)}：
     * {@code MapperAspectToTrace}（{@code resource/micro/web/src/main/java/com/chinasofti/huateng/
     * micro/monitor/trace/MapperAspectToTrace.java:51}）在 {@code management.tracing.enabled=true}
     * 时把 mapper 抛出的任何异常统一包成 {@code RuntimeException}，单层类型判断就捕不到——
     * 上面依赖的「重复上送吞掉 DuplicateKeyException、状态照常推进」会失效，AGM 超时重推
     * 会整笔失败、票卡状态停在原地。本模块当前未注入该 env（走
     * {@code resource/micro/web/src/main/resources/web.properties:78} 的默认 {@code false}），
     * 但那是配置巧合而非代码保证：2026-09-08 已在 account-server 与 para-server 实测到失效后果。</p>
     */
    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param duplicate   明细是否因唯一索引冲突而未插入（重复上送）
     * @param updateCount 票卡状态 upsert 影响行数；重复上送时为 0（未执行 upsert）
     * @param status      本次落库后生效的票卡状态：首次上送是传入的 nextStatus，
     *                    重复上送是库里已持久化的那一行。调用方对外输出与推送 <b>MUST</b> 用它，
     *                    <b>NEVER</b> 用入参 nextStatus——后者的 USE_COUNT / TXN_SEQ 是相对增量。
     */
    public record WriteResult(boolean duplicate, int updateCount, QRCodeStatus status) {
    }
}
