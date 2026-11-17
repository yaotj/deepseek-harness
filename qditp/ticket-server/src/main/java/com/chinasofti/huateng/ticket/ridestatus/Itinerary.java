package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;

public sealed interface Itinerary {

    record NoTxn(QRCodeStatus status) implements Itinerary {}

    record Entry(QRCodeStatus status, QRCodeTxnDetail detail) implements Itinerary {}

    record Exit(QRCodeStatus status, QRCodeTxnDetail detail) implements Itinerary {}


    static Itinerary of(QRCodeStatus status, QRCodeTxnDetail detail) {
        if (detail == null) {
            return new NoTxn(status);
        }
        return TrxTypeCodeEnum.isExitTxn(detail.getTrxType())
                ? new Exit(status, detail)
                : new Entry(status, detail);
    }
}
