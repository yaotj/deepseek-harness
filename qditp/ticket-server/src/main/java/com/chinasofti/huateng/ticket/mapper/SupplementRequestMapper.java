package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.ticket.entity.SupplementRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * IF5A-03 补站请求台账。
 *
 * <p>幂等靠 {@code UK_QSR_CARD_SEQ_ADVICE}（{@code CARD_ID + TXN_SEQ + ADVICE_OPT}）+
 * {@code DuplicateKeyException} 兜底，符合 AGENTS.md §5.1 末条。
 * <b>三个键列都 NOT NULL</b>：Oracle 的唯一索引对 NULL 不去重，
 * 任何一列允许 NULL 都会让并发防护静默失效，因此 {@code TXN_SEQ} 在写入前已由调用方兜成 {@code '0'}。</p>
 */
@Mapper
public interface SupplementRequestMapper {

    /**
     * 声明一次补站请求。冲突时抛 {@code DuplicateKeyException}（可能被切面包一层，
     * 调用方 MUST 沿 {@code getCause()} 链判定，见 AGENTS.md §5.2 / ADR-D53）。
     */
    int insertClaim(SupplementRequest record);

    SupplementRequest selectByKey(@Param("cardId") String cardId,
                                 @Param("txnSeq") String txnSeq,
                                 @Param("adviceOpt") String adviceOpt);

    /**
     * 把上一次已被闸机明确拒绝的请求重新置为处理中，供 BOM 合法重发。
     *
     * <p>CAS 条件是 {@code HANDLE_STATUS = 'REJECTED'}：
     * {@code PENDING}（并发在飞）与 {@code SUCCESS} / {@code UNKNOWN}（结果已成事实或不明）
     * <b>一律命中 0 行</b>，NEVER 放宽这个条件。</p>
     *
     * @return 1 = 重新声明成功，0 = 不可重新声明
     */
    int reclaimRejected(@Param("cardId") String cardId,
                        @Param("txnSeq") String txnSeq,
                        @Param("adviceOpt") String adviceOpt,
                        @Param("codeStatusSnapshot") String codeStatusSnapshot,
                        @Param("handleStationCode") String handleStationCode,
                        @Param("handleDateTime") String handleDateTime,
                        @Param("trxAmount") String trxAmount);

    /**
     * 收口：只允许从 {@code PENDING} 迁出，避免重复回写把终态覆盖掉。
     *
     * @return 影响行数，0 表示已被别人收口过
     */
    int finishClaim(@Param("cardId") String cardId,
                    @Param("txnSeq") String txnSeq,
                    @Param("adviceOpt") String adviceOpt,
                    @Param("handleStatus") String handleStatus,
                    @Param("failReason") String failReason);

    /**
     * 扫出结果未知的请求，供人工或外部定时任务处置。
     *
     * <p><b>目前没有服务内 `@Scheduled` 驱动它</b>：ticket-server 启动类没有 `@EnableScheduling`，
     * 按 AGENTS.md §2.2.1 新增定时任务 MUST 建在 web-admin 的 `sys_job`。
     * 本方法先把「能查」这一步落地，调度接线是独立事项。</p>
     */
    List<SupplementRequest> selectUnknown(@Param("limit") int limit);
}
