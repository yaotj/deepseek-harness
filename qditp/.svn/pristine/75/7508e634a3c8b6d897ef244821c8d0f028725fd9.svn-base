package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.mapper.fare.AddParaMapper;
import com.chinasofti.huateng.para.mapper.fare.BaseFareMapper;
import com.chinasofti.huateng.para.mapper.fare.FareGroupMapper;
import com.chinasofti.huateng.para.mapper.fare.FareMatrixMapper;
import com.chinasofti.huateng.para.mapper.fare.TicketFareMapper;
import com.chinasofti.huateng.para.model.RateParseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.function.ToIntFunction;

@Service
public class RateImportService {

    private static final int BATCH_SIZE = 100;

    private final RateParser parser;
    private final AddParaMapper addParaMapper;
    private final FareMatrixMapper fareMatrixMapper;
    private final TicketFareMapper ticketFareMapper;
    private final FareGroupMapper fareGroupMapper;
    private final BaseFareMapper baseFareMapper;

    public RateImportService(RateParser parser,
                             AddParaMapper addParaMapper,
                             FareMatrixMapper fareMatrixMapper,
                             TicketFareMapper ticketFareMapper,
                             FareGroupMapper fareGroupMapper,
                             BaseFareMapper baseFareMapper) {
        this.parser = parser;
        this.addParaMapper = addParaMapper;
        this.fareMatrixMapper = fareMatrixMapper;
        this.ticketFareMapper = ticketFareMapper;
        this.fareGroupMapper = fareGroupMapper;
        this.baseFareMapper = baseFareMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public RateParseResult importLocalFile(String filePath) {
        RateParseResult result;
        try {
            result = parser.parse(Path.of(filePath));
        } catch (Exception e) {
            throw new IllegalStateException("费率参数文件解析失败: " + filePath, e);
        }
        if (!Boolean.TRUE.equals(result.getMd5Valid())) {
            throw new IllegalArgumentException("费率参数文件 MD5 校验失败: " + filePath);
        }

        Long paraVerNo = Long.parseLong(result.getHeader().get("paraVerNo").toString());
        deleteByParaVerNo(paraVerNo);
        if (result.getAddPara() != null) {
            addParaMapper.insert(result.getAddPara());
        }
        batchInsert(result.getFareMatrices(), fareMatrixMapper::insertBatch);
        batchInsert(result.getTicketFares(), ticketFareMapper::insertBatch);
        batchInsert(result.getFareGroups(), fareGroupMapper::insertBatch);
        batchInsert(result.getBaseFares(), baseFareMapper::insertBatch);
        return result;
    }

    private void deleteByParaVerNo(Long paraVerNo) {
        baseFareMapper.deleteByParaVerNo(paraVerNo);
        fareGroupMapper.deleteByParaVerNo(paraVerNo);
        ticketFareMapper.deleteByParaVerNo(paraVerNo);
        fareMatrixMapper.deleteByParaVerNo(paraVerNo);
        addParaMapper.deleteByParaVerNo(paraVerNo);
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
