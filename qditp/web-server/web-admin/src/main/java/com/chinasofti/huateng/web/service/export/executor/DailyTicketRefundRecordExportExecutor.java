package com.chinasofti.huateng.web.service.export.executor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.dailyticket.DailyTicketRefundRecordExportDTO;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 日票退款记录异步导出执行器。
 */
@Component("DAILY_TICKET_REFUND_RECORD")
public class DailyTicketRefundRecordExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(DailyTicketRefundRecordExportExecutor.class);

    private static final String[] HEADERS = {
            "商户退款单号", "平台退款单号", "日票订单号", "支付平台订单号", "退款金额",
            "退款类型", "退款状态", "申请时间", "核验时间", "完成时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            26 * 256, 26 * 256, 24 * 256, 24 * 256, 14 * 256,
            14 * 256, 14 * 256, 18 * 256, 18 * 256, 18 * 256
    };

    @Autowired
    private DailyTicketClient dailyTicketClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("DAILY_TICKET_REFUND_RECORD 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String orderNo = params.getStr("orderNo");
        String paymentOrderNo = params.getStr("paymentOrderNo");
        String refundOrderNo = params.getStr("refundOrderNo");
        String refundStatus = params.getStr("refundStatus");
        Date beginTime = parseDateTime(params.getStr("beginTime"));
        Date endTime = parseDateTime(params.getStr("endTime"));

        List<DailyTicketRefundRecordExportDTO> list = dailyTicketClient.listDailyTicketRefundRecords(
                beginTime, endTime, orderNo, paymentOrderNo, refundOrderNo, refundStatus);
        final List<DailyTicketRefundRecordExportDTO> dataList = list == null ? List.of() : list;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long rowCount = dataList.size();
        writeExcel(task, "日票退款记录", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (DailyTicketRefundRecordExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getRefundOrderNo());
                setCell(row, 1, dto.getPlatformRefundNo());
                setCell(row, 2, dto.getOrderNo());
                setCell(row, 3, dto.getPaymentOrderNo());
                setCell(row, 4, fenToYuan(dto.getRefundAmount()));
                setCell(row, 5, "01".equals(dto.getRefundType()) ? "核验退款" : "直接退款");
                setCell(row, 6, refundStatusLabel(dto.getRefundStatus()));
                setCell(row, 7, formatDate(sdf, dto.getCreateTime()));
                setCell(row, 8, formatDate(sdf, dto.getVerifyAfterTime()));
                setCell(row, 9, formatDate(sdf, dto.getRefundDate()));
            }
        });

        log.info("DAILY_TICKET_REFUND_RECORD 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private Date parseDateTime(String value)
    {
        if (value == null || value.isEmpty())
        {
            return null;
        }
        try
        {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String formatDate(SimpleDateFormat sdf, Date value)
    {
        return value == null ? "" : sdf.format(value);
    }

    private String fenToYuan(Integer fen)
    {
        if (fen == null)
        {
            return "";
        }
        return String.format("%.2f", fen / 100.0);
    }

    private String refundStatusLabel(String status)
    {
        return switch (status == null ? "" : status)
        {
            case "REFUNDING" -> "退款处理中";
            case "REFUNDED" -> "退款完成";
            case "FAILED" -> "退款失败";
            case "WAIT_VERIFY" -> "待核验";
            default -> status == null ? "-" : status;
        };
    }
}
