package com.chinasofti.huateng.cardpool.service;

import com.chinasofti.huateng.cardpool.entity.LogicCardPoolBatch;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.github.pagehelper.PageInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CardPoolService {


    CardPoolReservationRespDTO reserve(CardPoolReservationReqDTO request);


    boolean confirm(String reservationId, String businessId);


    boolean release(String reservationId, String businessId);


    LogicCardPoolBatch requestBatch(String requestedType, String source, String operator);


    LogicCardPoolBatch retry(Long batchNo);


    PageInfo<LogicCardPoolBatch> batches(Long batchNo,
                                         String cardType,
                                         String accTicketType,
                                         String source,
                                         String status,
                                         LocalDateTime createTimeBegin,
                                         LocalDateTime createTimeEnd,
                                         Integer pageNum,
                                         Integer pageSize);


    List<Map<String, Object>> summary();


    Map<String, Object> runMaintenance();
}
