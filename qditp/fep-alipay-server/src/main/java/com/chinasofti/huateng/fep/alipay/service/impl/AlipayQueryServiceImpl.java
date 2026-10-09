package com.chinasofti.huateng.fep.alipay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.fep.alipay.service.AlipayQueryService;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.transquery.TransQueryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 支付宝出行查询服务实现（薄转发）。
 *
 * <p>{@code findTravelList} / {@code findTravelDetail} 的编排实现已迁入
 * {@code trans-query-server} 的 {@code AlipayTravelQueryHandler}（1.0.5 起），本类只做一次 RPC 转发：
 * 进出站明细拼装、{@code entryId} / {@code exitId} 切片、扣款结果映射全在那边，
 * **NEVER 在本类重新实现一份** —— 那等于两处并存、改一处漏一处。
 *
 * <p>详情应答是 <b>`retCode` / `retMsg` + `data` 三层结构</b>（2026-09-20 按支付宝侧实测要求改回，ADR-D150）：
 * 业务字段在 {@code AlipayTripFindTravelDetailRespDTO.data} 里，本类只把 trans-query 的应答**原样转发**，
 * <b>NEVER 在这里拆平或重新组装字段</b>（那等于把编排搬回接入层）。同日 ADR-D148 的「扁平、NEVER 包 data」
 * 口径**已作废**。
 */
@Service
public class AlipayQueryServiceImpl implements AlipayQueryService {
    private static final Logger log = LoggerFactory.getLogger(AlipayQueryServiceImpl.class);

    private final AlipayPaySignClient alipayPaySignClient;
    private final TransQueryClient transQueryClient;

    @Autowired
    public AlipayQueryServiceImpl(AlipayPaySignClient alipayPaySignClient, TransQueryClient transQueryClient) {
        this.alipayPaySignClient = alipayPaySignClient;
        this.transQueryClient = transQueryClient;
    }

    @Override
    public AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录列表,转发 trans-query-server,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelListRespDTO response;
        try {
            response = transQueryClient.findTravelList(request);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录列表,调用 trans-query-server 异常", e);
            response = null;
        }
        if (response == null) {
            response = new AlipayTripFindTravelListRespDTO();
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-查询乘车记录列表,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,转发 trans-query-server,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelDetailRespDTO response;
        try {
            response = transQueryClient.findTravelDetail(request);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情,调用 trans-query-server 异常, request={}", JSON.toJSONString(request), e);
            response = null;
        }
        if (response == null) {
            response = new AlipayTripFindTravelDetailRespDTO();
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request) {
        log.info("支付宝出行-支付结果查询,请求参数：{}", JSON.toJSONString(request));
        AlipayTripPayQueryRespDTO response = new AlipayTripPayQueryRespDTO();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数：orderNo不能为空");
            log.warn("支付宝出行-支付结果查询,参数校验失败");
            return response;
        }
        try {
            AlipayTripPayQueryRespDTO rpcResponse = alipayPaySignClient.alipayTripPayQuery(request);
            if (rpcResponse == null) {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("系统内部错误：支付签约服务返回空响应");
                log.error("支付宝出行-支付结果查询,alipay-pay-sign-server 返回空响应");
                return response;
            }

            response.setRetCode(rpcResponse.getRetCode());
            response.setRetMsg(rpcResponse.getRetMsg());
            response.setOutTradeNo(rpcResponse.getOutTradeNo());
            response.setPaymentTime(rpcResponse.getPaymentTime());
            response.setTradeStatus(rpcResponse.getTradeStatus());
            response.setTotalAmount(rpcResponse.getTotalAmount());
            response.setTradeNo(rpcResponse.getTradeNo());
            response.setTradeDesc(rpcResponse.getTradeDesc());
            log.info("支付宝出行-支付结果查询,alipay-pay-sign-server 响应：{}", JSON.toJSONString(rpcResponse));
        } catch (Exception e) {
            log.error("支付宝出行-支付结果查询 异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误：" + e.getMessage());
        }
        log.info("支付宝出行-支付结果查询,响应结果：{}", JSON.toJSONString(response));
        return response;
    }
}
