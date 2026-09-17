package com.chinasofti.huateng.cardpool.service;

import com.chinasofti.huateng.cardpool.entity.LogicCardPoolBatch;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.github.pagehelper.PageInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 逻辑卡号池服务。 */
public interface CardPoolService {

    /**
     * 预占一个逻辑卡号；同一业务归属重复调用返回同一预占（含已确认发卡的记录，见实现的复用白名单）。
     *
     * @param request 票种与业务归属
     * @return 预占结果；仅在池空或重试耗尽时返回 null
     */
    CardPoolReservationRespDTO reserve(CardPoolReservationReqDTO request);

    /**
     * 确认预占，将卡号置为已分配。
     *
     * @param reservationId 预占标识
     * @param businessId    业务流水号
     * @return 本次生效或已处于已确认终态返回 true
     */
    boolean confirm(String reservationId, String businessId);

    /**
     * 释放预占，将卡号退回可用。
     * NEVER 在失败分支调用：预占按业务键幂等、为并发请求共享，释放会抽走兄弟请求已确认的卡号（ADR-D52）。
     *
     * @param reservationId 预占标识
     * @param businessId    业务流水号
     * @return 本次生效或已处于可用终态返回 true
     */
    boolean release(String reservationId, String businessId);

    /**
     * 创建一个卡号申请批次并立即返回，不在本次调用内做 ACC 申请与文件导入。
     *
     * @param requestedType 票种
     * @param source        来源，AUTO 或 MANUAL
     * @param operator      操作人
     * @return 已落库的 CREATED 批次
     * @throws IllegalArgumentException 票种未启用卡池或来源非法
     * @throws IllegalStateException    该票种已有进行中的批次
     */
    LogicCardPoolBatch requestBatch(String requestedType, String source, String operator);

    /**
     * 重试失败批次；仅接受 FAILED 且已有文件名的批次，状态推进用条件更新保证并发安全。
     *
     * @param batchNo 批次号
     * @return 重试后的批次
     * @throws IllegalArgumentException 批次不存在
     * @throws IllegalStateException    批次状态不允许重试或未取到批次锁
     */
    LogicCardPoolBatch retry(Long batchNo);

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
     * @param pageNum         页码，从 1 开始，非法值按 1 处理
     * @param pageSize        每页条数，非法值按 10 处理，上限 100
     * @return 批次分页结果，创建时间倒序
     */
    PageInfo<LogicCardPoolBatch> batches(Long batchNo,
                                         String cardType,
                                         String accTicketType,
                                         String source,
                                         String status,
                                         LocalDateTime createTimeBegin,
                                         LocalDateTime createTimeEnd,
                                         Integer pageNum,
                                         Integer pageSize);

    /**
     * 各票种卡池水位与最近批次概览。
     *
     * @return 按票种码升序的概览列表
     */
    List<Map<String, Object>> summary();

    /**
     * 执行一轮卡池维护：回收超时预占、按阈值补货、推进待处理与可重试批次。
     *
     * @return 本轮各动作的计数，含 releasedExpired / createdBatches / executedBatches / failedBatches
     */
    Map<String, Object> runMaintenance();
}
