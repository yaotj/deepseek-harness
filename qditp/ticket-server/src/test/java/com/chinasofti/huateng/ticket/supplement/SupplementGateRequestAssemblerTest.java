package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplementGateRequestAssemblerTest {

    private final SupplementGateRequestAssembler assembler = new SupplementGateRequestAssembler();

    @Test
    void BOM补票传02自助补站传03() {
        assertEquals("02", build("02").getChannelType());
        assertEquals("03", build("03").getChannelType());
    }

    private NotifyVerifyResultReqDTO build(String channelType) {
        QRCodeStatus status = new QRCodeStatus();
        status.setChannel("01");
        status.setCodeStatus("04");
        status.setLastTxnStation("0101");
        status.setLastTxnTime("20261117120000");
        return assembler.newBaseRequest(status, "C001", "00522986", "02", "20261117130000",
                "0102", "0441", "03", channelType);
    }
}
