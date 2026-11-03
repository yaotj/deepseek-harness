package com.chinasofti.huateng.web.service.export.executor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.app.RegStatExportDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 注册量统计异步导出执行器。
 */
@Component("REG_STAT")
public class RegStatExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(RegStatExportExecutor.class);

    private static final String[] HEADERS = {
            "票种编码", "票种名称", "注册量", "占比"
    };
    private static final int[] COLUMN_WIDTHS = {
            14 * 256, 24 * 256, 14 * 256, 14 * 256
    };

    @Autowired
    private AccountClient accountClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("REG_STAT 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String startDate = params.getStr("startDate");
        String endDate = params.getStr("endDate");

        List<RegStatExportDTO> list = accountClient.listRegStats(startDate, endDate);
        final List<RegStatExportDTO> dataList = list == null ? List.of() : list;

        long total = dataList.stream().mapToLong(d -> d.getRegCount() == null ? 0L : d.getRegCount()).sum();
        long rowCount = dataList.size();
        writeExcel(task, "注册量统计", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (RegStatExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getCardType());
                setCell(row, 1, dto.getCardTypeName());
                setCell(row, 2, numberValue(dto.getRegCount()));
                setCell(row, 3, ratio(dto.getRegCount(), total));
            }
        });

        log.info("REG_STAT 异步导出完成, taskId={}, rowCount={}, fileSize={}",
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
