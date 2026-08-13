package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.mapper.fare.BaseFarePageMapper;
import com.chinasofti.huateng.para.model.BaseFarePageView;
import com.chinasofti.huateng.para.model.BaseFareStationOption;
import com.chinasofti.huateng.para.service.BaseFarePageService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
/** 基础票价页面服务，只读取参数当前版本而不暴露历史版本。 */
public class BaseFarePageServiceImpl implements BaseFarePageService {
    private final BaseFarePageMapper baseFarePageMapper;

    public BaseFarePageServiceImpl(BaseFarePageMapper baseFarePageMapper) {
        this.baseFarePageMapper = baseFarePageMapper;
    }

    /** 查询费率矩阵与 FARE_TYPE=0 基础票价关联后的当前有效票价。 */
    @Override
    public ResultVO<PageInfo<BaseFarePageView>> pageCurrentBaseFare(String entryStationCode, String exitStationCode,
                                                                     Integer pageNum, Integer pageSize) {
        PageInfo<BaseFarePageView> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> baseFarePageMapper.selectCurrentPage(trimToNull(entryStationCode), trimToNull(exitStationCode)));
        return ResultMapper.ok(page);
    }

    /** 查询当前路网版本的车站选项。 */
    @Override
    public ResultVO<List<BaseFareStationOption>> listCurrentStations() {
        return ResultMapper.ok(baseFarePageMapper.selectCurrentStations());
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
