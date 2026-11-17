package com.chinasofti.huateng.web.service.export.executor;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.chinasofti.huateng.web.domain.export.AsyncExportTask;


public abstract class AbstractExcelExportExecutor implements IAsyncExportExecutor
{
    protected static final int STREAM_WINDOW = 1000;


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


    protected void createHeader(Sheet sheet, String[] headers)
    {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++)
        {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
    }


    protected void setColumnWidths(Sheet sheet, int[] widths)
    {
        for (int i = 0; i < widths.length; i++)
        {
            sheet.setColumnWidth(i, widths[i]);
        }
    }


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
