package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.model.app.*;


public interface AppParaService {

    RequestBuySinlgeTicketMaxNumResult requestBuySinlgeTicketMaxNum();


    RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request);


    RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request);


    RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request);


    RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request);


    RequestStationNameResult requestStationName(RequestStationNameReqDTO request);


    RequestStationLineInfoResult requestStationLineInfo(RequestStationLineInfoReqDTO request);


    RequestStationNameBatchResult requestStationNameBatch(RequestStationNameBatchReqDTO request);
}
