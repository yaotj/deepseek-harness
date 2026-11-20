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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    public ResultVO<PageInfo<LineInfo>> pageLineInfo(Integer pageNum, Integer pageSize,
                                                      String lineCode, String lineNm) {
        ParaVersion currentVersion = currentNetworkVersion();
        if (currentVersion == null) {
            return ResultMapper.ok(new PageInfo<>());
        }
        PageInfo<LineInfo> pageInfo = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> lineInfoMapper.selectByParaVerNoAndCondition(
                        currentVersion.getCurrentVerNo(), lineCode, lineNm));
        return ResultMapper.ok(pageInfo);
    }

    @Override
    public ResultVO<PageInfo<StationInfo>> pageStationInfo(Integer pageNum, Integer pageSize,
                                                            String ownerLineId, String stationCode,
                                                            String stationNm, String stationENm) {
        ParaVersion currentVersion = currentNetworkVersion();
        if (currentVersion == null) {
            return ResultMapper.ok(new PageInfo<>());
        }
        PageInfo<StationInfo> pageInfo = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> stationInfoMapper.selectByParaVerNoAndCondition(
                        currentVersion.getCurrentVerNo(), ownerLineId, stationCode, stationNm, stationENm));
        return ResultMapper.ok(pageInfo);
    }

    @Override
    public ResultVO<PageInfo<LineStationVersion>> pageLineStationVersion(Integer pageNum, Integer pageSize,
                                                                          String paraTypeName, Long versionNo) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        ParaVersion networkVersion = paraVersionMapper.selectByParaType(NETWORK_PARA_TYPE);
        ParaVersion rateVersion = paraVersionMapper.selectByParaType(RATE_PARA_TYPE);
        LineStationVersion networkRow = buildVersionRow("路网拓扑", networkVersion);
        LineStationVersion rateRow = buildVersionRow("费率", rateVersion);
        List<LineStationVersion> versions = new ArrayList<>();
        if (networkRow != null) versions.add(networkRow);
        if (rateRow != null) versions.add(rateRow);
        // 应用筛选
        if (paraTypeName != null && !paraTypeName.isBlank()) {
            versions.removeIf(v -> !paraTypeName.equals(v.getParaTypeName()));
        }
        if (versionNo != null) {
            versions.removeIf(v -> v.getVersionNo() == null || !v.getVersionNo().equals(versionNo));
        }
        List<LineStationVersion> pageRows = safePageNum > 1 ? Collections.emptyList() : versions;
        int total = versions.size();
        PageInfo<LineStationVersion> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(safePageNum);
        pageInfo.setPageSize(safePageSize);
        pageInfo.setList(pageRows);
        pageInfo.setTotal(total);
        pageInfo.setSize(pageRows.size());
        pageInfo.setPages(total == 0 ? 0 : (total + safePageSize - 1) / safePageSize);
        pageInfo.setStartRow(pageRows.isEmpty() ? 0 : (safePageNum - 1) * safePageSize + 1);
        pageInfo.setEndRow(pageRows.isEmpty() ? 0 : (safePageNum - 1) * safePageSize + pageRows.size());
        return ResultMapper.ok(pageInfo);
    }

    private LineStationVersion buildVersionRow(String paraTypeName, ParaVersion paraVersion) {
        if (paraVersion == null || paraVersion.getCurrentVerNo() == null) {
            return null;
        }
        LineStationVersion version = new LineStationVersion();
        version.setParaTypeName(paraTypeName);
        version.setVersionNo(paraVersion.getCurrentVerNo());
        version.setFileName(paraVersion.getCurrentFileName());
        version.setUpdateTime(paraVersion.getLastUpdTms());
        version.setEffectiveTime(paraVersion.getValidDateTime());
        return version;
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
