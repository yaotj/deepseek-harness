package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.network.LineInfo;
import com.chinasofti.huateng.para.entity.network.StationInfo;
import com.chinasofti.huateng.para.model.LineStationVersion;
import com.github.pagehelper.PageInfo;

public interface ParaPageService {
    ResultVO<PageInfo<LineInfo>> pageLineInfo(Integer pageNum, Integer pageSize,
                                               String lineCode, String lineNm);

    ResultVO<PageInfo<StationInfo>> pageStationInfo(Integer pageNum, Integer pageSize,
                                                     String ownerLineId, String stationCode,
                                                     String stationNm, String stationENm);

    ResultVO<PageInfo<LineStationVersion>> pageLineStationVersion(Integer pageNum, Integer pageSize,
                                                                   String paraTypeName, Long versionNo);
}
