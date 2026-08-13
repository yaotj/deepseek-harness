package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.model.BaseFarePageView;
import com.chinasofti.huateng.para.model.BaseFareStationOption;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface BaseFarePageService {
    ResultVO<PageInfo<BaseFarePageView>> pageCurrentBaseFare(String entryStationCode, String exitStationCode, Integer pageNum, Integer pageSize);

    ResultVO<List<BaseFareStationOption>> listCurrentStations();
}
