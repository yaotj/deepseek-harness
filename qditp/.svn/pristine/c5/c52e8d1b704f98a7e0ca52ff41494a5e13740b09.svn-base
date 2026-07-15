package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.mapper.calendar.FareTimeMapper;
import com.chinasofti.huateng.para.mapper.calendar.SpecialDateMapper;
import com.chinasofti.huateng.para.mapper.calendar.TimeIntervalMapper;
import com.chinasofti.huateng.para.model.CalendarParseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.function.ToIntFunction;

@Service
public class CalendarImportService {

    private static final int BATCH_SIZE = 100;

    private final CalendarParser parser;
    private final SpecialDateMapper specialDateMapper;
    private final TimeIntervalMapper timeIntervalMapper;
    private final FareTimeMapper fareTimeMapper;

    public CalendarImportService(CalendarParser parser,
                                 SpecialDateMapper specialDateMapper,
                                 TimeIntervalMapper timeIntervalMapper,
                                 FareTimeMapper fareTimeMapper) {
        this.parser = parser;
        this.specialDateMapper = specialDateMapper;
        this.timeIntervalMapper = timeIntervalMapper;
        this.fareTimeMapper = fareTimeMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public CalendarParseResult importLocalFile(String filePath) {
        CalendarParseResult result;
        try {
            result = parser.parse(Path.of(filePath));
        } catch (Exception e) {
            throw new IllegalStateException("日历参数文件解析失败: " + filePath, e);
        }
        if (!Boolean.TRUE.equals(result.getMd5Valid())) {
            throw new IllegalArgumentException("日历参数文件 MD5 校验失败: " + filePath);
        }

        Long paraVerNo = Long.parseLong(result.getHeader().get("paraVerNo").toString());
        deleteByParaVerNo(paraVerNo);
        batchInsert(result.getSpecialDates(), specialDateMapper::insertBatch);
        batchInsert(result.getTimeIntervals(), timeIntervalMapper::insertBatch);
        batchInsert(result.getFareTimes(), fareTimeMapper::insertBatch);
        return result;
    }

    private void deleteByParaVerNo(Long paraVerNo) {
        fareTimeMapper.deleteByParaVerNo(paraVerNo);
        timeIntervalMapper.deleteByParaVerNo(paraVerNo);
        specialDateMapper.deleteByParaVerNo(paraVerNo);
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
