package com.chinasofti.huateng.fep.app.service;

import com.chinasofti.huateng.model.alipaytrip.*;

/**
 * 支付宝出行专属接口服务。
 * 与现有 IF8A 接口体系独立，单独承接支付宝出行侧的业务请求。
 */
public interface AlipayTripService {

    /**
     * 1.1 支付宝出行-添加签约信息。
     *
     * @param request 添加签约信息请求参数
     * @return 添加签约信息响应参数
     */
    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);

    /**
     * 1.2 支付宝出行-解约登记。
     *
     * @param request 解约登记请求参数
     * @return 解约登记响应参数
     */
    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);

    /**
     * 1.3 支付宝出行-开卡申请。
     *
     * @param request 开卡申请请求参数
     * @return 开卡申请响应参数
     */
    AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request);

    /**
     * 1.4 支付宝出行-获取行业数据。
     *
     * @param request 获取行业数据请求参数
     * @return 获取行业数据响应参数
     */
    AlipayTripRequestIndustryDataRespDTO requestIndustryData(AlipayTripRequestIndustryDataReqDTO request);

    /**
     * 1.5 支付宝出行-查询乘车记录。
     *
     * @param request 查询乘车记录请求参数
     * @return 查询乘车记录响应参数
     */
    AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request);

    /**
     * 1.6 支付宝出行-查询乘车记录详情。
     *
     * @param request 查询乘车记录详情请求参数
     * @return 查询乘车记录详情响应参数
     */
    AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request);

    /**
     * 1.7 支付宝出行-业务关闭结果通知。
     *
     * @param request 业务关闭结果通知请求参数
     * @return 业务关闭结果通知响应参数
     */
    AlipayTripCloseResultRespDTO closeResultForAlipay(AlipayTripCloseResultReqDTO request);

    /**
     * 1.8 支付宝出行-行程数据推送。
     *
     * @param request 行程数据推送请求参数
     * @return 行程数据推送响应参数
     */
    AlipayTripPushTransDataRespDTO pushTransData(AlipayTripPushTransDataReqDTO request);

    /**
     * 1.9 支付宝出行-行业数据推送。
     *
     * @param request 行业数据推送请求参数
     * @return 行业数据推送响应参数
     */
    AlipayTripReceiveCardDataRespDTO receiveCardDataFromItp(AlipayTripReceiveCardDataReqDTO request);

    /**
     * 1.10 支付宝出行-黑名单状态变更通知。
     *
     * @param request 黑名单状态变更通知请求参数
     * @return 黑名单状态变更通知响应参数
     */
    AlipayTripReceiveBlackListRespDTO receiveBlackListFromItp(AlipayTripReceiveBlackListReqDTO request);
}
