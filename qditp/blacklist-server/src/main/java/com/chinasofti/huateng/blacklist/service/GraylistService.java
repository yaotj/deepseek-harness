package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.entity.Graylist;
import com.chinasofti.huateng.blacklist.entity.GraylistOperateLog;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;

public interface GraylistService {

    ResultVO<PageInfo<Graylist>> page(String cardId, String thirdUserId,
                                      String createTimeBegin, String createTimeEnd,
                                      Integer pageNum, Integer pageSize);


    ResultVO<Void> addGray(String cardId, String thirdUserId, String cardType,
                           String bizNo, String reason, String operator);

    ResultVO<Void> removeGray(String cardId, String reason, String operator);

    ResultVO<PageInfo<GraylistOperateLog>> operateLogPage(String cardId, String operateType,
                                                          Integer pageNum, Integer pageSize);
}
