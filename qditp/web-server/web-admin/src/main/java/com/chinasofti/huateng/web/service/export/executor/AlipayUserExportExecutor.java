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
            "第三方用户 ID", "逻辑卡号", "卡类型", "手机号", "开户渠道", "支付用户标识", "签约流水号", "用户状态", "注册时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            28 * 256, 24 * 256, 16 * 256, 14 * 256, 14 * 256, 24 * 256, 28 * 256, 12 * 256, 18 * 256
    };

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("ALIPAY_USER 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        List<AlipayUserExportDTO> list = alipayAccountClient.searchAlipayUsers(
                params.getStr("msisdn"), params.getStr("cardId"),
                params.getStr("thirdUserId"), params.getStr("reqContractNo"));
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
                setCell(row, 6, dto.getReqContractNo());
                setCell(row, 7, userStatusLabel(dto.getStatus()));
                setCell(row, 8, formatDateTime(dto.getCreateTime()));
            }
        });

        log.info("ALIPAY_USER 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    /**
     * 卡类型翻成「编码-中文」，与页面同一口径（前端真源 {@code web/src/utils/cardType.js}）。
     *
     * <p>页面走的是 {@code formatCodeLabel} ⇒ 导出也必须带编码前缀，
     * 否则同一个字段在页面是 {@code 02-HCE}、在导出里是 {@code HCE}，
     * 运营拿导出对账时会对不上（2026-11-06 把本执行器的三个 label 一起对齐）。
     */
    private String cardTypeLabel(String cardType)
    {
        return codeLabel(cardType, switch (cardType == null ? "" : cardType)
        {
            case "01" -> "标准码";
            case "02" -> "HCE";
            case "03" -> "支付宝";
            default -> null;
        });
    }

    /** 开户渠道是发卡渠道码值空间，NEVER 套用支付渠道那张表。 */
    private String issueChannelLabel(String channel)
    {
        return codeLabel(channel, switch (channel == null ? "" : channel)
        {
            case "01" -> "正常渠道";
            case "07" -> "支付宝";
            default -> null;
        });
    }

    /**
     * 用户状态。取值集合目前只有 {@code ACTIVE} —— 2026-11-06 核查 alipay-account-server 全模块，
     * 代码里仅此一个写入字面量、无枚举类、DDL 也只有 {@code DEFAULT 'ACTIVE'}。
     * 出现第二个值时 MUST 先回代码与库确认语义再补，NEVER 按别的系统习惯猜 {@code INACTIVE}。
     */
    private String userStatusLabel(String status)
    {
        return codeLabel(status, "ACTIVE".equals(status) ? "生效中" : null);
    }

    /** 与前端 {@code utils/codeLabel.js#formatCodeLabel} 同语义：翻译不到就只显编码、空值显空串。 */
    private String codeLabel(String code, String name)
    {
        if (code == null || code.isBlank())
        {
            return "";
        }
        return name == null || name.isBlank() ? code : code + "-" + name;
    }

    private String formatDateTime(LocalDateTime value)
    {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
