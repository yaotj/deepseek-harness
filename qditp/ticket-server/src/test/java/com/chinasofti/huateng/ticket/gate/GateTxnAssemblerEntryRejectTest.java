package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class GateTxnAssemblerEntryRejectTest {

    private GateTxnAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new GateTxnAssembler();
        ReflectionTestUtils.setField(assembler, "codeStatusResolver", new GateCodeStatusResolver());
    }

    @Test
    void 入闸被拒绝_保留上次有效入闸信息() {
        // 模拟入闸被拒绝的场景（状态08免费更新）
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setTrxType(TrxTypeCodeEnum.ENTRY.getCode());
        request.setHandleDateTime("20261117170734");
        request.setHandleStationCode("0245");
        request.setExcessFareType(null);
        request.setAdviceOpt("020"); // 免费更新

        QRCodeStatus currentStatus = new QRCodeStatus();
        currentStatus.setCodeStatus(QRCodeStatusEnum.ENTRY.getCode());
        currentStatus.setLastTxnTime("20261117170429");
        currentStatus.setLastTxnStation("0245");

        QRCodeStatus nextStatus = assembler.buildNextStatus(request, currentStatus);

        // 入闸被拒绝时，应该保留上次的 lastTxnTime 和 lastTxnStation
        assertThat(nextStatus.getLastTxnTime()).isEqualTo("20261117170429");
        assertThat(nextStatus.getLastTxnStation()).isEqualTo("0245");
        // 但 gateInTime/gateInStation 仍应更新（记录本次入闸尝试）
        assertThat(nextStatus.getGateInTime()).isEqualTo("20261117170734");
        assertThat(nextStatus.getGateInStation()).isEqualTo("0245");
    }

    @Test
    void 入闸失败_保留上次有效入闸信息() {
        // 模拟入闸失败的场景（TRX_TYPE=04 进站失败）
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setTrxType(TrxTypeCodeEnum.ENTRY_FAIL.getCode());
        request.setHandleDateTime("20261117170734");
        request.setHandleStationCode("0245");
        request.setExcessFareType(null);
        request.setAdviceOpt(null);

        QRCodeStatus currentStatus = new QRCodeStatus();
        currentStatus.setCodeStatus(QRCodeStatusEnum.ENTRY.getCode());
        currentStatus.setLastTxnTime("20261117170429");
        currentStatus.setLastTxnStation("0245");

        QRCodeStatus nextStatus = assembler.buildNextStatus(request, currentStatus);

        // 入闸失败时，应该保留上次的 lastTxnTime 和 lastTxnStation
        assertThat(nextStatus.getLastTxnTime()).isEqualTo("20261117170429");
        assertThat(nextStatus.getLastTxnStation()).isEqualTo("0245");
    }

    @Test
    void 入闸成功_更新入闸信息() {
        // 模拟正常入闸成功的场景
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setTrxType(TrxTypeCodeEnum.ENTRY.getCode());
        request.setHandleDateTime("20261117170734");
        request.setHandleStationCode("0245");
        request.setExcessFareType(null);
        request.setAdviceOpt(null);

        QRCodeStatus currentStatus = new QRCodeStatus();
        currentStatus.setCodeStatus(QRCodeStatusEnum.SJT_ISSUE.getCode());
        currentStatus.setLastTxnTime("20261117160000");
        currentStatus.setLastTxnStation("0100");

        QRCodeStatus nextStatus = assembler.buildNextStatus(request, currentStatus);

        // 入闸成功时，应该更新 lastTxnTime 和 lastTxnStation
        assertThat(nextStatus.getLastTxnTime()).isEqualTo("20261117170734");
        assertThat(nextStatus.getLastTxnStation()).isEqualTo("0245");
    }

    @Test
    void 出闸_更新入闸信息() {
        // 模拟正常出闸场景
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setTrxType(TrxTypeCodeEnum.EXIT.getCode());
        request.setHandleDateTime("20261117171000");
        request.setHandleStationCode("0223");
        request.setExcessFareType(null);
        request.setAdviceOpt(null);

        QRCodeStatus currentStatus = new QRCodeStatus();
        currentStatus.setCodeStatus(QRCodeStatusEnum.ENTRY.getCode());
        currentStatus.setLastTxnTime("20261117170734");
        currentStatus.setLastTxnStation("0245");

        QRCodeStatus nextStatus = assembler.buildNextStatus(request, currentStatus);

        // 出闸时，lastTxnTime/lastTxnStation 应该更新为出闸信息
        assertThat(nextStatus.getLastTxnTime()).isEqualTo("20261117171000");
        assertThat(nextStatus.getLastTxnStation()).isEqualTo("0223");
    }
}
