package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.para.entity.ParaVersion;
import com.chinasofti.huateng.para.mapper.AppParaQueryMapper;
import com.chinasofti.huateng.para.mapper.ParaVersionMapper;
import com.chinasofti.huateng.para.service.AppParaService;
import com.chinasofti.huateng.para.service.SingleTicketPurchaseLimitService;
import com.chinasofti.huateng.para.entity.ticket.SingleTicketPurchaseLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class AppParaServiceImpl implements AppParaService {
    private static final Logger log = LoggerFactory.getLogger(AppParaServiceImpl.class);
    private static final String SUCCESS = "0000";
    private static final String FAIL = "9999";

    private final AppParaQueryMapper appParaQueryMapper;
    private final ParaVersionMapper paraVersionMapper;
    private final SingleTicketPurchaseLimitService singleTicketPurchaseLimitService;

    public AppParaServiceImpl(AppParaQueryMapper appParaQueryMapper,
                              ParaVersionMapper paraVersionMapper,
                              SingleTicketPurchaseLimitService singleTicketPurchaseLimitService) {
        this.appParaQueryMapper = appParaQueryMapper;
        this.paraVersionMapper = paraVersionMapper;
        this.singleTicketPurchaseLimitService = singleTicketPurchaseLimitService;
    }


    @Override
    public RequestBuySinlgeTicketMaxNumResult requestBuySinlgeTicketMaxNum() {
        RequestBuySinlgeTicketMaxNumResult result = new RequestBuySinlgeTicketMaxNumResult();
        SingleTicketPurchaseLimit limit = singleTicketPurchaseLimitService.getCurrent().getData();
        if (limit == null || limit.getMaxPurchaseQuantity() == null) {
            result.setRetCode(FAIL);
            result.setRetMsg("未配置单程票最大购买张数");
            return result;
        }
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setBuySinlgeTicketMaxNum(String.valueOf(limit.getMaxPurchaseQuantity()));
        return result;
    }


    @Override
    public RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request) {
        RequestLineCodeListResult result = new RequestLineCodeListResult();
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setLineCodeRecord(appParaQueryMapper.selectLineCodeList());
        return result;
    }


    @Override
    public RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request) {
        RequestStationCodeListResult result = new RequestStationCodeListResult();
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setStationCodeRecord(appParaQueryMapper.selectStationCodeList(request == null ? null : request.getLineCode()));
        return result;
    }


    @Override
    public RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request) {
        RequestTicketPriceByStationResult result = new RequestTicketPriceByStationResult();
        if (request == null || isBlank(request.getEntryStationCode()) || isBlank(request.getExitStationCode())) {
            log.warn("IF8A-10 计算票价参数异常, request={}", request);
            result.setRetCode(FAIL);
            result.setRetMsg("起点站点代码或终点站点代码为空");
            return result;
        }
        log.info("IF8A-10 计算票价, entry={}, exit={}", request.getEntryStationCode(), request.getExitStationCode());
        Integer fareTier = appParaQueryMapper.selectFareTier(request.getEntryStationCode(), request.getExitStationCode());
        log.info("IF8A-10 查询费率等级, entry={}, exit={}, fareTier={}", request.getEntryStationCode(), request.getExitStationCode(), fareTier);
        if (fareTier == null) {
            result.setRetCode(FAIL);
            result.setRetMsg("未找到站点对应费率等级");
            return result;
        }
        Integer ticketPrice = appParaQueryMapper.selectTicketPrice(fareTier);
        log.info("IF8A-10 查询基础票价, fareTier={}, ticketPrice={}", fareTier, ticketPrice);
        if (ticketPrice == null) {
            result.setRetCode(FAIL);
            result.setRetMsg("未找到费率等级对应票价");
            return result;
        }
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setTicketPrice(String.valueOf(ticketPrice));
        log.info("IF8A-10 计算票价成功, entry={}, exit={}, fareTier={}, ticketPrice={}",
                request.getEntryStationCode(), request.getExitStationCode(), fareTier, ticketPrice);
        return result;
    }


    @Override
    public RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request) {
        RequestLineStationCodeVersionResult result = new RequestLineStationCodeVersionResult();
        ParaVersion version = paraVersionMapper.selectByParaType("0001");
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        if (version != null) {
            String versionNo = String.valueOf(version.getCurrentVerNo());
            result.setLineCodeIdx(versionNo);
            result.setStationCodeIdx(versionNo);
            result.setChangeDate(version.getValidDateTime());
        }
        return result;
    }


    @Override
    public RequestStationNameResult requestStationName(RequestStationNameReqDTO request) {
        RequestStationNameResult result = new RequestStationNameResult();
        if (request == null || isBlank(request.getStationCode())) {
            result.setRetCode(FAIL);
            result.setRetMsg("车站代码为空");
            return result;
        }
        String stationName = appParaQueryMapper.selectStationName(request.getStationCode());
        if (isBlank(stationName)) {
            result.setRetCode(FAIL);
            result.setRetMsg("未找到车站名称");
            result.setStationCode(request.getStationCode());
            return result;
        }
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setStationCode(request.getStationCode());
        result.setStationName(stationName);
        return result;
    }


    @Override
    public RequestStationLineInfoResult requestStationLineInfo(RequestStationLineInfoReqDTO request) {
        RequestStationLineInfoResult result = new RequestStationLineInfoResult();
        if (request == null || isBlank(request.getStationCode())) {
            result.setRetCode(FAIL);
            result.setRetMsg("车站代码为空");
            return result;
        }
        RequestStationLineInfoDTO dto = appParaQueryMapper.selectStationLineInfo(request.getStationCode());
        if (dto == null || isBlank(dto.getStationName())) {
            result.setRetCode(FAIL);
            result.setRetMsg("未找到车站信息");
            result.setStationCode(request.getStationCode());
            return result;
        }
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setStationCode(dto.getStationCode());
        result.setStationName(dto.getStationName());
        result.setLineCode(dto.getLineCode());
        result.setLineName(dto.getLineName());
        return result;
    }


    @Override
    public RequestStationNameBatchResult requestStationNameBatch(RequestStationNameBatchReqDTO request) {
        RequestStationNameBatchResult result = new RequestStationNameBatchResult();
        if (request == null || request.getStationCodes() == null || request.getStationCodes().isEmpty()) {
            result.setRetCode(FAIL);
            result.setRetMsg("车站代码列表为空");
            return result;
        }
        List<RequestStationNameResult> list = appParaQueryMapper.selectStationNameBatch(request.getStationCodes());
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setStationNameList(list);
        return result;
    }


    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
