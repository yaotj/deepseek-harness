package com.chinasofti.huateng.web.service.export.executor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.model.employee.AccEmployeeCardExportDTO;
import com.chinasofti.huateng.rpc.accsimulator.AccSimulatorClient;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 员工卡 ACC 模拟器卡片异步导出执行器。
 */
// 该服务不在测试/生产部署清单中，禁用此导出执行器；如需启用，取消注释并部署 employee-card-acc-simulator 服务。
// @Component("ACC_SIMULATOR_CARD")
public class AccSimulatorCardExportExecutor extends AbstractExcelExportExecutor
{
    private static final Logger log = LoggerFactory.getLogger(AccSimulatorCardExportExecutor.class);

    private static final String[] HEADERS = {
            "员工号", "姓名", "手机号", "状态", "公司", "中心", "部门", "岗位", "更新时间"
    };
    private static final int[] COLUMN_WIDTHS = {
            18 * 256, 12 * 256, 14 * 256, 12 * 256, 24 * 256, 18 * 256, 20 * 256, 16 * 256, 18 * 256
    };

    @Autowired
    private AccSimulatorClient accSimulatorClient;

    @Override
    public void execute(AsyncExportTask task) throws Exception
    {
        String filePath = task.getFilePath();
        log.info("ACC_SIMULATOR_CARD 异步导出开始, taskId={}, filePath={}", task.getId(), filePath);

        JSONObject params = JSONUtil.parseObj(task.getParamJson());
        String cardNo = params.getStr("cardNo");
        String employeeName = params.getStr("employeeName");
        Integer cardStatus = params.getInt("cardStatus");

        List<AccEmployeeCardExportDTO> list = accSimulatorClient.listAccSimulatorCards(cardNo, employeeName, cardStatus);
        final List<AccEmployeeCardExportDTO> dataList = list == null ? List.of() : list;

        long rowCount = dataList.size();
        writeExcel(task, "员工卡模拟器卡片", HEADERS, COLUMN_WIDTHS, rowCount, (sheet, startRow) -> {
            int rowIdx = startRow;
            for (AccEmployeeCardExportDTO dto : dataList)
            {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, dto.getCardNo());
                setCell(row, 1, dto.getEmployeeName());
                setCell(row, 2, dto.getPhone());
                setCell(row, 3, statusLabel(dto.getCardStatus()));
                setCell(row, 4, dto.getCompany());
                setCell(row, 5, dto.getCenter());
                setCell(row, 6, dto.getDepartment());
                setCell(row, 7, dto.getPosition());
                setCell(row, 8, formatDateTime(dto.getUpdateTms()));
            }
        });

        log.info("ACC_SIMULATOR_CARD 异步导出完成, taskId={}, rowCount={}, fileSize={}",
                task.getId(), rowCount, task.getFileSize());
    }

    private String statusLabel(Integer status)
    {
        return switch (status == null ? 0 : status)
        {
            case 1 -> "启用";
            case 2 -> "禁用";
            case 3 -> "未启用";
            case 4 -> "注销";
            default -> String.valueOf(status);
        };
    }

    private String formatDateTime(LocalDateTime value)
    {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
