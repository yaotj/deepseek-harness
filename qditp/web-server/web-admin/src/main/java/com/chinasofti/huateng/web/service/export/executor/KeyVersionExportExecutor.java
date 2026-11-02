package com.chinasofti.huateng.web.service.export.executor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.key.KeyVersionExportDTO;
import com.chinasofti.huateng.rpc.key.KeyClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

/**
 * KEY_VERSION 异步导出执行器。
 * Bean 名称为 KEY_VERSION，与 ASYNC_EXPORT_TASK.task_code 对应。
 */
@Component("KEY_VERSION")
public class KeyVersionExportExecutor implements IAsyncExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(KeyVersionExportExecutor.class);

    private static final String[] HEADERS = { "密钥域编码", "接入方编码", "当前版本", "状态", "生效日期", "更新时间" };
    private static final String[] FIELDS = { "keyDomain", "providerId", "currentVersion", "statusDesc", "effectiveDate", "updateTime" };
    private static final int STREAM_WINDOW = 1000;

    @Autowired
    private KeyClient keyClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        if (filePath == null || filePath.isEmpty())
        {
            throw new IllegalArgumentException("文件路径为空");
        }

        File dir = new File(filePath).getParentFile();
        if (dir != null && !dir.exists())
        {
            dir.mkdirs();
        }

        log.info("KEY_VERSION 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);
        List<KeyVersionExportDTO> list = keyClient.listKeyVersions();
        if (list == null)
        {
            list = List.of();
        }

        long rowCount = list.size();
        long fileSize;

        try (Workbook wb = new SXSSFWorkbook(STREAM_WINDOW);
             FileOutputStream out = new FileOutputStream(filePath))
        {
            Sheet sheet = wb.createSheet("密钥版本");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++)
            {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
            }

            int rowIdx = 1;
            for (KeyVersionExportDTO dto : list)
            {
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getKeyDomain());
                setCell(row, 1, dto.getProviderId());
                setCell(row, 2, dto.getCurrentVersion());
                setCell(row, 3, dto.getStatusDesc());
                setCell(row, 4, dto.getEffectiveDate());
                setCell(row, 5, dto.getUpdateTime());
            }

            for (int i = 0; i < HEADERS.length; i++)
            {
                sheet.autoSizeColumn(i);
            }

            wb.write(out);
            fileSize = new File(filePath).length();
        }
        finally
        {
            System.gc();
        }

        task.setRowCount(rowCount);
        task.setFileSize(fileSize);
        log.info("KEY_VERSION 异步导出完成, taskId={}, rowCount={}, fileSize={}", task.getId(), rowCount, fileSize);
    }

    private void setCell(Row row, int col, String value)
    {
        Cell cell = row.createCell(col);
        if (value == null)
        {
            cell.setBlank();
        }
        else
        {
            cell.setCellValue(value);
        }
    }
}
