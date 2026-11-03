package com.chinasofti.huateng.web.service.export.executor;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.cardpool.CardPoolBatchExportDTO;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 卡池批次异步导出执行器。
 */
@Component("CARD_POOL_BATCH")
public class CardPoolBatchExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(CardPoolBatchExportExecutor.class);

    private static final String[] HEADERS = {
            "批次号", "票种", "ACC 子类型", "来源", "文件名", "申请数",
            "有效数", "重复数", "非法数", "状态", "失败原因", "创建时间", "完成时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            14 * 256, 16 * 256, 14 * 256, 12 * 256, 32 * 256, 12 * 256,
            12 * 256, 12 * 256, 12 * 256, 14 * 256, 32 * 256, 18 * 256, 18 * 256
    };

    @Autowired
    private CardPoolClient cardPoolClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("CARD_POOL_BATCH 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        Long batchNo = params.getLong("batchNo");
        String cardType = params.getStr("cardType");
        String accTicketType = params.getStr("accTicketType");
        String source = params.getStr("source");
        String status = params.getStr("status");
        LocalDateTime createTimeBegin = parseDateTime(params.getStr("createTimeBegin"));
        LocalDateTime createTimeEnd = parseDateTime(params.getStr("createTimeEnd"));

        List<CardPoolBatchExportDTO> list = cardPoolClient.listCardPoolBatches(
                batchNo, cardType, accTicketType, source, status, createTimeBegin, createTimeEnd);
        final List<CardPoolBatchExportDTO> dataList = list == null ? List.of() : list;

        long rowCount = dataList.size();
        writeExcel(task, "卡池批次", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (CardPoolBatchExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getBatchNo() == null ? "" : String.valueOf(dto.getBatchNo()));
                setCell(row, 1, ticketTypeLabel(dto.getCardType()));
                setCell(row, 2, dto.getAccTicketType());
                setCell(row, 3, dto.getSource());
                setCell(row, 4, dto.getFileName());
                setCell(row, 5, numberValue(dto.getRequestNum()));
                setCell(row, 6, numberValue(dto.getValidCount()));
                setCell(row, 7, numberValue(dto.getDuplicateCount()));
                setCell(row, 8, numberValue(dto.getInvalidCount()));
                setCell(row, 9, dto.getStatus());
                setCell(row, 10, dto.getErrorMsg());
                setCell(row, 11, formatDateTime(dto.getCreateTime()));
                setCell(row, 12, formatDateTime(dto.getFinishTime()));
            }
        });

        log.info("CARD_POOL_BATCH 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private LocalDateTime parseDateTime(String value)
    {
        if (value == null || value.isEmpty())
        {
            return null;
        }
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    private String formatDateTime(LocalDateTime value)
    {
        return value == null ? "" : value.toString().replace('T', ' ');
    }

    private String numberValue(Integer value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private String ticketTypeLabel(String cardType)
    {
        if (cardType == null)
        {
            return "";
        }
        return switch (cardType)
        {
            case "0441" -> "后付费二维码";
            case "0442" -> "HCE";
            case "0443" -> "新HCE";
            case "0444" -> "员工码";
            case "0445" -> "一日票";
            case "0446" -> "三日票";
            case "0447" -> "七日票";
            case "0448" -> "月票";
            case "044A" -> "爱山东";
            default -> cardType;
        };
    }
}
