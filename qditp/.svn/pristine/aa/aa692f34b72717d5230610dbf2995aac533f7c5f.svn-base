package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.entity.UserAccTicketNo;
import com.chinasofti.huateng.account.mapper.UserAccTicketNoMapper;
import com.chinasofti.huateng.account.model.card.FileNoticeReqDTO;
import com.chinasofti.huateng.account.model.card.LogicalCardRequestReqDTO;
import com.chinasofti.huateng.account.service.CardPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CardPoolServiceImpl implements CardPoolService {
    private static final Logger log = LoggerFactory.getLogger(CardPoolServiceImpl.class);
    private final AtomicInteger idGenerator = new AtomicInteger(1);
    @Autowired
    private UserAccTicketNoMapper userAccTicketNoMapper;
    @Value("${account.card-pool.threshold:10}")
    private Integer threshold;
    @Value("${account.card-pool.batch-size:20}")
    private Integer batchSize;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccTicketNo allocateNextCard(String thirdUserId) {
        monitorCardPool();
        for (int i = 0; i < 3; i++) {
            UserAccTicketNo nextCard = userAccTicketNoMapper.selectUnusedCard();
            if (nextCard == null) {
                requestLogicalCardNo(buildRequest(batchSize));
                nextCard = userAccTicketNoMapper.selectUnusedCard();
            }
            if (nextCard == null) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = userAccTicketNoMapper.updateAllocateCard(nextCard.getId(), thirdUserId, now);
            if (updated > 0) {
                nextCard.setThirdUserId(thirdUserId);
                nextCard.setRegTms(now);
                return nextCard;
            }
        }
        return null;
    }

    @Override
    public void monitorCardPool() {
        long unusedCount = userAccTicketNoMapper.countUnusedCards();
        log.info("卡号池监控, 当前未使用卡号数量={}", unusedCount);
        if (unusedCount <= threshold) {
            requestLogicalCardNo(buildRequest(batchSize));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestLogicalCardNo(LogicalCardRequestReqDTO request) {
        int requestCount = request == null || request.getRequestCount() == null ? batchSize : request.getRequestCount();
        // TODO 对接 Ticket/ACC 卡号申请接口，按 requestCount 真实申请逻辑卡号。
        log.info("准备申请逻辑卡号, count={}", requestCount);
        FileNoticeReqDTO fileNotice = new FileNoticeReqDTO();
        fileNotice.setFileName("CARD_POOL_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".txt");
        fileNotice.setFilePath("/download/cardPool");
        fileNotice.setFileDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        handleFileNotice(fileNotice);

        List<UserAccTicketNo> ticketNoList = new ArrayList<>(requestCount);
        for (int i = 0; i < requestCount; i++) {
            UserAccTicketNo ticketNo = new UserAccTicketNo();
            ticketNo.setCardId(buildCardId());
            ticketNo.setInsertTms(LocalDateTime.now());
            ticketNoList.add(ticketNo);
        }
        if (!ticketNoList.isEmpty()) {
            userAccTicketNoMapper.batchInsert(ticketNoList);
        }
    }

    @Override
    public void handleFileNotice(FileNoticeReqDTO request) {
        // TODO 对接 Ticket 文件通知处理流程，下载并解析卡号文件，再把卡号落入卡号池表。
        log.info("接收到文件通知, fileName={}, filePath={}, fileDate={}",
                request.getFileName(), request.getFilePath(), request.getFileDate());
        log.info("待执行文件下载和解析流程");
    }

    private LogicalCardRequestReqDTO buildRequest(Integer requestCount) {
        LogicalCardRequestReqDTO request = new LogicalCardRequestReqDTO();
        request.setRequestCount(requestCount);
        return request;
    }

    private String buildCardId() {
        return "QD" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
                + String.format("%06d", idGenerator.getAndIncrement());
    }
}
