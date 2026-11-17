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

@Service
public class OperationRideStatusService {

    private static final Logger log = LoggerFactory.getLogger(OperationRideStatusService.class);

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

    public enum UpdateResult {
        OK,
        STATUS_NOT_ALLOWED,
        CARD_NOT_FOUND
    }

    public record UpdateOutcome(UpdateResult result, QRCodeStatus status) {
    }

    public QRCodeStatus findByCardId(String cardId) {
        QRCodeStatus status = qrCodeStatusStore.findByCardId(cardId);
        fillStationNames(status);
        return status;
    }

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

    private String normalizeStatus(String codeStatus) {
        String value = codeStatus.trim().toUpperCase();
        return value.startsWith("0X") ? value.substring(2) : value;
    }
}
