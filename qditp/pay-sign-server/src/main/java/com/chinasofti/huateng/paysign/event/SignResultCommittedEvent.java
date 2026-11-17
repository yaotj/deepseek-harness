package com.chinasofti.huateng.paysign.event;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;


public record SignResultCommittedEvent(String requestSignSeq,
                                       String paymentVendor,
                                       ReceiveSignResultReqDTO callback) {
}
