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

/**
 * APP 参数查询服务实现。
 *
 * <p>线路、车站数据取当前路网参数版本；票价先按进出站查询费率等级，
 * 再根据基础费率表取对应票价。</p>
 */
@Service
public class AppParaServiceImpl implements AppParaService {
    private static final Logger log = LoggerFactory.getLogger(AppParaServiceImpl.class);
    /** 接口处理成功返回码。 */
    private static final String SUCCESS = "0000";
    /** 参数校验或参数数据缺失时的通用失败返回码。 */
    private static final String FAIL = "9999";

    /** APP 参数查询 Mapper。 */
    private final AppParaQueryMapper appParaQueryMapper;
    /** 参数版本 Mapper，用于读取当前已入库参数版本。 */
    private final ParaVersionMapper paraVersionMapper;
    /** 单程票购买上限参数服务。 */
    private final SingleTicketPurchaseLimitService singleTicketPurchaseLimitService;

    public AppParaServiceImpl(AppParaQueryMapper appParaQueryMapper,
                              ParaVersionMapper paraVersionMapper,
                              SingleTicketPurchaseLimitService singleTicketPurchaseLimitService) {
        this.appParaQueryMapper = appParaQueryMapper;
        this.paraVersionMapper = paraVersionMapper;
        this.singleTicketPurchaseLimitService = singleTicketPurchaseLimitService;
    }

    /**
     * 查询当前生效的单程票购买上限。
     */
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

    /**
     * 查询当前生效路网版本下的线路代码列表。
     */
    @Override
    public RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request) {
        // 组装 APP 接口标准返回对象。
        RequestLineCodeListResult result = new RequestLineCodeListResult();
        // 查询类接口只要查询过程正常，即返回成功码。
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        // 线路数据取 TBL_PARA_VERSION 中路网参数的当前版本。
        result.setLineCodeRecord(appParaQueryMapper.selectLineCodeList());
        return result;
    }

    /**
     * 查询当前生效路网版本下的车站代码列表。
     */
    @Override
    public RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request) {
        // 组装 APP 接口标准返回对象。
        RequestStationCodeListResult result = new RequestStationCodeListResult();
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        // request 为空时按全部线路查询；lineCode 有值时只查指定线路车站。
        result.setStationCodeRecord(appParaQueryMapper.selectStationCodeList(request == null ? null : request.getLineCode()));
        return result;
    }

    /**
     * 根据进出站查询费率等级，并按默认 FARE_TYPE = 0 换算为基础票价。
     */
    @Override
    public RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request) {
        // 组装 APP 接口标准返回对象。
        RequestTicketPriceByStationResult result = new RequestTicketPriceByStationResult();
        // 进出站是计算票价的必要条件，缺任意一个都无法继续查询费率矩阵。
        if (request == null || isBlank(request.getEntryStationCode()) || isBlank(request.getExitStationCode())) {
            log.warn("IF8A-10 计算票价参数异常, request={}", request);
            result.setRetCode(FAIL);
            result.setRetMsg("起点站点代码或终点站点代码为空");
            return result;
        }
        log.info("IF8A-10 计算票价, entry={}, exit={}", request.getEntryStationCode(), request.getExitStationCode());
        // 先根据进出站代码查询当前费率参数版本中的费率等级。
        Integer fareTier = appParaQueryMapper.selectFareTier(request.getEntryStationCode(), request.getExitStationCode());
        log.info("IF8A-10 查询费率等级, entry={}, exit={}, fareTier={}", request.getEntryStationCode(), request.getExitStationCode(), fareTier);
        if (fareTier == null) {
            result.setRetCode(FAIL);
            result.setRetMsg("未找到站点对应费率等级");
            return result;
        }
        // 再根据费率等级和默认 FARE_TYPE = 0 查询基础费率表中的票价。
        Integer ticketPrice = appParaQueryMapper.selectTicketPrice(fareTier);
        log.info("IF8A-10 查询基础票价, fareTier={}, ticketPrice={}", fareTier, ticketPrice);
        if (ticketPrice == null) {
            result.setRetCode(FAIL);
            result.setRetMsg("未找到费率等级对应票价");
            return result;
        }
        // 数据完整时返回成功，并按 APP 接口字段类型转成字符串。
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        result.setTicketPrice(String.valueOf(ticketPrice));
        log.info("IF8A-10 计算票价成功, entry={}, exit={}, fareTier={}, ticketPrice={}",
                request.getEntryStationCode(), request.getExitStationCode(), fareTier, ticketPrice);
        return result;
    }

    /**
     * 线路和车站代码同属路网参数文件，当前使用路网参数版本作为二者版本号。
     */
    @Override
    public RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request) {
        // 组装 APP 接口标准返回对象。
        RequestLineStationCodeVersionResult result = new RequestLineStationCodeVersionResult();
        // 0001 是路网拓扑参数类型，线路和车站代码都来自该参数文件。
        ParaVersion version = paraVersionMapper.selectByParaType("0001");
        result.setRetCode(SUCCESS);
        result.setRetMsg("成功");
        // 如果尚未导入路网参数，返回成功但版本字段为空，避免查询接口异常。
        if (version != null) {
            // APP 接口要求线路版本和车站版本分别返回，这里二者共用当前路网版本号。
            String versionNo = String.valueOf(version.getCurrentVerNo());
            result.setLineCodeIdx(versionNo);
            result.setStationCodeIdx(versionNo);
            // 版本变更日期取参数文件头中的生效时间。
            result.setChangeDate(version.getValidDateTime());
        }
        return result;
    }

    /**
     * 根据车站代码查询车站名称。
     */
    @Override
    public RequestStationNameResult requestStationName(RequestStationNameReqDTO request) {
        // 组装 APP/内部 RPC 通用返回对象。
        RequestStationNameResult result = new RequestStationNameResult();
        if (request == null || isBlank(request.getStationCode())) {
            result.setRetCode(FAIL);
            result.setRetMsg("车站代码为空");
            return result;
        }
        // 从当前路网参数版本查询站名。
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

    /**
     * 根据车站代码查询车站名称及所属线路信息。
     */
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

    /**
     * 批量根据车站代码查询车站名称。
     */
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

    /**
     * 判断字符串是否为空。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
