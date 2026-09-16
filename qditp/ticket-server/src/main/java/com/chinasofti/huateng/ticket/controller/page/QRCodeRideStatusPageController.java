package com.chinasofti.huateng.ticket.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.model.page.RideStatusUpdateRequest;
import com.chinasofti.huateng.ticket.query.OperationRideStatusService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户运营端二维码乘车状态查询与人工调整。
 *
 * <p>2026-09-14 起状态白名单、状态码归一与审计日志下沉到
 * {@link OperationRideStatusService}，本类只做入参非空校验与应答装配。
 * **NEVER 改回直接注 {@code QRCodeStatusMapper}** —— controller 直连 mapper 违反
 * AGENTS.md §3.3，且会让「运营端能改成哪些状态」这条白名单绕过 service 层。
 */
@RestController
@RequestMapping("/page/ride-status")
public class QRCodeRideStatusPageController {

    private final OperationRideStatusService rideStatusService;

    public QRCodeRideStatusPageController(OperationRideStatusService rideStatusService) {
        this.rideStatusService = rideStatusService;
    }

    /** 根据逻辑卡号查询二维码乘车状态。 */
    @GetMapping
    public ResultVO<QRCodeStatus> query(@RequestParam String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return ResultMapper.illegalParams("cardId不能为空");
        }
        QRCodeStatus status = rideStatusService.findByCardId(cardId.trim());
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

        OperationRideStatusService.UpdateOutcome outcome = rideStatusService.updateCodeStatus(
                cardId.trim(), request.getCodeStatus(), request.getChangeReason().trim());
        return switch (outcome.result()) {
            case OK -> ResultMapper.ok(outcome.status());
            case STATUS_NOT_ALLOWED -> ResultMapper.illegalParams("不支持的乘车状态编码");
            case CARD_NOT_FOUND -> ResultMapper.error("未查询到该逻辑卡号的乘车状态");
        };
    }
}
