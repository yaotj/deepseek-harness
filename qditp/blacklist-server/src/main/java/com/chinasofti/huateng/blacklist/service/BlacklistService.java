package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;

/**
 * 黑名单业务服务。
 */
public interface BlacklistService {
    /** 分页查询运营端黑名单管理记录，status / channelSyncStatus 均为可选筛选。 */
    ResultVO<PageInfo<Blacklist>> page(String cardId, String thirdUserId, String status,
                                       String channelSyncStatus, String createTimeBegin,
                                       String createTimeEnd, Integer pageNum, Integer pageSize);

    /**
     * 查询卡号是否命中黑名单。
     *
     * @param request 查询黑名单请求参数
     * @return 查询黑名单结果
     */
    QueryBlackListResult queryBlackList(QueryBlackListReqDTO request);

    /**
     * 新增黑名单。
     *
     * @param request 新增黑名单请求参数
     * @return 黑名单操作结果
     */
    BlackListOperateResult addBlackList(AddBlackListReqDTO request);

    /**
     * 物理删除黑名单。
     *
     * @param request 删除黑名单请求参数
     * @return 黑名单操作结果
     */
    BlackListOperateResult deleteBlackList(DeleteBlackListReqDTO request);
}
