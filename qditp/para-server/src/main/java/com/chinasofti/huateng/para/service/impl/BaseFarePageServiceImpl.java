package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.mapper.fare.BaseFarePageMapper;
import com.chinasofti.huateng.para.model.BaseFareLineOption;
import com.chinasofti.huateng.para.model.BaseFarePageView;
import com.chinasofti.huateng.para.model.BaseFareStationOption;
import com.chinasofti.huateng.para.service.BaseFarePageService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class BaseFarePageServiceImpl implements BaseFarePageService {
    private final BaseFarePageMapper baseFarePageMapper;

    public BaseFarePageServiceImpl(BaseFarePageMapper baseFarePageMapper) {
        this.baseFarePageMapper = baseFarePageMapper;
    }

    @Override
    public ResultVO<PageInfo<BaseFarePageView>> pageCurrentBaseFare(String entryStationCode, String exitStationCode,
                                                                     String entryLineCode, String exitLineCode,
                                                                     Integer pageNum, Integer pageSize) {
        PageInfo<BaseFarePageView> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> baseFarePageMapper.selectCurrentPage(trimToNull(entryStationCode), trimToNull(exitStationCode), trimToNull(entryLineCode), trimToNull(exitLineCode)));
        return ResultMapper.ok(page);
    }

    @Override
    public ResultVO<List<BaseFareStationOption>> listCurrentStations(String lineCode) {
        return ResultMapper.ok(baseFarePageMapper.selectCurrentStations(trimToNull(lineCode)));
    }

    @Override
    public ResultVO<List<BaseFareLineOption>> listCurrentLines() {
        return ResultMapper.ok(baseFarePageMapper.selectCurrentLines());
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
