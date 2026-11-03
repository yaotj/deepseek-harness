package com.chinasofti.huateng.web.service.export.executor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.pay.FacePayOrderExportDTO;
import com.chinasofti.huateng.rpc.facepay.FacePayClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 当面付订单异步导出执行器。
 */
@Component("FACE_PAY_ORDER")
public class FacePayOrderExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(FacePayOrderExportExecutor.class);

    private static final String[] HEADERS = {
            "业务订单号", "订单状态", "受理渠道", "业务类型", "支付渠道编码",
            "支付中心订单号", "渠道订单号", "起点站", "终点站", "票价", "数量",
            "订单总额", "购票类型", "设备编号", "退款状态", "已退金额", "最后退款时间", "下单时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            26 * 256, 12 * 256, 12 * 256, 12 * 256, 14 * 256,
            26 * 256, 26 * 256, 12 * 256, 12 * 256, 12 * 256, 10 * 256,
            14 * 256, 14 * 256, 18 * 256, 12 * 256, 14 * 256, 18 * 256, 18 * 256
    };

    @Autowired
    private FacePayClient facePayClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("FACE_PAY_ORDER 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String orderNo = params.getStr("orderNo");
        String payCenterOrderNo = params.getStr("payCenterOrderNo");
        String payCenterChannelOrderNo = params.getStr("payCenterChannelOrderNo");
        String deviceId = params.getStr("deviceId");
        String channel = params.getStr("channel");
        String bizType = params.getStr("bizType");
        String orderStatus = params.getStr("orderStatus");
        String beginTime = params.getStr("beginTime");
        String endTime = params.getStr("endTime");

        List<FacePayOrderExportDTO> list = facePayClient.listFacePayOrders(
                orderNo, payCenterOrderNo, payCenterChannelOrderNo, deviceId,
                channel, bizType, orderStatus, beginTime, endTime);
        final List<FacePayOrderExportDTO> dataList = list == null ? List.of() : list;

        long rowCount = dataList.size();
        writeExcel(task, "当面付订单", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (FacePayOrderExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getOrderNo());
                setCell(row, 1, orderStatusLabel(dto.getOrderStatus()));
                setCell(row, 2, f2fChannelLabel(dto.getChannel()));
                setCell(row, 3, bizTypeLabel(dto.getBizType()));
                setCell(row, 4, dto.getPayChannelCode());
                setCell(row, 5, dto.getPayCenterOrderNo());
                setCell(row, 6, dto.getPayCenterChannelOrderNo());
                setCell(row, 7, dto.getInStationName());
                setCell(row, 8, dto.getOutStationName());
                setCell(row, 9, amountValue(dto.getTicketPrice()));
                setCell(row, 10, numberValue(dto.getTicketNum()));
                setCell(row, 11, amountValue(dto.getOrderAmount()));
                setCell(row, 12, ticketTypeLabel(dto.getSingleTicketType()));
                setCell(row, 13, dto.getDeviceId());
                setCell(row, 14, refundStatusLabel(dto.getRefundStatus()));
                setCell(row, 15, amountValue(dto.getRefundAmount()));
                setCell(row, 16, dto.getLastRefundTime());
                setCell(row, 17, dto.getCreateTime());
            }
        });

        log.info("FACE_PAY_ORDER 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private String amountValue(Long value)
    {
        return value == null ? "" : String.format("%.2f", value / 100.0);
    }

    private String numberValue(Integer value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private String f2fChannelLabel(String channel)
    {
        return switch (channel == null ? "" : channel)
        {
            case "01" -> "APP";
            case "02" -> "TVM";
            case "03" -> "BOM";
            default -> channel;
        };
    }

    private String bizTypeLabel(String bizType)
    {
        return switch (bizType == null ? "" : bizType)
        {
            case "01" -> "购票";
            case "02" -> "充值";
            case "03" -> "取票";
            case "04" -> "非现金收款";
            default -> bizType;
        };
    }

    private String orderStatusLabel(String status)
    {
        return switch (status == null ? "" : status)
        {
            case "CREATED" -> "已下单";
            case "PAYING" -> "支付中";
            case "PAID" -> "已支付";
            case "FULFILLED" -> "已履约";
            case "FULFILL_FAILED" -> "履约失败";
            case "PAY_FAILED" -> "支付失败";
            case "EXPIRED" -> "已过期";
            case "REFUNDING" -> "退款中";
            case "REFUNDED" -> "已退款";
            default -> status;
        };
    }

    private String refundStatusLabel(String status)
    {
        return switch (status == null ? "" : status)
        {
            case "NONE" -> "无退款";
            case "REFUNDING" -> "退款中";
            case "REFUNDED" -> "已退款";
            case "FAIL" -> "退款失败";
            default -> status;
        };
    }

    private String ticketTypeLabel(String type)
    {
        return switch (type == null ? "" : type)
        {
            case "01" -> "单程票";
            case "02" -> "计次票";
            default -> type;
        };
    }
}
