package com.chinasofti.huateng.accsimulator;

import com.alibaba.fastjson2.JSON;
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

@RestController
@RequestMapping("/api/v1/employee_card")
public class AccMockController {

    private final AccEmployeeCardMapper cardMapper;

    public AccMockController(AccEmployeeCardMapper cardMapper) {
        this.cardMapper = cardMapper;
    }

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

    private Map<String, Object> success(String message, Object bizData) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retCode", "0000");
        result.put("retMsg", message);
        if (bizData != null) {
            result.put("bizData", bizData);
        }
        return result;
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retCode", code);
        result.put("retMsg", message);
        return result;
    }
}
