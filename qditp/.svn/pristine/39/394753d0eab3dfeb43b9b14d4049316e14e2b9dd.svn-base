package com.chinasofti.huateng.collectpay.controller.ci.tvm;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.constant.BomPayCodeEnum;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;
import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.*;
import com.chinasofti.huateng.collectpay.model.response.tvm.NotiTakeTicketFailResultRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.NotiTakeTicketResultRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.model.response.tvm.RequestPayResultRespDTO;
import com.chinasofti.huateng.collectpay.service.TvmOrderPreService;
import com.chinasofti.huateng.collectpay.service.TvmOrderService;
import com.chinasofti.huateng.collectpay.service.TvmTakeTicketService;
import com.chinasofti.huateng.collectpay.service.TvmTopupService;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * TVM扫码购票接口控制器。
 */
@RestController
@RequestMapping("/ci/tvm")
public class TvmOrderController {
    private static final Logger log = LoggerFactory.getLogger(TvmOrderController.class);

    @Autowired
    private TvmOrderService tvmOrderService;
    @Autowired
    private TvmOrderPreService tvmOrderPreService;
    @Autowired
    private TvmTakeTicketService tvmTakeTicketService;
    @Autowired
    private TvmTopupService tvmTopupService;

    /**
     * 将公共参数复制到业务请求DTO中。
     */
    private <T extends BaseRequestDTO> T copyBaseParams(BaseRequestDTO baseRequest, Class<T> clazz) {
        T request = JSON.parseObject(baseRequest.getBizData(), clazz);
        request.setProviderId(baseRequest.getProviderId());
        request.setCharset(baseRequest.getCharset());
        request.setFormat(baseRequest.getFormat());
        request.setTimestamp(baseRequest.getTimestamp());
        request.setDeviceId(baseRequest.getDeviceId());
        request.setSignType(baseRequest.getSignType());
        request.setSign(baseRequest.getSign());
        request.setBizData(baseRequest.getBizData());
        return request;
    }


    /**--------------------------------- 扫码购票 ---------------------------------------**/

    /**
     * IF2A-01 提交单程票订单。
     * 接口地址：/ci/tvm/requestGenSjtOrder
     */
    @PostMapping("/requestGenSjtOrder")
    public JSONObject requestGenSjtOrder(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到提交单程票订单请求, baseRequest={}", baseRequest);

        RequestGenSjtOrderReqDTO request = copyBaseParams(baseRequest, RequestGenSjtOrderReqDTO.class);

        log.info("转换后请求参数为 {}",request);
        // 校验参数
        String validMsg = validateRequestGenSjtOrder(request);
        if (validMsg != null) {
            log.info("参数校验失败 validMsg is {}",validMsg);
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),validMsg);
        }


        return tvmOrderService.requestTvmPayOrder(request);
    }

    private String validateRequestGenSjtOrder(RequestGenSjtOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getTicketPrice())) {
            return "ticketPrice不能为空";
        }
        if (!StringUtils.hasText(request.getSingelTicketNum())) {
            return "singelTicketNum不能为空";
        }
        if (!StringUtils.hasText(request.getSingleTicketType())) {
            return "singleTicketType不能为空";
        }
        if ("0".equals(request.getSingleTicketType())) {
            if (!StringUtils.hasText(request.getEntryStationCode())) {
                return "按站点购票时entryStationCode不能为空";
            }
            if (!StringUtils.hasText(request.getExitStationCode())) {
                return "按站点购票时exitStationCode不能为空";
            }
        }
        return null;
    }

    /**
     * IF2A-03 查询支付结果。
     * 接口地址：/ci/tvm/requestPayResult
     */
    @PostMapping("/requestPayResult")
    public JSONObject requestPayResult(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到查询支付结果请求, baseRequest={}", baseRequest);
        RequestPayResultReqDTO request = copyBaseParams(baseRequest, RequestPayResultReqDTO.class);

        if (request == null || StringUtils.isEmpty(request.getOrderNo())) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),"orderNo不能为空");
        }

        return tvmOrderPreService.requestPayResult(request);
    }


    /**
     * IF2A-04 出票结果通知。
     * 接口地址：/ci/tvm/notiTakeTicketResult
     */
    @PostMapping("/notiTakeTicketResult")
    public JSONObject notiTakeTicketResult(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到出票结果通知, baseRequest={}", baseRequest);
        NotiTakeTicketResultReqDTO request = copyBaseParams(baseRequest, NotiTakeTicketResultReqDTO.class);
        // 参数校验
        if (request == null || StringUtils.isEmpty(request.getOrderNo())) {
            log.info("参数校验失败，orderNo为空");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),"orderNo不能为空");
        }
        return tvmOrderService.notiTakeTicketResult(request);
    }

    /**
     * IF2A-05 出票故障通知。
     * 接口地址：/ci/tvm/notiTakeTicketFailResult
     */
    @PostMapping("/notiTakeTicketFailResult")
    public JSONObject notiTakeTicketFailResult(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到出票故障通知, baseRequest={}", baseRequest);
        NotiTakeTicketFailResultReqDTO request = copyBaseParams(baseRequest, NotiTakeTicketFailResultReqDTO.class);
        if (request == null || StringUtils.isEmpty(request.getOrderNo())) {
            log.info("参数校验失败，orderNo为空");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),"orderNo不能为空");
        }
        return tvmOrderService.notiTakeTicketFailResult(request);
    }

