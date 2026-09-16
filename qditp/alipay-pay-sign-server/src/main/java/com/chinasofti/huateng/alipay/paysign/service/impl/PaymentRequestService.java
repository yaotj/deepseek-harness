package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class PaymentRequestService {
    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private PayCenterClient payCenterClient;

    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    private com.chinasofti.huateng.alipay.paysign.service.impl.PaymentNotifyAdapter paymentNotifyAdapter;

    @Autowired
    private IndustryDetailEnricher industryDetailEnricher;

    @Autowired
    private BizDataBuilder bizDataBuilder;

    @Autowired
    private BlacklistClient blacklistClient;

    /**
     * 支付宝出行扣费申请。
     *
     * <p>本方法 <b>NEVER 再写 ALIPAY_PAY_LOG</b>：过闸扣费已收口到 gate-txn-pay-server，
     * 订单与状态的唯一权威是 {@code GATE_TXN_PAY}（落单在 {@code PaySignInitiator} 之前完成，
     * 幂等靠该表的唯一键 + 状态白名单）。这里再落一份日志表就是双写，
     * 两边状态一旦分叉无法判定谁对；<b>NEVER 恢复双写</b>。
     * 「订单已存在直接返回」的短路也随之下沉到 gate-txn-pay-server，本方法只负责调支付中心。</p>
     */
    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        AlipayTripRequestPayRespDTO response = new AlipayTripRequestPayRespDTO();
        log.info("接收到支付宝支付申请报文: {}", JSON.toJSONString(request));

        if (request == null || !isValidPayRequest(request)) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号/支付金额/行业类型/订单标题/订单描述/行业详情不能为空");
        }

