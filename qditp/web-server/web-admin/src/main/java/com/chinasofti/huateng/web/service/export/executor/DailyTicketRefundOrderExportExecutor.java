package com.chinasofti.huateng.web.service.export.executor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.dailyticket.DailyTicketRefundOrderExportDTO;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 日票 / 旅游票可退款订单异步导出执行器。
 */
@Component("DAILY_TICKET_REFUND_ORDER")
public class DailyTicketRefundOrderExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(DailyTicketRefundOrderExportExecutor.class);

    private static final String[] HEADERS = {
            "订单类型", "订单号", "支付平台订单号", "订单金额", "订单状态",
            "支付状态", "车票状态", "退款状态", "下单时间", "核验时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            12 * 256, 24 * 256, 24 * 256, 14 * 256, 12 * 256,
            12 * 256, 14 * 256, 14 * 256, 18 * 256, 18 * 256
    };

    @Autowired
    private DailyTicketClient dailyTicketClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("DAILY_TICKET_REFUND_ORDER 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String orderNo = params.getStr("orderNo");
        String paymentOrderNo = params.getStr("paymentOrderNo");
        Date beginTime = parseDateTime(params.getStr("beginTime"));
        Date endTime = parseDateTime(params.getStr("endTime"));

        List<DailyTicketRefundOrderExportDTO> list = dailyTicketClient.listDailyTicketRefundOrders(
                beginTime, endTime, orderNo, paymentOrderNo);
        final List<DailyTicketRefundOrderExportDTO> dataList = list == null ? List.of() : list;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long rowCount = dataList.size();
        writeExcel(task, "日票退款订单", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (DailyTicketRefundOrderExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, "2".equals(dto.getOrderType()) ? "旅游票" : "日票");
                setCell(row, 1, dto.getOrderNo());
                setCell(row, 2, dto.getPaymentOrderNo());
                setCell(row, 3, fenToYuan(dto.getTotalAmount()));
                setCell(row, 4, orderStatusLabel(dto.getOrderStatus()));
                setCell(row, 5, dto.getPayStatus());
                setCell(row, 6, ticketStatusLabel(dto.getTicketStatus()));
                setCell(row, 7, refundStatusLabel(dto.getRefundStatus()));
                setCell(row, 8, formatDate(sdf, dto.getCreateTime()));
                setCell(row, 9, formatDate(sdf, dto.getVerifyAfterTime()));
            }
        });

        log.info("DAILY_TICKET_REFUND_ORDER 异步导出完成, taskId={}, rowCount={}, fileSize={}",
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

    private String orderStatusLabel(String status)
    {
        return switch (status == null ? "" : status)
        {
            case "PAID" -> "已支付";
            case "UNPAID" -> "未支付";
            case "REFUNDED" -> "已退款";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String ticketStatusLabel(String status)
    {
        return switch (status == null ? "" : status)
        {
            case "UNUSED" -> "未使用";
            case "USED" -> "已使用";
            case "EXPIRED" -> "已过期";
            case "REFUNDED" -> "已退款";
            default -> status;
        };
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
