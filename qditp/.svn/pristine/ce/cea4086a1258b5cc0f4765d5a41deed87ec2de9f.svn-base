package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.mapper.ticket.ChipTypeMapper;
import com.chinasofti.huateng.para.mapper.ticket.TicketTypeMapper;
import com.chinasofti.huateng.para.mapper.ticket.TotalSalePartMapper;
import com.chinasofti.huateng.para.model.TicketParseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.function.ToIntFunction;

@Service
public class TicketImportService {

    private static final int BATCH_SIZE = 100;

    private final TicketParser parser;
    private final ChipTypeMapper chipTypeMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final TotalSalePartMapper totalSalePartMapper;

    public TicketImportService(TicketParser parser,
                               ChipTypeMapper chipTypeMapper,
                               TicketTypeMapper ticketTypeMapper,
                               TotalSalePartMapper totalSalePartMapper) {
        this.parser = parser;
        this.chipTypeMapper = chipTypeMapper;
        this.ticketTypeMapper = ticketTypeMapper;
        this.totalSalePartMapper = totalSalePartMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public TicketParseResult importLocalFile(String filePath) {
        TicketParseResult result;
        try {
            result = parser.parse(Path.of(filePath));
        } catch (Exception e) {
            throw new IllegalStateException("车票参数文件解析失败: " + filePath, e);
        }
        if (!Boolean.TRUE.equals(result.getMd5Valid())) {
            throw new IllegalArgumentException("车票参数文件 MD5 校验失败: " + filePath);
        }

        Long paraVerNo = Long.parseLong(result.getHeader().get("paraVerNo").toString());
        totalSalePartMapper.deleteByParaVerNo(paraVerNo);
        ticketTypeMapper.deleteByParaVerNo(paraVerNo);
        chipTypeMapper.deleteByParaVerNo(paraVerNo);
        batchInsert(result.getChipTypes(), chipTypeMapper::insertBatch);
        batchInsert(result.getTicketTypes(), ticketTypeMapper::insertBatch);
        batchInsert(result.getTotalSaleParts(), totalSalePartMapper::insertBatch);
        return result;
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
