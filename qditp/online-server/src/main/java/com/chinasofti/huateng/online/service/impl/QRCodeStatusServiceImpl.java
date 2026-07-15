package com.chinasofti.huateng.online.service.impl;

import com.chinasofti.huateng.online.entity.QRCodeStatus;
import com.chinasofti.huateng.online.mapper.QRCodeStatusMapper;
import com.chinasofti.huateng.online.model.agm.AgmDtos;
import com.chinasofti.huateng.online.service.QRCodeStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 二维码票卡状态服务实现。
 * 将 QRCodeStatus 的初始化、状态流转、行业数据组装集中处理，便于 AGM/BOM/TVM 共用。
 */
@Service
public class QRCodeStatusServiceImpl implements QRCodeStatusService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${online.default-provider-id:01}")
    private String defaultProviderId;

    @Value("${online.default-issue-channel-code:01}")
    private String defaultIssueChannelCode;

    @Value("${online.default-card-type:0441}")
    private String defaultCardType;

    @Value("${online.default-sign-channel-code:01}")
    private String defaultSignChannelCode;

    @Autowired
    private QRCodeStatusMapper qrCodeStatusMapper;

    @Override
    public QRCodeStatus getOrInitByCardId(String cardId) {
        QRCodeStatus qrCodeStatus = findByCardId(cardId);
        if (qrCodeStatus != null) {
            return qrCodeStatus;
        }
        qrCodeStatus = new QRCodeStatus();
        qrCodeStatus.setCardId(cardId);
        qrCodeStatus.setItpUserId(String.valueOf(Math.abs(cardId.hashCode())));
        qrCodeStatus.setProviderId(defaultProviderId);
        qrCodeStatus.setCardType(defaultCardType);
        qrCodeStatus.setCardStatus("03");
        qrCodeStatus.setIssueChannelCode(defaultIssueChannelCode);
        qrCodeStatus.setSignChannelCode(defaultSignChannelCode);
        qrCodeStatus.setLastLineCode("01");
        qrCodeStatus.setLastStationCode("FFFF");
        qrCodeStatus.setLastTransAmount(0);
        qrCodeStatus.setLastTicketTransSeq("0");
        qrCodeStatus.setQueryLockedYn("N");
        qrCodeStatus.setIndustryData(buildIndustryData(qrCodeStatus));
        qrCodeStatus.setCreateTms(LocalDateTime.now());
        qrCodeStatus.setUpdateTms(LocalDateTime.now());
        save(qrCodeStatus);
        return qrCodeStatus;
    }

    @Override
    public QRCodeStatus findByCardId(String cardId) {
        return qrCodeStatusMapper.selectByCardId(cardId);
    }

    @Override
    public QRCodeStatus findByItpUserId(String itpUserId) {
        return qrCodeStatusMapper.selectByItpUserId(itpUserId);
    }

    @Override
    public void save(QRCodeStatus qrCodeStatus) {
        qrCodeStatusMapper.upsert(qrCodeStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QRCodeStatus handleAgmVerifyResult(AgmDtos.NotiVerifyResultReqDTO request, String deviceId) {
        QRCodeStatus qrCodeStatus = getOrInitByCardId(request.getCardId());
        qrCodeStatus.setItpUserId(defaultString(request.getItpUserId(), qrCodeStatus.getItpUserId()));
        qrCodeStatus.setIssueChannelCode(defaultString(request.getIssueChannelCode(), qrCodeStatus.getIssueChannelCode()));
        qrCodeStatus.setSignChannelCode(defaultString(request.getSignChannelCode(), qrCodeStatus.getSignChannelCode()));
        qrCodeStatus.setCardType(defaultString(request.getCardType(), qrCodeStatus.getCardType()));
        qrCodeStatus.setLastStationCode(defaultString(request.getHandleStationCode(), qrCodeStatus.getLastStationCode()));
        qrCodeStatus.setLastHandleDateTime(parseDateTime(request.getHandleDateTime()));
        qrCodeStatus.setLastTransAmount(parseInteger(request.getTrxAmount()));
        qrCodeStatus.setLastTicketTransSeq(defaultString(request.getTikcetTransSeq(), qrCodeStatus.getLastTicketTransSeq()));
        qrCodeStatus.setLastDeviceId(deviceId);
        qrCodeStatus.setCardStatus(resolveAgmStatus(request));
        qrCodeStatus.setQueryLockedYn("089".equals(request.getHandleResultCode()) ? "Y" : "N");
        qrCodeStatus.setIndustryData(buildIndustryData(qrCodeStatus));
        qrCodeStatus.setUpdateTms(LocalDateTime.now());
        save(qrCodeStatus);
        return qrCodeStatus;
    }

    @Override
    public String buildIndustryData(QRCodeStatus qrCodeStatus) {
        String raw = defaultString(qrCodeStatus.getItpUserId(), "0")
                + "|" + defaultString(qrCodeStatus.getCardStatus(), "03")
                + "|" + defaultString(qrCodeStatus.getLastStationCode(), "FFFF")
                + "|" + formatDateTime(qrCodeStatus.getLastHandleDateTime())
                + "|" + defaultString(qrCodeStatus.getCardId(), "");
        return HexFormat.of().formatHex(raw.getBytes(StandardCharsets.UTF_8)).toUpperCase();
    }

    /**
     * 将 AGM 检票结果映射为平台侧二维码状态码。
     * 当前先保留最小可联调规则，后续可按线路正式规则继续细化。
     */
    private String resolveAgmStatus(AgmDtos.NotiVerifyResultReqDTO request) {
        if ("089".equals(request.getHandleResultCode())) {
            return "50";
        }
        if (!"00".equals(defaultString(request.getHandleResultCode(), "00"))) {
            return defaultString(request.getLastTicketStatus(), "03");
        }
        if ("01".equals(request.getTrxType())) {
            return "04";
        }
        if ("02".equals(request.getTrxType())) {
            return "05";
        }
        if ("03".equals(request.getTrxType())) {
            return "06";
        }
        return defaultString(request.getLastTicketStatus(), "03");
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATETIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATETIME_FORMATTER);
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
