package com.chinasofti.huateng.web.service.export.executor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.pay.OfflineCodeStatExportDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 离线码交易统计异步导出执行器。
 */
@Component("OFFLINE_CODE_STAT")
public class OfflineCodeStatExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(OfflineCodeStatExportExecutor.class);

    private static final String[] HEADERS = {
            "车站编码", "车站名称", "交易笔数", "独立卡数", "占比"
    };
    private static final int[] COLUMN_WIDTHS = {
            14 * 256, 20 * 256, 14 * 256, 14 * 256, 12 * 256
    };

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("OFFLINE_CODE_STAT 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String startDate = params.getStr("startDate");
        String endDate = params.getStr("endDate");

        List<OfflineCodeStatExportDTO> list = gateTxnPayClient.listOfflineCodeStats(startDate, endDate);
        final List<OfflineCodeStatExportDTO> dataList = list == null ? List.of() : list;

        long total = dataList.stream().mapToLong(d -> d.getTxnCount() == null ? 0L : d.getTxnCount()).sum();
        long rowCount = dataList.size();
        writeExcel(task, "离线码交易统计", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (OfflineCodeStatExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getStationCode());
                setCell(row, 1, dto.getStationName());
                setCell(row, 2, numberValue(dto.getTxnCount()));
                setCell(row, 3, numberValue(dto.getCardCount()));
                setCell(row, 4, ratio(dto.getTxnCount(), total));
            }
        });

        log.info("OFFLINE_CODE_STAT 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private String numberValue(Long value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private String ratio(Long count, long total)
    {
        if (total == 0)
        {
            return "0.00%";
        }
        return String.format("%.2f%%", (count == null ? 0L : count) * 100.0 / total);
    }
}
