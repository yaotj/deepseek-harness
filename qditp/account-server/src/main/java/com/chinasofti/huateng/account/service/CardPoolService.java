package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.entity.UserAccTicketNo;
import com.chinasofti.huateng.account.model.card.FileNoticeReqDTO;
import com.chinasofti.huateng.account.model.card.LogicalCardRequestReqDTO;

public interface CardPoolService {
    /**
     * 查询并分配下一个可用卡号。
     */
    UserAccTicketNo allocateNextCard(String thirdUserId);

    /**
     * 监控卡号池剩余数量。
     */
    void monitorCardPool();

    /**
     * 请求补充逻辑卡号。
     */
    void requestLogicalCardNo(LogicalCardRequestReqDTO request);

    /**
     * 处理文件通知。
     */
    void handleFileNotice(FileNoticeReqDTO request);
}
