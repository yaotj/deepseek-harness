package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.GraylistRisk;
import com.chinasofti.huateng.para.mapper.risk.GraylistRiskMapper;
import com.chinasofti.huateng.para.service.GraylistRiskService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraylistRiskServiceImpl implements GraylistRiskService {
    
    private final GraylistRiskMapper graylistRiskMapper;
    
    public GraylistRiskServiceImpl(GraylistRiskMapper graylistRiskMapper) {
        this.graylistRiskMapper = graylistRiskMapper;
    }
    
    @Override
    public ResultVO<PageInfo<GraylistRisk>> page(String cardId, String ruleName, 
                                                  String updateTimeBegin, String updateTimeEnd,
                                                  Integer pageNum, Integer pageSize) {
        // Build dynamic query conditions
        // For now, return all with pagination
        PageInfo<GraylistRisk> page = PageHelper.startPage(pageNum, pageSize)
                .doSelectPageInfo(() -> graylistRiskMapper.selectAll());
        return ResultMapper.ok(page);
    }
}
