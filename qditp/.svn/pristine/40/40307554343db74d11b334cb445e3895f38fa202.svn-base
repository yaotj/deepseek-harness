package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.network.LineInfo;
import com.chinasofti.huateng.para.entity.network.StationInfo;
import com.chinasofti.huateng.para.model.LineStationVersion;
import com.github.pagehelper.PageInfo;

/** 参数管理页面查询服务。 */
public interface ParaPageService {
    ResultVO<PageInfo<LineInfo>> pageLineInfo(Integer pageNum, Integer pageSize);

    ResultVO<PageInfo<StationInfo>> pageStationInfo(Integer pageNum, Integer pageSize);

    ResultVO<PageInfo<LineStationVersion>> pageLineStationVersion(Integer pageNum, Integer pageSize);
}
