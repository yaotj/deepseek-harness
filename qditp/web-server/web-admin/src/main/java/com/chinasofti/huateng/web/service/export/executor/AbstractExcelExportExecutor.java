package com.chinasofti.huateng.web.service.export.executor;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

/**
 * 异步 Excel 导出执行器公共骨架。
 */
public abstract class AbstractExcelExportExecutor implements IAsyncExportExecutor
{
    protected static final int STREAM_WINDOW = 1000;

    /**
     * 准备导出目录。
     */
    protected File prepareDirectory(AsyncExportTask task)
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
        return dir;
    }

    /**
     * 创建单元格并写入字符串值（null 时置空）。
     */
    protected void setCell(Row row, int col, String value)
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

    /**
     * 创建一行表头。
     */
    protected void createHeader(Sheet sheet, String[] headers)
    {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++)
        {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
    }

    /**
     * 统一设置列宽。
     */
    protected void setColumnWidths(Sheet sheet, int[] widths)
    {
        for (int i = 0; i < widths.length; i++)
        {
            sheet.setColumnWidth(i, widths[i]);
        }
    }

    /**
     * 使用 SXSSF 写 Excel 并更新 task 的 rowCount / fileSize。
     *
     * @param sheetName 工作表名
     * @param headers 表头
     * @param widths 列宽（单位：1/256 字符）
     * @param rowCount 数据行数
     * @param writer 按行写数据的回调，接收起始行号（从 1 开始）
     * @return 文件大小
     */
    protected long writeExcel(AsyncExportTask task, String sheetName, String[] headers,
            int[] widths, long rowCount, ExcelRowWriter writer) throws Exception
    {
        prepareDirectory(task);
        String filePath = task.getFilePath();
        long fileSize;
        try (Workbook wb = new SXSSFWorkbook(STREAM_WINDOW);
             FileOutputStream out = new FileOutputStream(filePath))
        {
            Sheet sheet = wb.createSheet(sheetName);
            createHeader(sheet, headers);
            writer.write(sheet, 1);
            setColumnWidths(sheet, widths);
            wb.write(out);
            fileSize = new File(filePath).length();
        }
        finally
        {
            System.gc();
        }
        task.setRowCount(rowCount);
        task.setFileSize(fileSize);
        return fileSize;
    }

    @FunctionalInterface
    protected interface ExcelRowWriter
    {
        void write(Sheet sheet, int startRow) throws Exception;
    }
}