//        if (request.getRequestSignSeq() == null || request.getRequestSignSeq().trim().isEmpty()) {
//            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "免密场景签约流水号不能为空");
//        }

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (signInfo == null) {
            throw new BusinessException(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), "用户未签约");
        }

        request.setRequestSignSeq(signInfo.getAgreementCode());

        request.setIndustryDetail(industryDetailEnricher.enrich(request.getIndustryDetail(), signInfo.getThirdUserId()));
        log.info("支付宝支付申请,行业详情 enrichment 完成, orderNo={}", request.getOrderNo());

        Map<String, Object> bizDataMap = bizDataBuilder.build(request, signInfo, payCenterProperties);

        log.info("支付宝支付申请,调用支付中心支付接口,请求参数: {}", JSON.toJSONString(bizDataMap));
        PayCenterResponse payCenterResponse = payCenterClient.requestPay(bizDataMap);
        log.info("支付宝支付申请,支付中心响应结果: success={}, msg={}", payCenterResponse != null ? payCenterResponse.getSuccess() : "null", payCenterResponse != null ? payCenterResponse.getMsg() : "null");

        boolean paySuccess = false;
        String tradeNo = "";
        String resultCode = FepAppErrorCodeEnum.SYSTEM_ERROR.getCode();
        String resultMsg = "系统内部错误";

        if (payCenterResponse != null && (payCenterResponse.getCode() != null && payCenterResponse.getCode() == 200 || Boolean.TRUE.equals(payCenterResponse.getSuccess()))) {
            String dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "retCode");
            if (dataRetCode == null) {
                dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "returnCode");
            }
            String dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "retMsg");
            if (dataRetMsg == null) {
                dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "returnMsg");
            }

            log.info("支付宝支付申请,支付中心解密后数据: retCode={}, retMsg={}", dataRetCode, dataRetMsg);

            if ("SUCCESS".equals(dataRetCode)) {
                paySuccess = true;
                resultCode = FepAppErrorCodeEnum.SUCCESS.getCode();
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付成功";
                tradeNo = payCenterClient.getStringFromData(payCenterResponse, "channelOrderNo");
            } else {
                resultCode = FepAppErrorCodeEnum.FAIL.getCode();
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付失败";
                addBlackListIfNeeded(signInfo, resultMsg);
            }
        } else if (payCenterResponse != null) {
            resultCode = FepAppErrorCodeEnum.FAIL.getCode();
            resultMsg = payCenterResponse.getMsg() != null ? payCenterResponse.getMsg() : "支付失败";
            log.error("支付宝支付申请未拿到业务应答，按传输层失败处理、NEVER 加黑名单, orderNo={}, code={}, msg={}, success={}",
                    request.getOrderNo(), payCenterResponse.getCode(), payCenterResponse.getMsg(), payCenterResponse.getSuccess());
        } else {
            resultCode = FepAppErrorCodeEnum.SYSTEM_ERROR.getCode();
            resultMsg = "调用支付中心失败";
            log.error("支付宝支付申请调用支付中心无响应，按传输层失败处理、NEVER 加黑名单, orderNo={}", request.getOrderNo());
        }

        response.setRetCode(resultCode);
        response.setRetMsg(resultMsg);
        response.setOrderNo(request.getOrderNo());
        log.info("支付宝支付申请完成, orderNo={}, tradeNo={}, status={}", request.getOrderNo(), tradeNo, paySuccess ? "SUCCESS" : "FAIL");
        return response;
    }

    private boolean isValidPayRequest(AlipayTripRequestPayReqDTO request) {
        return request.getOrderNo() != null && !request.getOrderNo().trim().isEmpty()
                && request.getAmount() != null
                && request.getIndustryType() != null && !request.getIndustryType().trim().isEmpty()
                && request.getSubject() != null && !request.getSubject().trim().isEmpty()
                && request.getBody() != null && !request.getBody().trim().isEmpty()
                && request.getIndustryDetail() != null && !request.getIndustryDetail().trim().isEmpty();
    }

    /**
     * 扣款失败后把该卡加入黑名单。
     *
     * <p><b>只允许在「支付中心已给出业务应答且判定为扣款失败」这一条分支调用</b>
     * （即解密 data 后 {@code retCode != SUCCESS}）。传输层失败、网关路径错
     * （实测形态是 {@code code=600 操作失败}，见 AGENTS.md §8）、对端 5xx、响应为空
     * 都 <b>NEVER 加黑名单</b>——那些与乘客的付款能力无关，加黑会直接拦住其过闸。
     *
     * <p>{@code blacklistClient.addBlackList} 是「返回结果对象、不抛异常」的 RPC 包装，
     * 因此 <b>MUST 显式判 retCode</b>（AGENTS.md §5.2）；判不过只打 ERROR，
     * 不影响本次支付申请对上游的应答。</p>
     */
    private void addBlackListIfNeeded(AlipaySignInfo signInfo, String reason) {
        if (signInfo == null || !StringUtils.hasText(signInfo.getCardId()) || !StringUtils.hasText(signInfo.getThirdUserId())) {
            return;
        }
        try {
            AddBlackListReqDTO blackListRequest = new AddBlackListReqDTO();
            blackListRequest.setCardId(signInfo.getCardId());
            blackListRequest.setThirdUserId(signInfo.getThirdUserId());
            blackListRequest.setCardType(signInfo.getCardType());
            blackListRequest.setReason(StringUtils.hasText(reason) ? reason : "地铁扣款失败");
            log.info("支付宝支付申请失败,添加黑名单, cardId={}, thirdUserId={}, cardType={}, reason={}",
                    blackListRequest.getCardId(), blackListRequest.getThirdUserId(), blackListRequest.getCardType(), blackListRequest.getReason());
            BlackListOperateResult blackListResult = blacklistClient.addBlackList(blackListRequest);
            if (blackListResult == null || !FepAppErrorCodeEnum.SUCCESS.getCode().equals(blackListResult.getRetCode())) {
                log.error("添加黑名单未成功，该卡仍可过闸、MUST 人工核对, cardId={}, thirdUserId={}, retCode={}, retMsg={}",
                        blackListRequest.getCardId(), blackListRequest.getThirdUserId(),
                        blackListResult != null ? blackListResult.getRetCode() : "null",
                        blackListResult != null ? blackListResult.getRetMsg() : "null");
                return;
            }
            log.info("支付宝支付申请失败,添加黑名单完成, cardId={}, retCode={}, retMsg={}",
                    blackListRequest.getCardId(), blackListResult.getRetCode(), blackListResult.getRetMsg());
        } catch (Exception e) {
            log.error("支付宝支付申请失败,添加黑名单异常, cardId={}", signInfo.getCardId(), e);
        }
    }
}
