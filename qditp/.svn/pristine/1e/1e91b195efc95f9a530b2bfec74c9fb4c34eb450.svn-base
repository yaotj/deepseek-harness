package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPushTransDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPushTransDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveCardDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveCardDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;

/**
 * 支付宝出行 Trip 服务接口。
 */
public interface AlipayTripService {

    /**
     * 添加签约信息。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);

    /**
     * 解约登记。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);

    /**
     * 开卡申请。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request);

    /**
     * 获取行业数据。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripRequestIndustryDataRespDTO requestIndustryData(AlipayTripRequestIndustryDataReqDTO request);

    /**
     * 查询乘车记录列表。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request);

    /**
     * 查询乘车记录详情。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request);

    /**
     * 支付申请。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request);

    /**
     * 退款申请。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request);

    /**
     * 支付结果查询。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request);

    /**
     * 行程数据推送。
     * <p>
     * ITP 主动推送给支付宝，由 ticket-server / online-server 在行程完成后调用。
     * </p>
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripPushTransDataRespDTO pushTransData(AlipayTripPushTransDataReqDTO request);

    /**
     * 业务关闭结果通知。
     * <p>
     * ITP 主动推送给支付宝，由 pay-sign-server 在解约完成后调用。
     * </p>
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripCloseResultRespDTO closeResultForAlipay(AlipayTripCloseResultReqDTO request);

    /**
     * 行业数据推送。
     * <p>
     * ITP 主动推送给支付宝，由 account-server / security-server 在行业数据更新后调用。
     * </p>
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripReceiveCardDataRespDTO receiveCardDataFromItp(AlipayTripReceiveCardDataReqDTO request);

    /**
     * 黑名单状态变更通知。
     * <p>
     * ITP 主动推送给支付宝，由 blacklist-server 在黑名单状态变更后调用。
     * </p>
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripReceiveBlackListRespDTO receiveBlackListFromItp(AlipayTripReceiveBlackListReqDTO request);

    /**
     * 支付结果回调。
     * <p>
     * 支付宝支付完成后回调该接口，更新支付状态。
     * </p>
     *
     * @param request 请求对象
     * @return 通用响应
     */
    AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request);
}
