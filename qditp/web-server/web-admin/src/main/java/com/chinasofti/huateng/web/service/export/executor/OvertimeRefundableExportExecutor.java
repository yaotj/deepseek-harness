package com.chinasofti.huateng.web.service.export.executor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.pay.OvertimeRefundableExportDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 超时罚金可退款订单异步导出执行器。
 */
@Component("OVERTIME_REFUNDABLE")
public class OvertimeRefundableExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(OvertimeRefundableExportExecutor.class);

    private static final String[] HEADERS = {
            "订单号", "逻辑卡号", "出站车站", "超时罚金(元)", "实付(元)", "扣费状态", "出站时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            26 * 256, 20 * 256, 16 * 256, 16 * 256, 14 * 256, 14 * 256, 18 * 256
    };

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("OVERTIME_REFUNDABLE 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String stationCode = params.getStr("stationCode");
        String startDate = params.getStr("startDate");
        String endDate = params.getStr("endDate");

        List<OvertimeRefundableExportDTO> list = gateTxnPayClient.listOvertimeRefundable(stationCode, startDate, endDate);
        final List<OvertimeRefundableExportDTO> dataList = list == null ? List.of() : list;

        long rowCount = dataList.size();
        writeExcel(task, "超时罚金可退款订单", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (OvertimeRefundableExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getOrderNo());
                setCell(row, 1, dto.getCardId());
                setCell(row, 2, dto.getExitStationName() != null ? dto.getExitStationName() : dto.getOutStation());
                setCell(row, 3, fenToYuan(dto.getOvertimeAmount()));
                setCell(row, 4, fenToYuan(dto.getTotalAmount()));
                setCell(row, 5, dto.getDebitStatus());
                setCell(row, 6, dto.getOutTime());
            }
        });

        log.info("OVERTIME_REFUNDABLE 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private String fenToYuan(Integer fen)
    {
        if (fen == null)
        {
            return "";
        }
        return String.format("%.2f", fen / 100.0);
    }
}
