package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;

/**
 * 行程三态：无过闸 / 进站 / 出站。
 *
 * <p>三态判定只在 {@link #of} 一处，Assembler 用穷尽 {@code switch} 消费，
 * 与 {@code rpc/RpcOutcome}（ADR-D45）同一设计动机。
 *
 * <p><b>sealed 保护的边界要说清</b>：新增一个 {@code Itinerary} 子类型时，
 * Assembler 的两个 {@code switch} 会**编译期失败**；但**新增一种 {@code trxType} 码值不会**——
 * 那只会静默走进 {@link #of} 的兜底分支。因此改 {@link TrxTypeCodeEnum} 时
 * MUST 回来核对 {@link #of} 的分类，NEVER 以为「有 sealed 就自动安全」。
 *
 * <p>NEVER 在 sealed 体系之外再写 {@code if (detail == null)} 或
 * {@code isExitTxn(trxType)} 分支——那是本改造要消灭的重复判定。
 */
public sealed interface Itinerary {

    /** 尚无过闸记录，只有开卡状态。 */
    record NoTxn(QRCodeStatus status) implements Itinerary {}

    /** 最近一笔是进站（含补进站）。 */
    record Entry(QRCodeStatus status, QRCodeTxnDetail detail) implements Itinerary {}

    /** 最近一笔是出站（正常出站 / 超时出站）。 */
    record Exit(QRCodeStatus status, QRCodeTxnDetail detail) implements Itinerary {}

    /**
     * 唯一的三态分类入口。
     *
     * <p>分类口径（{@link TrxTypeCodeEnum}）：
     * <ul>
     *   <li>{@code detail == null} → {@link NoTxn}</li>
     *   <li>{@code 02} 出站 / {@code 03} 超时出站 → {@link Exit}</li>
     *   <li>{@code 01} 进站，以及 {@code 04} 进站失败、{@code 99} 异常、{@code trxType} 为空
     *       → {@link Entry}</li>
     * </ul>
     * 末一档是**有意的兜底**：这三种码值在 {@code QRCODE_TXN_DETAIL} 里本不该成为「最近一笔」，
     * 真出现时按进站口径展示（上一站取 {@code LAST_HANDLE_*}）比丢字段更安全。
     * 要改这一档的归属 MUST 同步改 {@code MemberItineraryAssemblerTest} 的基线。
     *
     * @param status 乘车码当前状态，非空
     * @param detail 最近一次过闸明细，可为空
     * @return 三态之一
     */
    static Itinerary of(QRCodeStatus status, QRCodeTxnDetail detail) {
        if (detail == null) {
            return new NoTxn(status);
        }
        return TrxTypeCodeEnum.isExitTxn(detail.getTrxType())
                ? new Exit(status, detail)
                : new Entry(status, detail);
    }
}
