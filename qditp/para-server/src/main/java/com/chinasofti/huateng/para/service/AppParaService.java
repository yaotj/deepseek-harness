package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.model.app.*;

/**
 * APP 线路、车站、票价参数查询服务。
 */
public interface AppParaService {
    /**
     * IF8A-09 获取单次购买单程票最大张数。
     *
     * @return 当前单程票购买上限
     */
    RequestBuySinlgeTicketMaxNumResult requestBuySinlgeTicketMaxNum();

    /**
     * IF8A-07 获取线路代码。
     *
     * @param request 请求参数，当前无业务字段
     * @return 当前生效线路代码列表
     */
    RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request);

    /**
     * IF8A-08 获取车站代码。
     *
     * @param request 请求参数，可按线路代码过滤
     * @return 当前生效车站代码列表
     */
    RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request);

    /**
     * IF8A-10 根据进出站计算票价。
     *
     * @param request 进站车站代码、出站车站代码
     * @return 票价计算结果
     */
    RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request);

    /**
     * IF8A-17 获取线路、车站代码版本。
     *
     * @param request 请求参数，当前无业务字段
     * @return 当前路网参数版本信息
     */
    RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request);

    /**
     * 根据车站代码查询车站名称。
     *
     * @param request 车站代码
     * @return 当前路网参数版本中的车站中文名称
     */
    RequestStationNameResult requestStationName(RequestStationNameReqDTO request);

    /**
     * 根据车站代码查询车站名称及所属线路信息。
     *
     * @param request 车站代码
     * @return 车站名称、所属线路代码、线路名称
     */
    RequestStationLineInfoResult requestStationLineInfo(RequestStationLineInfoReqDTO request);

    /**
     * 批量根据车站代码查询车站名称。
     *
     * @param request 车站代码列表
     * @return 车站名称结果列表
     */
    RequestStationNameBatchResult requestStationNameBatch(RequestStationNameBatchReqDTO request);
}
