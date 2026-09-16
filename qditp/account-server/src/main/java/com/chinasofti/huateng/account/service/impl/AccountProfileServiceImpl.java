package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.service.AccountProfileService;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

/**
 * 账户资料的读取与 HCE 数据维护实现，见 {@link AccountProfileService}。
 *
 * <p>2026-09-11 第六轮拆分从 {@code AccountApplicationServiceImpl}（已删除）逐行搬来，行为不变。
 * 三个入口都只读写 {@code USER_ITP_REG_INFO} 自身字段，<b>不涉及任何状态机</b>：
 * 销户改 {@code DEL_YN} 归 {@code AccountCancelService}、支付通道字段归 {@code PayChannelService}，
 * <b>NEVER 在本类里改那两类字段</b>。</p>
 */
@Service
public class AccountProfileServiceImpl implements AccountProfileService {
    private static final Logger log = LoggerFactory.getLogger(AccountProfileServiceImpl.class);

    private final UserItpRegInfoMapper userItpRegInfoMapper;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public AccountProfileServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
    }

    @Override
    public QueryUserInfoResult queryUserInfo(QueryUserInfoReqDTO request) {
        QueryUserInfoResult response = new QueryUserInfoResult();
        try {
            log.info("开始处理查询用户信息, request={}", JSON.toJSONString(request));
            String validMsg = validateQueryUserInfoRequest(request);
            if (validMsg != null) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                log.warn("查询用户信息参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            String thirdUserId = request.getThirdUserId().trim();
            String cardId = request.getCardId().trim();
            String cardType = CardTypeMapping.toIssueCardType(request.getCardType());
            UserItpRegInfo regInfo = userItpRegInfoMapper.selectActiveByThirdUserIdAndCardIdAndCardType(
                    thirdUserId, cardId, cardType);
            if (regInfo == null || !regInfo.isActive()) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("查询用户信息未找到有效用户账户, thirdUserId={}, cardId={}, cardType={}",
                        thirdUserId, cardId, cardType);
                return response;
            }

            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setThirdUserId(regInfo.getThirdUserId());
            response.setCardId(regInfo.getCardId());
            response.setCardType(regInfo.getCardType());
            response.setItpCardType(regInfo.getItpCardType());
            response.setChannel(regInfo.getChannel());
            response.setCardIssueCode(regInfo.getCardIssueCode());
            response.setThirdPayId(regInfo.getThirdPayId());
            response.setReqContractNo(regInfo.getReqContractNo());
            response.setHceData(regInfo.getHceData());
            response.setMsisdn(regInfo.getMsisdn());
            response.setCompanionFlag(regInfo.getCompanionFlag());
            if (regInfo.getRegTms() != null) {
                response.setRegTms(regInfo.getRegTms().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            }
            log.info("查询用户信息成功, thirdUserId={}, cardId={}, cardType={}, itpCardType={}, channel={}, cardIssueCode={}, thirdPayId={}, reqContractNo={}, msisdn={}, regTms={}, companionFlag={}",
                    regInfo.getThirdUserId(), regInfo.getCardId(), regInfo.getCardType(), regInfo.getItpCardType(),
                    regInfo.getChannel(), regInfo.getCardIssueCode(), regInfo.getThirdPayId(), regInfo.getReqContractNo(),
                    regInfo.getMsisdn(), response.getRegTms(), regInfo.getCompanionFlag());
            return response;
        } catch (Exception e) {
            log.error("处理查询用户信息异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    public QueryUserInfoResult queryCardTypeByCardId(String cardId) {
        QueryUserInfoResult response = new QueryUserInfoResult();
        try {
            if (!StringUtils.hasText(cardId)) {
                response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("cardId不能为空");
                return response;
            }
            UserItpRegInfo regInfo = userItpRegInfoMapper.selectActiveByCardId(cardId.trim());
            if (regInfo == null || !regInfo.isActive()) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("按逻辑卡号未找到有效用户账户, cardId={}", cardId);
                return response;
            }
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            response.setThirdUserId(regInfo.getThirdUserId());
            response.setCardId(regInfo.getCardId());
            response.setCardType(regInfo.getCardType());
            response.setItpCardType(regInfo.getItpCardType());
            response.setCardIssueCode(regInfo.getCardIssueCode());
            response.setMsisdn(regInfo.getMsisdn());
            response.setCompanionFlag(regInfo.getCompanionFlag());
            // 签约信息 MUST 一并返回：出站扣费链路（ticket-server → fep-dev-server →
            // gate-txn-pay-server → pay-sign-server）靠这三个字段定位免密扣款的签约协议。
            // 原先漏了这三个 set，调用方 GateTicketHandler.applyActualCardType 每次都读到 null，
            // 只能由 pay-sign-server 再回查一次 account-server 兜底（2026-08-26 修复）。
            response.setChannel(regInfo.getChannel());
            response.setReqContractNo(regInfo.getReqContractNo());
            response.setThirdPayId(regInfo.getThirdPayId());
            if (regInfo.getRegTms() != null) {
                response.setRegTms(regInfo.getRegTms().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            }
            log.info("按逻辑卡号查询真实ITP卡类型成功, cardId={}, cardType={}, itpCardType={}, msisdn={}, regTms={}, "
                            + "companionFlag={}, channel={}, reqContractNo={}, thirdPayId={}",
                    regInfo.getCardId(), regInfo.getCardType(), regInfo.getItpCardType(),
                    regInfo.getMsisdn(), response.getRegTms(), regInfo.getCompanionFlag(),
                    regInfo.getChannel(), regInfo.getReqContractNo(), regInfo.getThirdPayId());
            return response;
        } catch (Exception e) {
            log.error("按逻辑卡号查询真实ITP卡类型异常, cardId={}", cardId, e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 保存 IF1A-01 reserve1 中携带的闸机处理后 HCE 卡数据。
     */
    @Override
    public UpdateHceDataResult updateHceData(UpdateHceDataReqDTO request) {
        UpdateHceDataResult response = new UpdateHceDataResult();
        if (request == null || !StringUtils.hasText(request.getCardId()) || !StringUtils.hasText(request.getHceData())) {
            response.setRetCode(AccountErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId和hceData不能为空");
            return response;
        }
        try {
            int updated = userItpRegInfoMapper.updateActiveHceDataByCardId(
                    request.getCardId().trim(), request.getHceData().trim());
            if (updated == 0) {
                response.setRetCode(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getCode());
                response.setRetMsg(AccountErrorCodeEnum.NO_ACCOUNT_CARD.getMsg());
                log.warn("更新HCE卡数据未找到有效账户, cardId={}", request.getCardId());
                return response;
            }
            response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SUCCESS.getMsg());
            log.info("更新HCE卡数据成功, cardId={}, hceDataLength={}", request.getCardId(), request.getHceData().trim().length());
            return response;
        } catch (Exception e) {
            log.error("更新HCE卡数据异常, cardId={}", request.getCardId(), e);
            response.setRetCode(AccountErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(AccountErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    private String validateQueryUserInfoRequest(QueryUserInfoReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }
}