/**--------------------------------- 扫码取票 ---------------------------------------**/

    /**
     * IF8A-15 激活取票订单。
     * 手机扫码TVM二维码后调用此接口激活取票订单。
     *
     * @return 响应结果
     */
    @PostMapping("/requestActiveTicket")
    public JSONObject requestActiveTicket(@RequestBody BaseRequestDTO baseRequest) {
        RequestActiveTicketReqDTO request = copyBaseParams(baseRequest, RequestActiveTicketReqDTO.class);
        if(ObjectUtils.isEmpty(request) || StringUtils.isEmpty(request.getOrderNo())|| StringUtils.isEmpty(request.getDeviceId())|| StringUtils.isEmpty(request.getQrcodeGenDate())|| StringUtils.isEmpty(request.getRandomFact())){
            log.info("参数校验失败");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),TvmPayCodeEnum.INVALID_PARAM.getMsg());
        }
        return tvmTakeTicketService.requestActiveTicket(request);
    }

    /**
     * IF2A-08 扫码取票订单查询。
     * TVM轮询查询激活状态。
     *
     * @return 响应结果
     */
    @PostMapping("/requestTakeTicketAuth")
    public JSONObject requestTakeTicketAuth(@RequestBody BaseRequestDTO baseRequest) {
        RequestTakeTicketAuthReqDTO request = copyBaseParams(baseRequest, RequestTakeTicketAuthReqDTO.class);
        if(ObjectUtils.isEmpty(request) || StringUtils.isEmpty(request.getDeviceId())|| StringUtils.isEmpty(request.getQrcodeGenDate())|| StringUtils.isEmpty(request.getRandomFact())){
            log.info("参数校验失败");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),TvmPayCodeEnum.INVALID_PARAM.getMsg());
        }
        return tvmTakeTicketService.requestTakeTicketAuth(request);
    }

    /**--------------------------------- 扫码支付 ---------------------------------------**/

    /**
     * IF2A-09 请求充值下单。
     * TVM向ITP平台发起充值下单请求。
     *
     * @return 响应结果
     */
    @PostMapping("/requestTopup")
    public JSONObject requestTopup(@RequestBody BaseRequestDTO baseRequest) {
        RequestTopupReqDTO request = copyBaseParams(baseRequest, RequestTopupReqDTO.class);
        if(ObjectUtils.isEmpty(request) || StringUtils.isEmpty(request.getTicketPhysicsNum())|| StringUtils.isEmpty(request.getTicketLogicNum())|| StringUtils.isEmpty(request.getBeforeAmount())|| StringUtils.isEmpty(request.getTransAmount())){
            log.info("参数校验失败");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),TvmPayCodeEnum.INVALID_PARAM.getMsg());
        }

        return tvmTopupService.requestTopup(request);
    }

    /**
     * IF2A-06 充值结果通知。
     * TVM充值成功后通知ITP平台。
     *
     * @return 响应结果
     */
    @PostMapping("/topupCardResultNoti")
    public JSONObject topupCardResultNoti(@RequestBody TopupCardResultNotiReqDTO  baseRequest) {
        TopupCardResultNotiReqDTO request = copyBaseParams(baseRequest, TopupCardResultNotiReqDTO.class);
        if(ObjectUtils.isEmpty(request) || StringUtils.isEmpty(request.getOrderNo())|| StringUtils.isEmpty(request.getTicketLogicNum())|| StringUtils.isEmpty(request.getTicketPhysicsNum())){
            log.info("参数校验失败");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),TvmPayCodeEnum.INVALID_PARAM.getMsg());
        }
        return tvmTopupService.topupCardResultNoti(request);
    }

    /**
     * IF2A-07 充值失败通知。
     * TVM充值失败后通知ITP平台。
     *
     * @return 响应结果
     */
    @PostMapping("/topupCardFailNoti")
    public JSONObject topupCardFailNoti(@RequestBody TopupCardFailNotiReqDTO baseRequest) {

        TopupCardFailNotiReqDTO request = copyBaseParams(baseRequest, TopupCardFailNotiReqDTO.class);
        if(ObjectUtils.isEmpty(request) || StringUtils.isEmpty(request.getOrderNo())|| StringUtils.isEmpty(request.getTicketLogicNum())|| StringUtils.isEmpty(request.getTicketPhysicsNum())){
            log.info("参数校验失败");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(),TvmPayCodeEnum.INVALID_PARAM.getMsg());
        }

        return tvmTopupService.topupCardFailNoti(request);
    }
}
