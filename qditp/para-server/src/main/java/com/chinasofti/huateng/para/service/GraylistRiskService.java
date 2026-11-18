package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.GraylistRisk;
import com.github.pagehelper.PageInfo;

public interface GraylistRiskService {
    
    ResultVO<PageInfo<GraylistRisk>> page(String cardId, String ruleName, 
                                           String updateTimeBegin, String updateTimeEnd,
                                           Integer pageNum, Integer pageSize);
}
