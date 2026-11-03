package com.chinasofti.huateng.web.service.export.executor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.alipay.AlipayUserExportDTO;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 支付宝用户异步导出执行器。
 */
@Component("ALIPAY_USER")
public class AlipayUserExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(AlipayUserExportExecutor.class);

    private static final String[] HEADERS = {
            "第三方用户 ID", "逻辑卡号", "卡类型", "手机号", "开户渠道", "支付用户标识", "用户状态", "注册时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            28 * 256, 24 * 256, 16 * 256, 14 * 256, 14 * 256, 24 * 256, 12 * 256, 18 * 256
    };

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("ALIPAY_USER 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String queryType = params.getStr("queryType");
        String keyword = params.getStr("keyword");

        List<AlipayUserExportDTO> list = alipayAccountClient.searchAlipayUsers(queryType, keyword);
        final List<AlipayUserExportDTO> dataList = list == null ? List.of() : list;

        long rowCount = dataList.size();
        writeExcel(task, "支付宝用户", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (AlipayUserExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getThirdUserId());
                setCell(row, 1, dto.getCardId());
                setCell(row, 2, cardTypeLabel(dto.getCardType()));
                setCell(row, 3, dto.getMsisdn());
                setCell(row, 4, issueChannelLabel(dto.getChannel()));
                setCell(row, 5, dto.getThirdPayId());
                setCell(row, 6, dto.getStatus());
                setCell(row, 7, formatDateTime(dto.getCreateTime()));
            }
        });

        log.info("ALIPAY_USER 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private String cardTypeLabel(String cardType)
    {
        return switch (cardType == null ? "" : cardType)
        {
            case "01" -> "标准码";
            case "02" -> "HCE";
            case "03" -> "支付宝";
            default -> cardType;
        };
    }

    private String issueChannelLabel(String channel)
    {
        return switch (channel == null ? "" : channel)
        {
            case "01" -> "正常渠道";
            case "07" -> "支付宝";
            default -> channel;
        };
    }

    private String formatDateTime(LocalDateTime value)
    {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
