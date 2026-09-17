package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;

/** 行程三态：无过闸 / 进站 / 出站。 */
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
