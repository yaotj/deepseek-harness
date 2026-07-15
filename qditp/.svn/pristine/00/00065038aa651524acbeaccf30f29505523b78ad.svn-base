package com.chinasofti.huateng.wallet.service.impl;

import com.chinasofti.huateng.wallet.constant.WalletErrorCodeEnum;
import com.chinasofti.huateng.wallet.model.contract.RequestContractResultReqDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestContractResultRespDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestTerminationReqDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestTerminationRespDTO;
import com.chinasofti.huateng.wallet.service.WalletContractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WalletContractServiceImpl implements WalletContractService {
    private static final Logger log = LoggerFactory.getLogger(WalletContractServiceImpl.class);
    private static final String STATUS_NOT_SIGNED = "NOT_SIGNED";
    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_UNSIGNED = "UNSIGNED";
    private final Map<String, ContractRecord> contractRecordMap = new ConcurrentHashMap<>();

    @Override
    public RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request) {
        try {
            log.info("开始处理签约结果咨询, request={}", request);
            RequestContractResultRespDTO response = new RequestContractResultRespDTO();
            String validMsg = validateRequestContractResult(request);
            if (validMsg != null) {
                response.setRetCode(WalletErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }
            String paymentVendor = request.getPaymentVendor().trim();
            if (!isSupportedPaymentVendor(paymentVendor)) {
                response.setRetCode(WalletErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("paymentVendor无效");
                return response;
            }

            String key = buildContractKey(request.getThirdUserId(), request.getRequestSignSeq());
            ContractRecord record = contractRecordMap.computeIfAbsent(key, k -> {
                ContractRecord newRecord = new ContractRecord();
                newRecord.setThirdUserId(request.getThirdUserId().trim());
                newRecord.setRequestSignSeq(request.getRequestSignSeq().trim());
                newRecord.setPaymentVendor(paymentVendor);
                newRecord.setStatus(STATUS_SIGNED);
                newRecord.setPayAccountId(request.getThirdUserId().trim());
                newRecord.setPayAgreementNo(request.getRequestSignSeq().trim());
                return newRecord;
            });

            response.setRetCode(WalletErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(WalletErrorCodeEnum.SUCCESS.getMsg());
            response.setStatus(record.getStatus());
            response.setPayAccountId(record.getPayAccountId());
            response.setPayAgreementNo(record.getPayAgreementNo());
            return response;
        } catch (Exception e) {
            log.error("处理签约结果咨询异常", e);
            RequestContractResultRespDTO response = new RequestContractResultRespDTO();
            response.setRetCode(WalletErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(WalletErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request) {
        try {
            log.info("开始处理请求解约, request={}", request);
            RequestTerminationRespDTO response = new RequestTerminationRespDTO();
            String validMsg = validateRequestTermination(request);
            if (validMsg != null) {
                response.setRetCode(WalletErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }

            String key = buildContractKey(request.getThirdUserId(), request.getRequestSignSeq());
            ContractRecord record = contractRecordMap.get(key);
            if (record == null) {
                response.setRetCode(WalletErrorCodeEnum.USER_NOT_SIGNED.getCode());
                response.setRetMsg(WalletErrorCodeEnum.USER_NOT_SIGNED.getMsg());
                return response;
            }

            record.setStatus(STATUS_UNSIGNED);
            response.setRetCode(WalletErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(WalletErrorCodeEnum.SUCCESS.getMsg());
            return response;
        } catch (Exception e) {
            log.error("处理请求解约异常", e);
            RequestTerminationRespDTO response = new RequestTerminationRespDTO();
            response.setRetCode(WalletErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(WalletErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 校验签约结果咨询必输参数。
     */
    private String validateRequestContractResult(RequestContractResultReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return "paymentVendor不能为空";
        }
        return null;
    }

    /**
     * 校验请求解约必输参数。
     */
    private String validateRequestTermination(RequestTerminationReqDTO request) {
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
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        return null;
    }

    /**
     * 校验文档定义的支付类型。
     */
    private boolean isSupportedPaymentVendor(String paymentVendor) {
        return "03".equals(paymentVendor)
                || "04".equals(paymentVendor)
                || "06".equals(paymentVendor)
                || "07".equals(paymentVendor)
                || "0B".equalsIgnoreCase(paymentVendor);
    }

    private String buildContractKey(String thirdUserId, String requestSignSeq) {
        return thirdUserId.trim() + "|" + requestSignSeq.trim();
    }

    /**
     * 轻量内存签约记录。
     */
    private static class ContractRecord {
        /**
         * 第三方用户编码。
         */
        private String thirdUserId;

        /**
         * 签约请求流水号。
         */
        private String requestSignSeq;

        /**
         * 支付类型。
         */
        private String paymentVendor;

        /**
         * 签约状态。
         */
        private String status = STATUS_NOT_SIGNED;

        /**
         * 支付用户编码。
         */
        private String payAccountId;

        /**
         * 支付渠道签约流水号。
         */
        private String payAgreementNo;

        public String getThirdUserId() {
            return thirdUserId;
        }

        public void setThirdUserId(String thirdUserId) {
            this.thirdUserId = thirdUserId;
        }

        public String getRequestSignSeq() {
            return requestSignSeq;
        }

        public void setRequestSignSeq(String requestSignSeq) {
            this.requestSignSeq = requestSignSeq;
        }

        public String getPaymentVendor() {
            return paymentVendor;
        }

        public void setPaymentVendor(String paymentVendor) {
            this.paymentVendor = paymentVendor;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getPayAccountId() {
            return payAccountId;
        }

        public void setPayAccountId(String payAccountId) {
            this.payAccountId = payAccountId;
        }

        public String getPayAgreementNo() {
            return payAgreementNo;
        }

        public void setPayAgreementNo(String payAgreementNo) {
            this.payAgreementNo = payAgreementNo;
        }
    }
}
