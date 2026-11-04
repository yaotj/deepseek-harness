package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.entity.Graylist;
import com.chinasofti.huateng.blacklist.entity.GraylistOperateLog;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;

/** 灰名单业务服务（观察/预警名单：不进拦截、不渠道同步、无两阶段解除）。 */
public interface GraylistService {

    /** 分页查询当前在灰记录。 */
    ResultVO<PageInfo<Graylist>> page(String cardId, String thirdUserId,
                                      String createTimeBegin, String createTimeEnd,
                                      Integer pageNum, Integer pageSize);

    /**
     * 进灰（运维手工，发起方定死 09）：写 GRAYLIST + 操作流水 ADD。
     * 卡已在灰视为幂等成功（唯一约束兜底，不覆盖原记录）。
     */
    ResultVO<Void> addGray(String cardId, String thirdUserId, String cardType,
                           String bizNo, String reason, String operator);

    /** 移出灰名单：删 GRAYLIST 行 + 操作流水 REMOVE；不在灰则报错。 */
    ResultVO<Void> removeGray(String cardId, String reason, String operator);

    /** 操作流水分页（cardId / operateType 可选）。 */
    ResultVO<PageInfo<GraylistOperateLog>> operateLogPage(String cardId, String operateType,
                                                          Integer pageNum, Integer pageSize);
}
