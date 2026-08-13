package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardLogMapper;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardMapper;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.account.service.EmployeeCardService;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyResult;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import com.chinasofti.huateng.common.response.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 员工码状态通知与资料查询实现。
 */
@Service
public class EmployeeCardServiceImpl implements EmployeeCardService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeCardServiceImpl.class);
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserAccEmployeeCardMapper employeeCardMapper;
    private final UserAccEmployeeCardLogMapper employeeCardLogMapper;
    private final EmployeeCardPersistenceService employeeCardPersistenceService;
    private final RestTemplate restTemplate;

    @Value("${employee-card.app-register-url:}")
    private String appRegisterUrl;

    @Value("${employee-card.acc-query-url:}")
    private String accQueryUrl;

    @Value("${employee-card.provider-id:06}")
    private String providerId;

    @Value("${employee-card.charset:UTF-8}")
    private String charset;

    @Value("${employee-card.format:json}")
    private String format;

    @Value("${employee-card.device-id:ITP}")
    private String deviceId;

    @Value("${employee-card.sign-type:00}")
    private String signType;

    public EmployeeCardServiceImpl(UserAccEmployeeCardMapper employeeCardMapper,
                                   UserAccEmployeeCardLogMapper employeeCardLogMapper,
                                   EmployeeCardPersistenceService employeeCardPersistenceService,
                                   RestTemplateBuilder restTemplateBuilder) {
        this.employeeCardMapper = employeeCardMapper;
        this.employeeCardLogMapper = employeeCardLogMapper;
        this.employeeCardPersistenceService = employeeCardPersistenceService;
        this.restTemplate = restTemplateBuilder.build();
    }

    @Override
    public EmployeeCardNotifyResult notifyEmployeeCardStatus(EmployeeCardNotifyReqDTO request) {
        EmployeeCardNotifyResult result = new EmployeeCardNotifyResult();
        List<EmployeeCardInfoDTO> cardList = request == null || request.getCardList() == null
                ? Collections.emptyList() : request.getCardList();
        if (cardList.isEmpty()) {
            result.setRetCode("8001");
            result.setRetMsg("cardList不能为空");
            return result;
        }

        for (EmployeeCardInfoDTO card : cardList) {
            String validationError = validateCard(card);
            if (validationError != null) {
                result.getFailList().add(new EmployeeCardNotifyResult.FailureItem(
                        card == null ? null : card.getCardNo(), validationError));
                continue;
            }

            AppRegistrationResult appResult = registerEmployeeCardToApp(card);
            if (!appResult.success()) {
                insertLog(card.getCardNo(), "OPEN", card.getCardStatus(), appResult.message());
                result.getFailList().add(new EmployeeCardNotifyResult.FailureItem(card.getCardNo(), appResult.message()));
                continue;
            }

            try {
                employeeCardPersistenceService.saveFromStatusNotify(card);
            } catch (RuntimeException ex) {
                log.error("员工码状态通知落库失败, cardNo={}", card.getCardNo(), ex);
                insertLog(card.getCardNo(), "STATUS", card.getCardStatus(), "员工码信息落库失败");
                result.getFailList().add(new EmployeeCardNotifyResult.FailureItem(card.getCardNo(), "员工码信息落库失败"));
            }
        }

        if (result.getFailList().isEmpty()) {
            result.setRetCode("0000");
            result.setRetMsg("员工码状态通知处理成功");
        } else if (result.getFailList().size() == cardList.size()) {
            result.setRetCode("9999");
            result.setRetMsg("员工码状态通知处理失败");
        } else {
            result.setRetCode("0001");
            result.setRetMsg("员工码状态通知部分处理失败");
        }
        return result;
    }

    @Override
    public EmployeeCardQueryResult queryEmployeeCard(EmployeeCardQueryReqDTO request) {
        EmployeeCardQueryResult result = new EmployeeCardQueryResult();
        if (request == null || !StringUtils.hasText(request.getCardNo())) {
            result.setRetCode("8001");
            result.setRetMsg("cardNo不能为空");
            return result;
        }

        UserAccEmployeeCard card = employeeCardMapper.selectByCardNo(request.getCardNo());
        if (card == null) {
            result.setRetCode("8004");
            result.setRetMsg("员工码信息不存在");
            return result;
        }

        if (!StringUtils.hasText(card.getEmployeeName())) {
            EmployeeCardInfoDTO accCard = queryEmployeeCardFromAcc(request.getCardNo());
            if (accCard != null) {
                applyAccInfo(card, accCard);
                employeeCardMapper.update(card);
            } else {
                log.warn("员工码资料未补全或ACC查询失败, cardNo={}", request.getCardNo());
            }
        }

        copyToResult(card, result);
        result.setRetCode("0000");
        result.setRetMsg("成功");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult updateEmployeeInfo(EmployeeInfoUpdateNotifyReqDTO request) {
        CommonResult result = new CommonResult();
        if (request == null || !StringUtils.hasText(request.getCardNo())) {
            return failure(result, "8001", "cardNo不能为空");
        }

        UserAccEmployeeCard employee = employeeCardMapper.selectByCardNo(request.getCardNo());
        if (employee == null) {
            return failure(result, "8004", "未注册员工信息");
        }

        employee.setCompany(request.getCompany());
        employee.setCenter(request.getCenter());
        employee.setDepartment(request.getDepartment());
        employee.setPosition(request.getPosition());
        employee.setPhotoUrl(request.getPhoto());
        employee.setUpdateTms(LocalDateTime.now());
        employeeCardMapper.updateEmployeeInfo(employee);
        insertLog(employee.getCardNo(), "CHANGE", employee.getCardStatus(), "员工信息变更通知处理成功");

        result.setRetCode("0000");
        result.setRetMsg("员工信息变更通知处理成功");
        return result;
    }

    private AppRegistrationResult registerEmployeeCardToApp(EmployeeCardInfoDTO card) {
        if (!StringUtils.hasText(appRegisterUrl)) {
            return AppRegistrationResult.failure("APP注册接口地址未配置");
        }
        try {
            String response = postFormData(appRegisterUrl, card);
            if (!StringUtils.hasText(response)) {
                return AppRegistrationResult.failure("APP注册接口无响应");
            }
            JSONObject body = JSON.parseObject(response);
            String resultCode = body.getString("retCode");
            if (!StringUtils.hasText(resultCode)) {
                resultCode = body.getString("code");
            }
            if ("0000".equals(resultCode) || "200".equals(resultCode)) {
                return AppRegistrationResult.accepted();
            }
            String resultMessage = body.getString("retMsg");
            if (!StringUtils.hasText(resultMessage)) {
                resultMessage = body.getString("msg");
            }
            return AppRegistrationResult.failure(
                    StringUtils.hasText(resultMessage) ? resultMessage : "APP注册接口返回失败");
        } catch (RuntimeException ex) {
            log.error("调用APP注册员工码失败, cardNo={}", card.getCardNo(), ex);
            return AppRegistrationResult.failure("调用APP注册接口失败");
        }
    }

    private EmployeeCardInfoDTO queryEmployeeCardFromAcc(String cardNo) {
        if (!StringUtils.hasText(accQueryUrl)) {
            log.warn("ACC员工码查询地址未配置, cardNo={}", cardNo);
            return null;
        }
        try {
            EmployeeCardQueryReqDTO request = new EmployeeCardQueryReqDTO();
            request.setCardNo(cardNo);
            String response = postFormData(accQueryUrl, request);
            if (!StringUtils.hasText(response)) {
                return null;
            }
            JSONObject body = JSON.parseObject(response);
            String resultCode = body.getString("retCode");
            if (!StringUtils.hasText(resultCode)) {
                resultCode = body.getString("code");
            }
            if (StringUtils.hasText(resultCode) && !"0000".equals(resultCode) && !"200".equals(resultCode)) {
                log.warn("ACC员工码查询返回失败, cardNo={}, response={}", cardNo, response);
                return null;
            }
            Object bizData = body.get("bizData");
            if (bizData instanceof JSONObject jsonObject) {
                return jsonObject.toJavaObject(EmployeeCardInfoDTO.class);
            }
            if (bizData instanceof String json && StringUtils.hasText(json)) {
                return JSON.parseObject(json, EmployeeCardInfoDTO.class);
            }
            return body.toJavaObject(EmployeeCardInfoDTO.class);
        } catch (RuntimeException ex) {
            log.error("调用ACC查询员工码信息失败, cardNo={}", cardNo, ex);
            return null;
        }
    }

    private String postFormData(String url, Object bizData) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("providerId", providerId);
        formData.add("charset", charset);
        formData.add("format", format);
        formData.add("timestamp", LocalDateTime.now().format(REQUEST_TIME_FORMATTER));
        formData.add("deviceId", deviceId);
        formData.add("signType", signType);
        formData.add("sign", "");
        formData.add("bizData", JSON.toJSONString(bizData));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForObject(url, new HttpEntity<>(formData, headers), String.class);
    }

    private String validateCard(EmployeeCardInfoDTO card) {
        if (card == null || !StringUtils.hasText(card.getCardNo())) {
            return "cardNo不能为空";
        }
        if (!StringUtils.hasText(card.getPhone())) {
            return "phone不能为空";
        }
        if (card.getCardStatus() == null || card.getCardStatus() < 1 || card.getCardStatus() > 4) {
            return "cardStatus必须为1、2、3或4";
        }
        return null;
    }

    private void applyAccInfo(UserAccEmployeeCard target, EmployeeCardInfoDTO source) {
        target.setCardNo(source.getCardNo());
        target.setEmployeeName(source.getEmployeeName());
        target.setIdCardNo(source.getIdCardNo());
        target.setCompany(source.getCompany());
        target.setCenter(source.getCenter());
        target.setDepartment(source.getDepartment());
        target.setPosition(source.getPosition());
        target.setPhotoUrl(source.getPhotoUrl());
        target.setCardStatus(source.getCardStatus());
    }

    private void copyToResult(UserAccEmployeeCard source, EmployeeCardQueryResult target) {
        target.setCardNo(source.getCardNo());
        target.setPhone(source.getPhone());
        target.setEmployeeName(source.getEmployeeName());
        target.setIdCardNo(source.getIdCardNo());
        target.setCompany(source.getCompany());
        target.setCenter(source.getCenter());
        target.setDepartment(source.getDepartment());
        target.setPosition(source.getPosition());
        target.setPhotoUrl(source.getPhotoUrl());
        target.setCardStatus(source.getCardStatus());
    }

    private void insertLog(String cardNo, String eventType, Integer cardStatus, String remark) {
        com.chinasofti.huateng.account.entity.UserAccEmployeeCardLog logRecord =
                new com.chinasofti.huateng.account.entity.UserAccEmployeeCardLog();
        logRecord.setCardNo(cardNo);
        logRecord.setEventType(eventType);
        logRecord.setCardStatus(cardStatus);
        logRecord.setRemark(remark);
        logRecord.setCreateTms(LocalDateTime.now());
        employeeCardLogMapper.insert(logRecord);
    }

    private CommonResult failure(CommonResult result, String retCode, String retMsg) {
        result.setRetCode(retCode);
        result.setRetMsg(retMsg);
        return result;
    }

    private record AppRegistrationResult(boolean success, String message) {
        private static AppRegistrationResult accepted() {
            return new AppRegistrationResult(true, null);
        }

        private static AppRegistrationResult failure(String message) {
            return new AppRegistrationResult(false, message);
        }
    }
}
