package com.chinasofti.huateng.web.service.export.executor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.app.BlacklistExportDTO;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 黑名单异步导出执行器。
 */
@Component("BLACKLIST")
public class BlacklistExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(BlacklistExportExecutor.class);

    private static final String[] HEADERS = {
            "卡ID", "三方用户ID", "状态", "渠道同步", "拉黑原因", "解除原因", "创建时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            22 * 256, 18 * 256, 12 * 256, 16 * 256, 28 * 256, 24 * 256, 18 * 256
    };

    @Autowired
    private BlacklistClient blacklistClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("BLACKLIST 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String cardId = params.getStr("cardId");
        String thirdUserId = params.getStr("thirdUserId");
        String status = params.getStr("status");
        String channelSyncStatus = params.getStr("channelSyncStatus");
        String createTimeBegin = params.getStr("createTimeBegin");
        String createTimeEnd = params.getStr("createTimeEnd");

        List<BlacklistExportDTO> list = blacklistClient.listBlacklists(
                cardId, thirdUserId, status, channelSyncStatus, createTimeBegin, createTimeEnd);
        final List<BlacklistExportDTO> dataList = list == null ? List.of() : list;

        long rowCount = dataList.size();
        writeExcel(task, "黑名单", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (BlacklistExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getCardId());
                setCell(row, 1, dto.getThirdUserId());
                setCell(row, 2, statusLabel(dto.getStatus()));
                setCell(row, 3, dto.getChannelSyncStatus());
                setCell(row, 4, dto.getReason());
                setCell(row, 5, dto.getReleaseReason());
                setCell(row, 6, formatDateTime(dto.getCreateTime()));
            }
        });

        log.info("BLACKLIST 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private String statusLabel(String status)
    {
        return switch (status == null ? "" : status)
        {
            case "ACTIVE" -> "生效中";
            case "RELEASING" -> "解除中";
            default -> status;
        };
    }

    private String formatDateTime(LocalDateTime value)
    {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
