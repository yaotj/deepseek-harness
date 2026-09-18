package com.chinasofti.huateng.accsimulator.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import com.chinasofti.huateng.accsimulator.mapper.AccEmployeeCardMapper;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟 ACC 员工卡接口控制器。
 *
 * <p>路径前缀 {@code /api/v1/employee_card}，模拟真实 ACC 的员工卡查询与激活接口，
 * 接收 multipart/form-data 表单请求，返回与 ACC 协议一致的响应格式。</p>
 */
@RestController
@RequestMapping("/api/v1/employee_card")
public class AccMockController {

    private final AccEmployeeCardMapper cardMapper;

    /**
     * 构造模拟 ACC 控制器。
     *
     * @param cardMapper 员工卡数据访问
     */
    public AccMockController(AccEmployeeCardMapper cardMapper) {
        this.cardMapper = cardMapper;
    }

    /**
     * 查询员工卡信息。
     *
     * <p>根据 bizData 中的卡号查询本地模拟库，返回员工卡完整信息；
     * 卡号为空或不存在时返回对应错误码。</p>
     *
     * @param request 公共表单请求，bizData 为 {@link EmployeeCardQueryReqDTO} 的 JSON 串
     * @return 员工卡信息或错误响应
     */
    @PostMapping(value = "/query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> query(@ModelAttribute ItpCommonFormRequest request) {
        EmployeeCardQueryReqDTO query = JSON.parseObject(request.getBizData(), EmployeeCardQueryReqDTO.class);
        if (query == null || query.getCardNo() == null || query.getCardNo().isBlank()) {
            return error("8001", "卡号不能为空");
        }
        AccEmployeeCard stored = cardMapper.selectByCardNo(query.getCardNo());
        if (stored == null) {
            return error("8004", "员工码信息不存在");
        }
        EmployeeCardInfoDTO info = new EmployeeCardInfoDTO();
        info.setCardNo(stored.getCardNo());
        info.setCardStatus(stored.getCardStatus());
        info.setEmployeeName(stored.getEmployeeName());
        info.setPhone(stored.getPhone());
        info.setCompany(stored.getCompany());
        info.setCenter(stored.getCenter());
        info.setDepartment(stored.getDepartment());
        info.setPosition(stored.getPosition());
        info.setIdCardNo(stored.getIdCardNo());
        info.setPhotoUrl(stored.getPhoto());
        return success("查询成功", info);
    }

    /**
     * 激活或禁用员工卡。
     *
     * <p>根据 bizData 中的卡号与动作标识更新电子卡状态；
     * actionFlag 为 1 时激活（状态置为 1），为 0 时禁用（状态置为 2）。</p>
     *
     * @param request 公共表单请求，bizData 为 {@link EmployeeCardActivateReqDTO} 的 JSON 串
     * @return 操作结果或错误响应
     */
    @PostMapping(value = "/activate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> activate(@ModelAttribute ItpCommonFormRequest request) {
        EmployeeCardActivateReqDTO activate = JSON.parseObject(request.getBizData(), EmployeeCardActivateReqDTO.class);
        if (activate == null || activate.getCardNo() == null || activate.getCardNo().isBlank()) {
            return error("8001", "卡号不能为空");
        }
        if (activate.getActionFlag() == null || (activate.getActionFlag() != 0 && activate.getActionFlag() != 1)) {
            return error("8001", "动作标识只能是 0 或 1");
        }
        if (cardMapper.selectByCardNo(activate.getCardNo()) == null) {
            return error("8004", "员工码信息不存在");
        }
        cardMapper.updateStatus(activate.getCardNo(), activate.getActionFlag() == 1 ? 1 : 2);
        return success(activate.getActionFlag() == 1 ? "激活成功" : "禁用成功", null);
    }

    /**
     * 构建成功响应。
     *
     * @param message 提示信息
     * @param bizData 业务数据，可为 null
     * @return 成功响应
     */
    private Map<String, Object> success(String message, Object bizData) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retCode", "0000");
        result.put("retMsg", message);
        if (bizData != null) {
            result.put("bizData", bizData);
        }
        return result;
    }

    /**
     * 构建错误响应。
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 错误响应
     */
    private Map<String, Object> error(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retCode", code);
        result.put("retMsg", message);
        return result;
    }
}
