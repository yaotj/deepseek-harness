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

/**
 * 逻辑卡号池接口。
 *
 * <p>{@code /internal/**} 供内部服务调用，{@code /page/**} 供运营后台调用。
 * 申请批次只落库即返回，实际的 ACC 申请与文件导入由 {@code /card-pools/maintenance} 推进。</p>
 */
@RestController
public class CardPoolController {

    private final CardPoolService cardPoolService;

    /**
     * 构造控制器。
     *
     * @param cardPoolService 卡号池服务
     */
    public CardPoolController(CardPoolService cardPoolService) {
        this.cardPoolService = cardPoolService;
    }

    /**
     * 预占一个逻辑卡号。
     *
     * <p>返回值三分，供调用方区分降级方式，NEVER 再把三者混成一句「无可分配逻辑卡号」：</p>
     * <ul>
     *   <li>{@code code=200 data!=null} —— 预占成功；</li>
     *   <li>{@code code=200 data=null} —— 该票种卡池已空，属正常业务结果，可提示稍后重试或走降级发卡；</li>
     *   <li>{@code code=400} —— 票种不走卡池、票种非法、业务归属缺失或归属冲突，属调用方缺陷，重试无用。</li>
     * </ul>
     *
     * @param request 票种与业务归属
     * @return 预占结果
     */
    @PostMapping("/internal/card-pools/reservations")
    public ResultVO<CardPoolReservationRespDTO> reserve(@RequestBody CardPoolReservationReqDTO request) {
        try {
            return ResultMapper.ok(cardPoolService.reserve(request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResultMapper.illegalParams(ex.getMessage());
        }
    }

    /**
     * 确认预占。
     *
     * @param reservationId 预占标识
     * @param request       业务流水号
     * @return 确认结果
     */
    @PostMapping("/internal/card-pools/reservations/{reservationId}/confirm")
    public ResultVO<Void> confirm(@PathVariable String reservationId,
                                  @RequestBody CardPoolReservationActionReqDTO request) {
        String businessId = request == null ? null : request.getBusinessId();
        if (cardPoolService.confirm(reservationId, businessId)) {
            return ResultMapper.ok();
        }
        return ResultMapper.error("预占记录不存在或状态不允许确认");
    }

    /**
     * 释放预占。
     *
     * @param reservationId 预占标识
     * @param request       业务流水号
     * @return 释放结果
     */
    @PostMapping("/internal/card-pools/reservations/{reservationId}/release")
    public ResultVO<Void> release(@PathVariable String reservationId,
                                  @RequestBody CardPoolReservationActionReqDTO request) {
        String businessId = request == null ? null : request.getBusinessId();
        if (cardPoolService.release(reservationId, businessId)) {
            return ResultMapper.ok();
        }
        return ResultMapper.error("预占记录不存在或状态不允许释放");
    }

    /**
     * 各票种卡池水位概览。
     *
     * @return 概览列表
     */
    @GetMapping({"/internal/card-pools/summary", "/page/card-pools/summary"})
    public ResultVO<List<Map<String, Object>>> summary() {
        return ResultMapper.ok(cardPoolService.summary());
    }

    /**
     * 按条件分页查询批次。
     *
     * @param batchNo         批次号
     * @param cardType        票种
     * @param accTicketType   ACC 票种
     * @param source          来源
     * @param status          状态
     * @param createTimeBegin 创建时间起
     * @param createTimeEnd   创建时间止
     * @param pageNum         页码，从 1 开始
     * @param pageSize        每页条数，上限 100
     * @return 批次分页结果
     */
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

    /**
     * 创建卡号申请批次，立即返回 CREATED 批次，不在本次请求内下载导入。
     *
     * @param request 含 cardType 与可选 operator
     * @return 已创建批次
     */
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

    /**
     * 重试失败批次。
     *
     * @param batchNo 批次号
     * @return 重试后的批次
     */
    @PostMapping({"/internal/card-pools/batches/{batchNo}/retry", "/page/card-pools/batches/{batchNo}/retry"})
    public ResultVO<LogicCardPoolBatch> retry(@PathVariable Long batchNo) {
        try {
            return ResultMapper.ok(cardPoolService.retry(batchNo));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResultMapper.error(ex.getMessage());
        }
    }

    /**
     * 受理一轮卡池维护：回收超时预占、按阈值补货、推进待处理与可重试批次。
     *
     * <p>由 web-admin 的 Quartz 任务或运维按需触发；本模块不注册 {@code @Scheduled}。</p>
     *
     * <p>维护含 ACC 调用、FTP 下载与分片入库，耗时不可控，因此接口只做受理：提交到单线程
     * 维护线程池后立即返回，执行结果看服务端日志与 {@code /card-pools/summary}。
     * 上一轮尚未结束时返回 {@code accepted=false}。</p>
     *
     * @return {@code accepted} 与 {@code message} 两项受理结果
     */
    @PostMapping({"/internal/card-pools/maintenance", "/page/card-pools/maintenance"})
    public ResultVO<Map<String, Object>> maintenance() {
        return ResultMapper.ok(cardPoolService.runMaintenance());
    }


}
