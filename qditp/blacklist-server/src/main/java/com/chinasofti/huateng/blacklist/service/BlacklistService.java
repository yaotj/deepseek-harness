package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.BlacklistOperateLog;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;

import java.util.List;


public interface BlacklistService {
    ResultVO<PageInfo<Blacklist>> page(String cardId, String thirdUserId, String status,
                                       String channelSyncStatus, String createTimeBegin,
                                       String createTimeEnd, Integer pageNum, Integer pageSize);

    List<BlacklistOperateLog> getOperateLogs(String cardId);

    QueryBlackListResult queryBlackList(QueryBlackListReqDTO request);

    BlackListOperateResult addBlackList(AddBlackListReqDTO request);

    BlackListOperateResult deleteBlackList(DeleteBlackListReqDTO request);
}
