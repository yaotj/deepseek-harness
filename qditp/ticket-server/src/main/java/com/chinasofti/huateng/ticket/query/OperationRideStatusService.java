package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 运营后台乘车状态查询与人工调整。 */
@Service
public class OperationRideStatusService {

    private static final Logger log = LoggerFactory.getLogger(OperationRideStatusService.class);

    /** 运营端允许人工写入的状态码集合。 */
    private static final Set<String> ALLOWED_CODE_STATUS = Stream.of(
            QRCodeStatusEnum.END_TRIP,
            QRCodeStatusEnum.SJT_ISSUE,
            QRCodeStatusEnum.ENTRY,
            QRCodeStatusEnum.EXIT,
            QRCodeStatusEnum.EXIT_OVERTIME,
            QRCodeStatusEnum.UPDATE_FREE,
            QRCodeStatusEnum.UPDATE_PAY,
            QRCodeStatusEnum.UPDATE_ENTRY,
            QRCodeStatusEnum.SELF_SERVICE_EXIT,
            QRCodeStatusEnum.SELF_SERVICE_ENTRY
    ).map(QRCodeStatusEnum::getCode).collect(Collectors.toSet());

    private final QRCodeStatusStore qrCodeStatusStore;
    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    public OperationRideStatusService(QRCodeStatusStore qrCodeStatusStore, QRCodeTxnDetailMapper qrCodeTxnDetailMapper) {
        this.qrCodeStatusStore = qrCodeStatusStore;
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
    }

    /** 人工调整的三种收口，由 controller 翻成页面应答。 */
    public enum UpdateResult {
        OK,
        /** 目标状态码不在运营白名单内。 */
        STATUS_NOT_ALLOWED,
        /** 该逻辑卡号没有乘车状态行。 */
        CARD_NOT_FOUND
    }

    /** 人工调整的结果。 */
    public record UpdateOutcome(UpdateResult result, QRCodeStatus status) {
    }

    /** 按逻辑卡号查乘车状态，查不到返回 {@code null}。 */
    public QRCodeStatus findByCardId(String cardId) {
        QRCodeStatus status = qrCodeStatusStore.findByCardId(cardId);
        fillStationNames(status);
        return status;
    }

    /** 运营端展示用：按进站/末次交易车站编码批量查 STATION_INFO 回填中文名； 查不到的保持 null，由前端回退显示编码。 */
    private void fillStationNames(QRCodeStatus status) {
        if (status == null) {
            return;
        }
        Set<String> codes = new HashSet<>();
        if (StringUtils.hasText(status.getGateInStation())) {
            codes.add(status.getGateInStation().trim());
        }
        if (StringUtils.hasText(status.getLastTxnStation())) {
            codes.add(status.getLastTxnStation().trim());
        }
        if (codes.isEmpty()) {
            return;
        }
        Map<String, String> nameByCode = new HashMap<>();
        for (Map<String, Object> row : qrCodeTxnDetailMapper.selectStationNames(new ArrayList<>(codes))) {
            Object code = row.get("STATION_CODE");
            Object name = row.get("STATION_NAME");
            if (code != null && name != null) {
                nameByCode.put(String.valueOf(code), String.valueOf(name));
            }
        }
        if (StringUtils.hasText(status.getGateInStation())) {
            status.setGateInStationName(nameByCode.get(status.getGateInStation().trim()));
        }
        if (StringUtils.hasText(status.getLastTxnStation())) {
            status.setLastTxnStationName(nameByCode.get(status.getLastTxnStation().trim()));
        }
    }

    /**
     * 人工调整乘车状态。
     *
     * @param cardId 逻辑卡号，调用方已 trim
     * @param codeStatus 目标状态码，页面可传 {@code 0x04} 或 {@code 04}
     * @param changeReason 变更原因，调用方已校验非空，只进审计日志
     */
    public UpdateOutcome updateCodeStatus(String cardId, String codeStatus, String changeReason) {
        String normalizedStatus = normalizeStatus(codeStatus);
        if (!ALLOWED_CODE_STATUS.contains(normalizedStatus)) {
            return new UpdateOutcome(UpdateResult.STATUS_NOT_ALLOWED, null);
        }

        QRCodeStatus currentStatus = qrCodeStatusStore.findByCardId(cardId);
        if (currentStatus == null) {
            return new UpdateOutcome(UpdateResult.CARD_NOT_FOUND, null);
        }

        qrCodeStatusStore.updateCodeStatus(cardId, normalizedStatus);
        QRCodeStatus updatedStatus = qrCodeStatusStore.findByCardId(cardId);
        fillStationNames(updatedStatus);
        log.warn("运营端人工修改乘车状态, cardId={}, beforeStatus={}, afterStatus={}, reason={}",
                cardId, currentStatus.getCodeStatus(), normalizedStatus, changeReason);
        return new UpdateOutcome(UpdateResult.OK, updatedStatus);
    }

    /** 页面可输入 {@code 0x04} 或 {@code 04}，入库统一保存两位十六进制状态码。 */
    private String normalizeStatus(String codeStatus) {
        String value = codeStatus.trim().toUpperCase();
        return value.startsWith("0X") ? value.substring(2) : value;
    }
}
