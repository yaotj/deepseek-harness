package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ParaVersion;
import com.chinasofti.huateng.para.entity.network.LineInfo;
import com.chinasofti.huateng.para.entity.network.StationInfo;
import com.chinasofti.huateng.para.mapper.ParaVersionMapper;
import com.chinasofti.huateng.para.mapper.network.LineInfoMapper;
import com.chinasofti.huateng.para.mapper.network.StationInfoMapper;
import com.chinasofti.huateng.para.model.LineStationVersion;
import com.chinasofti.huateng.para.service.ParaPageService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.Collections;

/** 参数管理页面查询服务实现。 */
@Service
public class ParaPageServiceImpl implements ParaPageService {
    private static final String NETWORK_PARA_TYPE = "0001";
    private static final String RATE_PARA_TYPE = "0004";

    private final ParaVersionMapper paraVersionMapper;
    private final LineInfoMapper lineInfoMapper;
    private final StationInfoMapper stationInfoMapper;

    public ParaPageServiceImpl(ParaVersionMapper paraVersionMapper, LineInfoMapper lineInfoMapper,
                               StationInfoMapper stationInfoMapper) {
        this.paraVersionMapper = paraVersionMapper;
        this.lineInfoMapper = lineInfoMapper;
        this.stationInfoMapper = stationInfoMapper;
    }

    @Override
    public ResultVO<PageInfo<LineInfo>> pageLineInfo(Integer pageNum, Integer pageSize) {
        ParaVersion currentVersion = currentNetworkVersion();
        if (currentVersion == null) {
            return ResultMapper.ok(new PageInfo<>());
        }
        PageInfo<LineInfo> pageInfo = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> lineInfoMapper.selectByParaVerNo(currentVersion.getCurrentVerNo()));
        return ResultMapper.ok(pageInfo);
    }

    @Override
    public ResultVO<PageInfo<StationInfo>> pageStationInfo(Integer pageNum, Integer pageSize) {
        ParaVersion currentVersion = currentNetworkVersion();
        if (currentVersion == null) {
            return ResultMapper.ok(new PageInfo<>());
        }
        PageInfo<StationInfo> pageInfo = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> stationInfoMapper.selectByParaVerNo(currentVersion.getCurrentVerNo()));
        return ResultMapper.ok(pageInfo);
    }

    @Override
    public ResultVO<PageInfo<LineStationVersion>> pageLineStationVersion(Integer pageNum, Integer pageSize) {
        int safePageNum = safePageNum(pageNum);
        ParaVersion currentVersion = currentNetworkVersion();
        PageInfo<LineStationVersion> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(safePageNum);
        pageInfo.setPageSize(safePageSize(pageSize));

        if (currentVersion == null || safePageNum > 1) {
            pageInfo.setList(Collections.emptyList());
            pageInfo.setTotal(currentVersion == null ? 0 : 1);
            return ResultMapper.ok(pageInfo);
        }

        LineStationVersion version = new LineStationVersion();
        version.setLineCodeVersion(currentVersion.getCurrentVerNo());
        version.setStationCodeVersion(currentVersion.getCurrentVerNo());
        version.setNetworkFileName(currentVersion.getCurrentFileName());
        ParaVersion rateVersion = paraVersionMapper.selectByParaType(RATE_PARA_TYPE);
        if (rateVersion != null) {
            version.setRateFileName(rateVersion.getCurrentFileName());
        }
        version.setUpdateTime(currentVersion.getLastUpdTms());
        version.setEffectiveTime(currentVersion.getValidDateTime());
        pageInfo.setList(Collections.singletonList(version));
        pageInfo.setTotal(1);
        return ResultMapper.ok(pageInfo);
    }

    private ParaVersion currentNetworkVersion() {
        ParaVersion currentVersion = paraVersionMapper.selectByParaType(NETWORK_PARA_TYPE);
        return currentVersion != null && currentVersion.getCurrentVerNo() != null ? currentVersion : null;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
