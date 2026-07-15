package com.chinasofti.huateng.fep.app.service;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveBlackListFromItpReqDTO;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestIndustryDataResult;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.app.RequestLineCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestLineCodeListResult;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionReqDTO;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionResult;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestStationCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestStationCodeListResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import com.chinasofti.huateng.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;

/**
 * @author zzm
 * @date 2026/5/25 11:54
 */
public interface AppService {


    /**
     * IF8A-01 请求开户。
     *
     * @param request 请求开户参数
     * @return 请求开户结果
    */
    RequestApplicationResult userRegister(RequestApplicationReqDTO request);

    /**
     * IF8A-02 请求同步密钥。
     *
     * <p>APP 在开户后调用该接口获取本地保存所需的用户 SM2 私钥密文、
     * 用户公钥、CA签名数据和 CA 索引。</p>
     *
     * @param request 请求同步密钥参数
     * @return 请求同步密钥结果
     */
    RequestKeyListResult requestKeyList(RequestKeyListReqDTO request);

    /**
     * IF8A-23 请求添加支付通道。
     *
     * @param request 请求添加支付通道参数
     * @return 请求添加支付通道结果
     */
    RequestAddPayChannelResult requestAddPayChannel(RequestAddPayChannelReqDTO request);

    /**
     * IF8A-24 请求设置默认支付通道。
     *
     * @param request 请求设置默认支付通道参数
     * @return 请求设置默认支付通道结果
     */
    RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(RequestSetDefaultPayChannelReqDTO request);

    /**
     * IF8A-03 请求行业数据。
     *
     * @param request 请求行业数据参数
     * @return 请求行业数据结果
     */
    RequestIndustryDataResult requestIndustryData(RequestIndustryDataReqDTO request);

    /**
     * IF8D_03 获取离线码数据。
     *
     * @param request 获取离线码数据参数
     * @return 离线码行业数据
     */
    RequestNoSignalDataResult requestNoSignalData(RequestNoSignalDataReqDTO request);

    /**
     * IF8A-16 请求签约信息。
     *
     * @param request 请求签约信息参数
     * @return 请求签约信息结果
     */
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request);

    /**
     * IF8A-06 请求解约。
     *
     * @param request 请求解约参数
     * @return 请求解约受理结果
     */
    RequestTerminationResult requestTermination(RequestTerminationReqDTO request);

    /**
     * IPD02 接收签约结果通知。
     *
     * @param request 签约结果通知参数
     * @return 签约结果通知处理结果
     */
    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request);

    /**
     * 支付 API 5.1 支付回调处理。
     *
     * @param request 支付回调业务参数
     * @return 支付回调处理结果
     */
    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request);

    /**
     * 支付 API 5.3 解约回调处理。
     *
     * @param request 解约回调业务参数
     * @return 解约回调处理结果
     */
    PaySignCallbackResult receiveTerminationResult(ReceiveTerminationResultReqDTO request);

    /**
     * IF8A-73 查询黑名单。
     *
     * @param request 查询黑名单请求参数
     * @return 查询黑名单结果
     */
    QueryBlackListResult queryBlackList(QueryBlackListReqDTO request);

    /**
     * IF8B-03 接收黑名单结果通知。
     *
     * @param request 黑名单结果通知请求参数
     * @return 通用响应
     */
    CommonResult receiveBlackListFromItp(ReceiveBlackListFromItpReqDTO request);

    /**
     * IF8A-29 查询用户上次行程。
     *
     * @param request 查询用户上次行程请求参数
     * @return 用户行程信息
     */
    QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request);

    RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request);

    RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request);

    RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request);

    RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request);

    /**
     * IF8A-04 请求自助补站。
     *
     * @param request 自助补站请求参数
     * @return 自助补站结果
     */
    RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request);

    /**
     * IF8A-05 请求查询交易记录。
     *
     * @param request 查询交易记录请求参数
     * @return 交易记录列表
     */
    RequestTransListResult requestTransList(RequestTransListReqDTO request);

}
