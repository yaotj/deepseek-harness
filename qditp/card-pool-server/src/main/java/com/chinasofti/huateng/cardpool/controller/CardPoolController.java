package com.chinasofti.huateng.cardpool.controller;

import com.chinasofti.huateng.cardpool.entity.LogicCardPoolBatch;
import com.chinasofti.huateng.cardpool.service.CardPoolService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationActionReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.github.pagehelper.PageInfo;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class CardPoolController {

    private final CardPoolService cardPoolService;


    public CardPoolController(CardPoolService cardPoolService) {
        this.cardPoolService = cardPoolService;
    }


    @PostMapping("/internal/card-pools/reservations")
    public ResultVO<CardPoolReservationRespDTO> reserve(@RequestBody CardPoolReservationReqDTO request) {
        try {
            return ResultMapper.ok(cardPoolService.reserve(request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResultMapper.illegalParams(ex.getMessage());
        }
    }


    @PostMapping("/internal/card-pools/reservations/{reservationId}/confirm")
    public ResultVO<Void> confirm(@PathVariable String reservationId,
                                  @RequestBody CardPoolReservationActionReqDTO request) {
        String businessId = request == null ? null : request.getBusinessId();
        if (cardPoolService.confirm(reservationId, businessId)) {
            return ResultMapper.ok();
        }
        return ResultMapper.error("预占记录不存在或状态不允许确认");
    }


    @PostMapping("/internal/card-pools/reservations/{reservationId}/release")
    public ResultVO<Void> release(@PathVariable String reservationId,
                                  @RequestBody CardPoolReservationActionReqDTO request) {
        String businessId = request == null ? null : request.getBusinessId();
        if (cardPoolService.release(reservationId, businessId)) {
            return ResultMapper.ok();
        }
        return ResultMapper.error("预占记录不存在或状态不允许释放");
    }


    @GetMapping({"/internal/card-pools/summary", "/page/card-pools/summary"})
    public ResultVO<List<Map<String, Object>>> summary() {
        return ResultMapper.ok(cardPoolService.summary());
    }


    @GetMapping({"/internal/card-pools/batches", "/page/card-pools/batches"})
    public ResultVO<PageInfo<LogicCardPoolBatch>> batches(
            @RequestParam(required = false) Long batchNo,
            @RequestParam(required = false) String cardType,
            @RequestParam(required = false) String accTicketType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createTimeBegin,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createTimeEnd,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ResultMapper.ok(cardPoolService.batches(batchNo, cardType, accTicketType, source, status,
                createTimeBegin, createTimeEnd, pageNum, pageSize));
    }


    @PostMapping({"/internal/card-pools/batches", "/page/card-pools/batches"})
    public ResultVO<LogicCardPoolBatch> createBatch(@RequestBody Map<String, String> request) {
        String cardType = request == null ? null : request.get("cardType");
        if (!StringUtils.hasText(cardType)) {
            return ResultMapper.illegalParams("cardType不能为空");
        }
        try {
            String operator = request.getOrDefault("operator", "WEB");
            return ResultMapper.ok(cardPoolService.requestBatch(cardType, "MANUAL", operator));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResultMapper.error(ex.getMessage());
        }
    }


    @PostMapping({"/internal/card-pools/batches/{batchNo}/retry", "/page/card-pools/batches/{batchNo}/retry"})
    public ResultVO<LogicCardPoolBatch> retry(@PathVariable Long batchNo) {
        try {
            return ResultMapper.ok(cardPoolService.retry(batchNo));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResultMapper.error(ex.getMessage());
        }
    }


    @PostMapping({"/internal/card-pools/maintenance", "/page/card-pools/maintenance"})
    public ResultVO<Map<String, Object>> maintenance() {
        return ResultMapper.ok(cardPoolService.runMaintenance());
    }


}
