package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.mapper.network.LineInfoMapper;
import com.chinasofti.huateng.para.mapper.network.SectInfoMapper;
import com.chinasofti.huateng.para.mapper.network.StationInfoMapper;
import com.chinasofti.huateng.para.mapper.network.TsfInfoMapper;
import com.chinasofti.huateng.para.mapper.network.ZoneDtlMapper;
import com.chinasofti.huateng.para.mapper.network.ZoneInfoMapper;
import com.chinasofti.huateng.para.model.RowNetworkParseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.function.ToIntFunction;

@Service
public class RowNetworkImportService {

    private static final int BATCH_SIZE = 100;

    private final RowNetworkParser parser;
    private final LineInfoMapper lineInfoMapper;
    private final StationInfoMapper stationInfoMapper;
    private final TsfInfoMapper tsfInfoMapper;
    private final ZoneInfoMapper zoneInfoMapper;
    private final ZoneDtlMapper zoneDtlMapper;
    private final SectInfoMapper sectInfoMapper;

    public RowNetworkImportService(RowNetworkParser parser,
                                   LineInfoMapper lineInfoMapper,
                                   StationInfoMapper stationInfoMapper,
                                   TsfInfoMapper tsfInfoMapper,
                                   ZoneInfoMapper zoneInfoMapper,
                                   ZoneDtlMapper zoneDtlMapper,
                                   SectInfoMapper sectInfoMapper) {
        this.parser = parser;
        this.lineInfoMapper = lineInfoMapper;
        this.stationInfoMapper = stationInfoMapper;
        this.tsfInfoMapper = tsfInfoMapper;
        this.zoneInfoMapper = zoneInfoMapper;
        this.zoneDtlMapper = zoneDtlMapper;
        this.sectInfoMapper = sectInfoMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public RowNetworkParseResult importLocalFile(String filePath) {
        RowNetworkParseResult result;
        try {
            result = parser.parse(Path.of(filePath));
        } catch (Exception e) {
            throw new IllegalStateException("路网拓扑参数文件解析失败: " + filePath, e);
        }
        if (!Boolean.TRUE.equals(result.getMd5Valid())) {
            throw new IllegalArgumentException("路网拓扑参数文件 MD5 校验失败: " + filePath);
        }

        Long paraVerNo = Long.parseLong(result.getHeader().get("paraVerNo").toString());
        deleteByParaVerNo(paraVerNo);

        batchInsert(result.getLineInfos(), lineInfoMapper::insertBatch);
        batchInsert(result.getStationInfos(), stationInfoMapper::insertBatch);
        batchInsert(result.getTsfInfos(), tsfInfoMapper::insertBatch);
        batchInsert(result.getZoneInfos(), zoneInfoMapper::insertBatch);
        batchInsert(result.getZoneDtls(), zoneDtlMapper::insertBatch);
        batchInsert(result.getSectInfos(), sectInfoMapper::insertBatch);
        return result;
    }

    private void deleteByParaVerNo(Long paraVerNo) {
        zoneDtlMapper.deleteByParaVerNo(paraVerNo);
        sectInfoMapper.deleteByParaVerNo(paraVerNo);
        tsfInfoMapper.deleteByParaVerNo(paraVerNo);
        stationInfoMapper.deleteByParaVerNo(paraVerNo);
        zoneInfoMapper.deleteByParaVerNo(paraVerNo);
        lineInfoMapper.deleteByParaVerNo(paraVerNo);
    }

    private <T> void batchInsert(List<T> rows, ToIntFunction<List<T>> insertFunction) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, rows.size());
            insertFunction.applyAsInt(rows.subList(from, to));
        }
    }
}
