package com.chinasofti.huateng.ticket.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import com.chinasofti.huateng.ticket.model.page.RideStatusUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 用户运营端二维码乘车状态查询与人工调整。
 */
@RestController
@RequestMapping("/page/ride-status")
public class QRCodeRideStatusPageController {
    private static final Logger log = LoggerFactory.getLogger(QRCodeRideStatusPageController.class);
    private static final Set<String> ALLOWED_CODE_STATUS = Set.of("02", "03", "04", "05", "06", "08", "09", "10", "80", "81");

    private final QRCodeStatusMapper qrCodeStatusMapper;

    public QRCodeRideStatusPageController(QRCodeStatusMapper qrCodeStatusMapper) {
        this.qrCodeStatusMapper = qrCodeStatusMapper;
    }

    /** 根据逻辑卡号查询二维码乘车状态。 */
    @GetMapping
    public ResultVO<QRCodeStatus> query(@RequestParam String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return ResultMapper.illegalParams("cardId不能为空");
        }
        QRCodeStatus status = qrCodeStatusMapper.selectByCardId(cardId.trim());
        return status == null ? ResultMapper.error("未查询到该逻辑卡号的乘车状态") : ResultMapper.ok(status);
    }

    /** 人工修改状态时必须填写原因，且只能写入运营确认的状态码集合。 */
    @PutMapping("/{cardId}")
    public ResultVO<QRCodeStatus> update(@PathVariable String cardId, @RequestBody RideStatusUpdateRequest request) {
        if (!StringUtils.hasText(cardId)) {
            return ResultMapper.illegalParams("cardId不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getCodeStatus())) {
            return ResultMapper.illegalParams("codeStatus不能为空");
        }
        if (!StringUtils.hasText(request.getChangeReason())) {
            return ResultMapper.illegalParams("changeReason不能为空");
        }

        String normalizedStatus = normalizeStatus(request.getCodeStatus());
        if (!ALLOWED_CODE_STATUS.contains(normalizedStatus)) {
            return ResultMapper.illegalParams("不支持的乘车状态编码");
        }
        String normalizedCardId = cardId.trim();
        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(normalizedCardId);
        if (currentStatus == null) {
            return ResultMapper.error("未查询到该逻辑卡号的乘车状态");
        }

        qrCodeStatusMapper.updateCodeStatus(normalizedCardId, normalizedStatus);
        QRCodeStatus updatedStatus = qrCodeStatusMapper.selectByCardId(normalizedCardId);
        log.warn("运营端人工修改乘车状态, cardId={}, beforeStatus={}, afterStatus={}, reason={}",
                normalizedCardId, currentStatus.getCodeStatus(), normalizedStatus, request.getChangeReason().trim());
        return ResultMapper.ok(updatedStatus);
    }

    private String normalizeStatus(String codeStatus) {
        // 页面可输入“0x04”或“04”，入库时统一保存两位十六进制状态码。
        String value = codeStatus.trim().toUpperCase();
        return value.startsWith("0X") ? value.substring(2) : value;
    }
}
