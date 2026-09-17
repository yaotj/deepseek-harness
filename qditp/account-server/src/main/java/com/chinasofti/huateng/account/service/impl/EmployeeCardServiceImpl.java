package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.domain.AccResultCode;
import com.chinasofti.huateng.account.domain.EmployeeCardEvent;
import com.chinasofti.huateng.account.domain.EmployeeCardStatus;
import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.account.mapper.AccountExceptionTicketMapper;
import com.chinasofti.huateng.account.mapper.UserAccEmployeeCardMapper;
import com.chinasofti.huateng.account.service.EmployeeCardOutboundService;
import com.chinasofti.huateng.account.service.EmployeeCardPersistenceService;
import com.chinasofti.huateng.account.service.EmployeeCardService;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardNotifyResult;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;
import com.chinasofti.huateng.common.response.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 员工码状态通知与资料查询实现。
 */
@Service
public class EmployeeCardServiceImpl implements EmployeeCardService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeCardServiceImpl.class);

    private final UserAccEmployeeCardMapper employeeCardMapper;
    private final AccountExceptionTicketMapper accountExceptionTicketMapper;
    private final EmployeeCardPersistenceService employeeCardPersistenceService;
    private final EmployeeCardOutboundService employeeCardOutboundService;

    /**
     * APP 注册的单批条数。
     */
    @Value("${employee-card.app-batch-size:200}")
    private int appBatchSize;

    public EmployeeCardServiceImpl(UserAccEmployeeCardMapper employeeCardMapper,
                                   AccountExceptionTicketMapper accountExceptionTicketMapper,
                                   EmployeeCardPersistenceService employeeCardPersistenceService,
                                   EmployeeCardOutboundService employeeCardOutboundService) {
        this.employeeCardMapper = employeeCardMapper;
        this.accountExceptionTicketMapper = accountExceptionTicketMapper;
        this.employeeCardPersistenceService = employeeCardPersistenceService;
        this.employeeCardOutboundService = employeeCardOutboundService;
    }

    @Override
    public EmployeeCardNotifyResult notifyEmployeeCardStatus(EmployeeCardNotifyReqDTO request) {
        EmployeeCardNotifyResult result = new EmployeeCardNotifyResult();
        List<EmployeeCardInfoDTO> cardList = request == null || request.getCardList() == null
                ? Collections.emptyList() : request.getCardList();
        if (cardList.isEmpty()) {
            result.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("cardList不能为空");
            return result;
        }

        List<EmployeeCardInfoDTO> validCards = new ArrayList<>();
        for (EmployeeCardInfoDTO card : cardList) {
            String validationError = validateCard(card);
            if (validationError != null) {
                result.getFailList().add(new EmployeeCardNotifyResult.FailureItem(
                        card == null ? null : card.getCardNo(), validationError));
            } else {
                validCards.add(card);
            }
        }

        int batchSize = Math.max(appBatchSize, 1);
        for (int i = 0; i < validCards.size(); i += batchSize) {
            processAppBatch(validCards.subList(i, Math.min(i + batchSize, validCards.size())), result);
        }

        if (result.getFailList().isEmpty()) {
            result.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            result.setRetMsg("员工码状态通知处理成功");
        } else if (result.getFailList().size() == cardList.size()) {
            result.setRetCode(AccountErrorCodeEnum.FAIL.getCode());
            result.setRetMsg("员工码状态通知处理失败");
        } else {
            result.setRetCode(AccountErrorCodeEnum.PARTIAL_SUCCESS.getCode());
            result.setRetMsg("员工码状态通知部分处理失败");
        }
        return result;
    }

    /**
     * ACC 的 cardNo 是员工号、不是逻辑卡号；后续静默开户由 APP 侧完成。
     */
    private void processAppBatch(List<EmployeeCardInfoDTO> batch, EmployeeCardNotifyResult result) {
        EmployeeCardOutboundService.AppRegisterResult appResult =
                employeeCardOutboundService.registerToApp(batch);
        for (EmployeeCardInfoDTO card : batch) {
            try {
                String appFailure = appResult.failureReasonOf(card.getCardNo());
                if (appFailure != null) {
                    employeeCardPersistenceService.recordEvent(card.getCardNo(), EmployeeCardEvent.OPEN,
                            card.getCardStatus(), appFailure);
                    result.getFailList().add(new EmployeeCardNotifyResult.FailureItem(card.getCardNo(), appFailure));
                    continue;
                }

                try {
                    employeeCardPersistenceService.saveFromStatusNotify(card);
                } catch (RuntimeException ex) {
                    log.error("员工码状态通知落库失败, cardNo={}", card.getCardNo(), ex);
                    employeeCardPersistenceService.recordEvent(card.getCardNo(), EmployeeCardEvent.STATUS,
                            card.getCardStatus(), "员工码信息落库失败");
                    result.getFailList().add(new EmployeeCardNotifyResult.FailureItem(card.getCardNo(), "员工码信息落库失败"));
                }
            } catch (RuntimeException ex) {
                log.error("员工码状态通知处理异常（单卡隔离）, cardNo={}", card.getCardNo(), ex);
                result.getFailList().add(new EmployeeCardNotifyResult.FailureItem(card.getCardNo(), "员工码状态通知处理异常"));
            }
        }
    }

    @Override
    public EmployeeCardQueryResult queryEmployeeCard(EmployeeCardQueryReqDTO request) {
        EmployeeCardQueryResult result = new EmployeeCardQueryResult();
        if (request == null || !StringUtils.hasText(request.getCardNo())) {
            result.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("cardNo不能为空");
            return result;
        }

        UserAccEmployeeCard card = employeeCardMapper.selectByCardNo(request.getCardNo());
        if (card == null) {
            result.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
            result.setRetMsg("员工码信息不存在");
            return result;
        }

        if (!StringUtils.hasText(card.getEmployeeName())) {
            EmployeeCardInfoDTO accCard = employeeCardOutboundService.queryFromAcc(request.getCardNo());
            if (accCard != null) {
                employeeCardPersistenceService.refreshProfileFromAcc(card, accCard);
            } else {
                log.warn("员工码资料未补全或ACC查询失败, cardNo={}", request.getCardNo());
            }
        }

        copyToResult(card, result);
        result.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");
        return result;
    }

    /**
     * APP 请求激活 / 禁用电子员工卡（IF3A）。
     *
     * @param request {@code actionFlag=1} 激活（仅允许 {@code CARD_STATUS=3} 未激活）、
     * {@code 0} 禁用（仅允许 {@code CARD_STATUS=1} 正常）
     * @return {@code 0000} 成功；{@code 8001} 参数非法；{@code 8004} 员工码不存在；
     * {@code 2002} 当前状态不允许；{@code 9998} ACC 已受理但本地回写失败；
     * {@code 9999} 调用 ACC 失败（连不上 / 超时 / 5xx，可重试）；
     * <b>其余码为 ACC 原样透传</b>——ACC 用「HTTP 4xx + 业务错误体」表达参数被拒
     * （实测 {@code 1002 参数校验失败：卡号不存在。}），这类码不可重试
     */
    @Override
    public CommonResult activateEmployeeCard(EmployeeCardActivateReqDTO request) {
        CommonResult result = new CommonResult();
        String validationError = validateActivationRequest(request);
        if (validationError != null) {
            return failure(result, AccountErrorCodeEnum.INVALID_PARAM.getCode(), validationError);
        }
        if (!employeeCardOutboundService.isActivationUrlConfigured()) {
            return failure(result, AccountErrorCodeEnum.FAIL.getCode(), "ACC员工码激活接口地址未配置");
        }

        String cardNo = request.getCardNo().trim();
        request.setCardNo(cardNo);

        UserAccEmployeeCard employeeCard = employeeCardMapper.selectByCardNo(cardNo);
        if (employeeCard == null) {
            return failure(result, AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode(), "员工码信息不存在");
        }
        if (request.getActionFlag() == 1 && !EmployeeCardStatus.NOT_ENABLED.is(employeeCard.getCardStatus())) {
            return failure(result, AccountErrorCodeEnum.EMPLOYEE_CARD_STATUS_NOT_ALLOWED.getCode(), "电子卡当前状态不允许激活操作");
        }
        if (request.getActionFlag() == 0 && !EmployeeCardStatus.NORMAL.is(employeeCard.getCardStatus())) {
            return failure(result, AccountErrorCodeEnum.EMPLOYEE_CARD_STATUS_NOT_ALLOWED.getCode(), "电子卡当前状态不允许禁用操作");
        }

        String response;
        try {
            response = employeeCardOutboundService.requestActivation(request);
        } catch (HttpClientErrorException ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("ACC员工码激活或禁用返回{}并带业务错误体, cardNo={}, actionFlag={}, body={}",
                    ex.getStatusCode(), cardNo, request.getActionFlag(), body);
            if (StringUtils.hasText(body)) {
                applyAccActivationResponse(body, result);
                if (!AccResultCode.isSuccess(result.getRetCode())) {
                    return result;
                }
            }
            return failure(result, AccountErrorCodeEnum.FAIL.getCode(), "调用ACC员工码激活接口失败");
        } catch (RuntimeException ex) {
            log.error("调用ACC员工码激活或禁用接口失败, cardNo={}, actionFlag={}",
                    cardNo, request.getActionFlag(), ex);
            return failure(result, AccountErrorCodeEnum.FAIL.getCode(), "调用ACC员工码激活接口失败");
        }

        applyAccActivationResponse(response, result);
        if (!AccResultCode.isSuccess(result.getRetCode())) {
            log.warn("ACC员工码激活或禁用返回失败, cardNo={}, actionFlag={}, response={}",
                    cardNo, request.getActionFlag(), response);
            return result;
        }

        EmployeeCardStatus target = request.getActionFlag() == 1
                ? EmployeeCardStatus.NORMAL : EmployeeCardStatus.DISABLED;
        boolean activating = target == EmployeeCardStatus.NORMAL;
        String remark = activating ? "APP请求激活员工码成功" : "APP请求禁用员工码成功";
        try {
            if (!employeeCardPersistenceService.applyActivationResult(cardNo, target.code(),
                    activating, remark)) {
                throw new IllegalStateException("按卡号未命中员工码记录（并发注销？）");
            }
            return result;
        } catch (Exception ex) {
            log.error("ACC已受理员工码状态变更但本地回写失败，已开异常工单, cardNo={}, targetStatus={}",
                    cardNo, target.code(), ex);
            openActivationTicketQuietly(cardNo, employeeCard.getThirdUserId(), target.code(), ex.getMessage());
            return failure(result, AccountErrorCodeEnum.LOCAL_WRITE_BACK_FAILED.getCode(), "ACC已受理但本地状态回写失败，已开异常工单待人工处理");
        }
    }

    /**
     * 为「ACC 已受理、本地未落库」开一张异常工单。
     *
     * @param thirdUserId 该员工码归属的 ITP 用户，用于运维按用户维度捞工单。
     * <b>真实数据下当前恒为 {@code null}</b>：`USER_ACC_EMPLOYEE_CARD.THIRD_USER_ID`
     * 全表为空（能写它的 {@code updateThirdUserId} 在本模块没有任何调用点），
     * 只有手工造的数据才会有值（2026-09-11 用合成卡实测确认这条链路本身是通的）。
     * 照样传是为了等挂接链路补齐后自动生效，**NEVER 因为「反正是空」就删掉这个参数**。
     */
    private void openActivationTicketQuietly(String cardNo, String thirdUserId, int targetStatus, String detail) {
        try {
            AccountExceptionTicket ticket = new AccountExceptionTicket();
            ticket.setTicketType(AccountExceptionTicket.TYPE_EMPLOYEE_CARD_STATUS_UNSYNCED);
            ticket.setBizKey(cardNo + ":" + targetStatus);
            ticket.setThirdUserId(thirdUserId);
            ticket.setTicketStatus(AccountExceptionTicket.STATUS_OPEN);
            ticket.setRetryCount(0);
            ticket.setDetail(truncateTicketDetail(detail));
            ticket.setCreateTms(LocalDateTime.now());
            accountExceptionTicketMapper.insert(ticket);
        } catch (Exception ex) {
            log.error("员工码状态未同步工单开立失败或已存在, cardNo={}, targetStatus={}", cardNo, targetStatus, ex);
        }
    }

    private String truncateTicketDetail(String detail) {
        if (detail == null) {
            return "ACC已受理但本地状态回写失败";
        }
        return detail.length() > 500 ? detail.substring(0, 500) : detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult updateEmployeeInfo(EmployeeInfoUpdateNotifyReqDTO request) {
        CommonResult result = new CommonResult();
        if (request == null || !StringUtils.hasText(request.getCardNo())) {
            return failure(result, AccountErrorCodeEnum.INVALID_PARAM.getCode(), "cardNo不能为空");
        }

        UserAccEmployeeCard employee = employeeCardMapper.selectByCardNo(request.getCardNo());
        if (employee == null) {
            return failure(result, AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode(), "未注册员工信息");
        }

        employee.setCompany(request.getCompany());
        employee.setCenter(request.getCenter());
        employee.setDepartment(request.getDepartment());
        employee.setPosition(request.getPosition());
        employee.setPhotoUrl(request.getPhoto());
        employee.setUpdateTms(LocalDateTime.now());
        if (employeeCardMapper.updateEmployeeInfo(employee) == 0) {
            log.warn("员工信息变更落库影响0行（并发删除？）, cardNo={}, id={}", employee.getCardNo(), employee.getId());
            return failure(result, AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode(), "员工信息记录已不存在，变更未落库");
        }
        employeeCardPersistenceService.recordEvent(employee.getCardNo(), EmployeeCardEvent.CHANGE,
                employee.getCardStatus(), "员工信息变更通知处理成功");

        result.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("员工信息变更通知处理成功");
        return result;
    }

    private void applyAccActivationResponse(String response, CommonResult result) {
        if (!StringUtils.hasText(response)) {
            failure(result, AccountErrorCodeEnum.FAIL.getCode(), "ACC员工码激活接口无响应");
            return;
        }
        JSONObject body = JSON.parseObject(response);
        if (body == null) {
            failure(result, AccountErrorCodeEnum.FAIL.getCode(), "ACC员工码激活接口响应非JSON");
            return;
        }
        Object bizData = body.get("bizData");
        JSONObject responseBody = bizData instanceof JSONObject jsonObject ? jsonObject : body;
        if (bizData instanceof String json && StringUtils.hasText(json)) {
            JSONObject parsed = JSON.parseObject(json);
            if (parsed == null) {
                failure(result, AccountErrorCodeEnum.FAIL.getCode(), "ACC员工码激活接口bizData非JSON");
                return;
            }
            responseBody = parsed;
        }
        String retCode = responseBody.getString("retCode");
        if (!StringUtils.hasText(retCode)) {
            retCode = responseBody.getString("code");
        }
        String retMsg = responseBody.getString("retMsg");
        if (!StringUtils.hasText(retMsg)) {
            retMsg = responseBody.getString("msg");
        }
        result.setRetCode(StringUtils.hasText(retCode) ? retCode : AccountErrorCodeEnum.FAIL.getCode());
        result.setRetMsg(StringUtils.hasText(retMsg) ? retMsg : "ACC员工码激活接口返回异常");
    }

    private String validateCard(EmployeeCardInfoDTO card) {
        if (card == null || !StringUtils.hasText(card.getCardNo())) {
            return "cardNo不能为空";
        }
        if (!StringUtils.hasText(card.getPhone())) {
            return "phone不能为空";
        }
        if (!EmployeeCardStatus.isKnownCode(card.getCardStatus())) {
            return "cardStatus必须为1、2、3或4";
        }
        return null;
    }

    private String validateActivationRequest(EmployeeCardActivateReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getCardNo())) {
            return "cardNo不能为空";
        }
        if (request.getCardNo().trim().length() > 20) {
            return "cardNo长度不能超过20";
        }
        if (request.getActionFlag() == null || (request.getActionFlag() != 0 && request.getActionFlag() != 1)) {
            return "actionFlag必须为0或1";
        }
        return null;
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

    private CommonResult failure(CommonResult result, String retCode, String retMsg) {
        result.setRetCode(retCode);
        result.setRetMsg(retMsg);
        return result;
    }
}
