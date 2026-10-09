package com.chinasofti.huateng.fep.alipay.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultRespDTO;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.fep.alipay.service.AlipayTripService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 支付宝出行 Trip 服务门面实现。 */
@Service
public class AlipayTripServiceImpl implements AlipayTripService {

    private final AlipayContractServiceImpl alipayContractService;
    private final AlipayApplicationServiceImpl alipayApplicationService;
    private final AlipayQueryServiceImpl alipayQueryService;
    private final AlipayPaymentServiceImpl alipayPaymentService;
    private final AlipayNotifyServiceImpl alipayNotifyService;

    @Autowired
    public AlipayTripServiceImpl(AlipayContractServiceImpl alipayContractService,
                                 AlipayApplicationServiceImpl alipayApplicationService,
                                 AlipayQueryServiceImpl alipayQueryService,
                                 AlipayPaymentServiceImpl alipayPaymentService,
                                 AlipayNotifyServiceImpl alipayNotifyService) {
        this.alipayContractService = alipayContractService;
        this.alipayApplicationService = alipayApplicationService;
        this.alipayQueryService = alipayQueryService;
        this.alipayPaymentService = alipayPaymentService;
        this.alipayNotifyService = alipayNotifyService;
    }

    @Override
    public AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request) {
        return alipayContractService.addContract(request);
    }

    @Override
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        return alipayContractService.terminateContract(request);
    }

    @Override
    public AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request) {
        return alipayApplicationService.requestApplication(request);
    }

    @Override
    public AlipayTripRequestIndustryDataRespDTO requestIndustryData(AlipayTripRequestIndustryDataReqDTO request) {
        return alipayApplicationService.requestIndustryData(request);
    }

    @Override
    public AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request) {
        return alipayQueryService.findTravelList(request);
    }

    @Override
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        return alipayQueryService.findTravelDetail(request);
    }

    @Override
    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        return alipayPaymentService.requestRefund(request);
    }

    @Override
    public AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request) {
        return alipayQueryService.payQuery(request);
    }

    @Override
    public AlipayTripPayNotifyRespDTO handlePaymentNotify(AlipayTripPayNotifyReqDTO request) {
        return alipayNotifyService.handlePaymentNotify(request);
    }

    @Override
    public AlipayTripCloseResultRespDTO closeResult(AlipayTripCloseResultReqDTO request) {
        return alipayNotifyService.closeResult(request);
    }

    @Override
    public BlackListOperateResult addBlackListForAlipay(AddBlackListReqDTO request) {
        return alipayPaymentService.addBlackListForAlipay(request);
    }

    @Override
    public com.chinasofti.huateng.model.alipaytrip.TerminationExecuteResult executeTermination(String agreementCode) {
        return alipayPaymentService.executeTermination(agreementCode);
    }
}
