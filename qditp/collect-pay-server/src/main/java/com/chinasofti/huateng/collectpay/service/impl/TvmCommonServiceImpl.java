package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.ItpCommon;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.*;
import com.chinasofti.huateng.collectpay.entity.RefundOrder;
import com.chinasofti.huateng.collectpay.mapper.AppRefundOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.RefundOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmAppOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmNoticeAppMapper;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.app.NoticeAppRefundDTO;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.app.AppOrderResult;
import com.chinasofti.huateng.collectpay.model.response.bom.BomOrderResult;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.service.TvmCommonService;
import com.chinasofti.huateng.collectpay.utils.BaseResult;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import com.chinasofti.huateng.collectpay.utils.HttpUtils;
import com.chinasofti.huateng.collectpay.utils.TransforUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


/** 当面付业务公用方法 */
@Service
@Slf4j
public class TvmCommonServiceImpl implements TvmCommonService {

    @Autowired
    PayCenterCommon payCenterCommon;
    @Autowired
    Environment environment;
    @Autowired
    private PayCenterService payCenterService;
    @Autowired
    private PayCenterProperties payCenterProperties;
    @Autowired
    private RefundOrderMapper refundOrderMapper;
    @Autowired
    private TvmNoticeAppMapper tvmNoticeAppMapper;
    @Autowired
    private HttpUtils httpUtils;
    @Autowired
    private AppRefundOrderMapper appRefundOrderMapper;
    @Autowired
    private TvmAppOrderMapper tvmAppOrderMapper;

    @Resource(name = "tvmexecutor")
    ThreadPoolTaskExecutor executor;

    @Override
    public boolean doRefund(String bussInessType, String orderNo, String payCenterOrderNo, int refundAmount,String refundNo) {

        boolean b = false;

        try {

            log.info("bussInessType is {} orderNo is {}  payCenterOrderNo is {}  refundAmount is {} ", bussInessType, orderNo, payCenterOrderNo, refundAmount);

            String payOrderNo = orderNo;

            PayCenterRequest payCenterRefundRequest = payCenterCommon.getRefundRequest(refundNo, orderNo, payCenterOrderNo, refundAmount);

            log.info("退款开始 payCenterRefundRequest is {}", payCenterRefundRequest);

            // 4. 调用支付中心退款接口
            PayCenterResponse payCenterResponse = payCenterService.callPayCenter(payCenterProperties.getPayCenterRefundUrl(), payCenterRefundRequest);

            log.info("退款结束 payCenterResponse is {}", payCenterResponse);
            // 5. 保存退款记录
            RefundOrder refundOrder = new RefundOrder();
            refundOrder.setRefundNo(refundNo);
            refundOrder.setPayOrderNo(payOrderNo);
            refundOrder.setBusinessType(bussInessType);
            refundOrder.setRefundAmount(refundAmount);
            refundOrder.setRefundReason(environment.getProperty("pay.center.refundReason"));
            refundOrder.setCreateTime(DateUtils.getNowTime());

            if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                log.info("退款成功, payOrderNo={}, refundNo={}, refundAmount={}", payOrderNo, refundNo, refundAmount);

                // 退款成功
                Map<String, Object> data = payCenterResponse.getData();
                refundOrder.setMerchantRefundNo(TransforUtils.getStringFromData(data, "merchantRefundNo"));
                refundOrder.setChannelRefundNo(TransforUtils.getStringFromData(data, "channelRefundNo"));

                String refundTime = TransforUtils.getStringFromData(data, "refundTime");
                // 如果不为空，将格式转为yyyy-MM-dd HH:mm:ss
                if (!StringUtils.isEmpty(refundTime)) {
                    refundTime = getRefundTime(refundTime);
                }
                refundOrder.setRefundTime(refundTime);
                // 实际退款结果需要去查询或者等待支付中心通知
                refundOrder.setRefundStatus(ItpStatusEnum.REFUND_ING.getCode()); // 1-退款中
                refundOrder.setRefundMsg(ItpStatusEnum.REFUND_ING.getDesc()); // 1-退款中

                // 退款后发起退款结果查询
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        BaseResult baseResult = getPayCenterRefundResult( refundNo);
                        log.info("退款查询业务处理结束，baseResult is {}", baseResult);
                        if (baseResult.getErrorCode()==BaseResult.SUCCESS){

                            JSONObject refundResultInfo = JSONObject.parseObject(baseResult.getData().toString());
                            String refundSatus = refundResultInfo.get("status").toString();
                            String refundTime = refundResultInfo.get("refundTime").toString();
                            boolean b1 = dealRefundResult(refundNo, refundSatus, refundTime);
                            log.info("处理扫码购票退款业务结束，b1 is {}",b1);
                        }
                    }
                });

            } else {
                String errorMsg = payCenterResponse != null ? payCenterResponse.getMsg() : "调用支付中心退款失败";
                log.error("退款失败, payOrderNo={}, refundNo={}, errorMsg={}", payOrderNo, refundNo, errorMsg);
                // 退款失败
                refundOrder.setRefundStatus(ItpStatusEnum.REFUNDING_FAIL.getCode()); // 2-退款失败
                refundOrder.setRefundMsg(ItpStatusEnum.REFUNDING_FAIL.getDesc()); // 2-退款失败
            }

            int insert = refundOrderMapper.insert(refundOrder);
            if (insert > 0) {
                log.info("退款记录保存成功");
                // 如果退款记录保存成功
            } else {
                log.info("退款记录保存失败");
            }
            b=true;

        } catch (Exception e) {
            log.error("发起退款异常, payOrderNo={}", orderNo, e);
        }
        return b;
    }

//    @Override
//    public boolean getPayCenterRefundResult(String payOrderNo, String refundNo, String refundAmount, String businessType) {
//        log.info("1.______轮询开始...");
//        // 这里借用bom的时间设置
//        Integer timeOut = Integer.valueOf(environment.getProperty("bom.payTimeOut"));
//        Integer payTimeInterval = Integer.valueOf(environment.getProperty("bom.payTimeInterval"));
//        // 第min秒
//        int min = 0;
//        try {
//            while (min < timeOut) {
//                PayCenterResponse payCenterResponse = this.queryRefundResult(refundNo);
//
//                // 如果查询返回成功
//                if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
//
//                    Map<String, Object> data = payCenterResponse.getData();
//                    String status = TransforUtils.getStringFromData(data, "status");
//                    String refundTime = TransforUtils.getStringFromData(data, "refundTime");
//                    String refundDate = refundTime.substring(0, 8);
//
//                    // 处理业务
//                    boolean refundFlag = false;
//                    // 如果是扫码购票业务，调用dealRefundResult
//                    if (StringUtils.equals(businessType, BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode())||StringUtils.equals(businessType, BusinessTypeEnum.APP_REFUND.getCode())) {
//
//                        refundFlag = dealRefundResult(refundNo, status, refundTime);
//                    }
//                    // 如果是扫码取票或者app主动发起退款，则调用dealAppRefundResult
//                    else if (StringUtils.equals(businessType, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())||StringUtils.equals(businessType, BusinessTypeEnum.APP_REFUND.getCode())){
//                        refundFlag = dealAppRefundResult(payOrderNo, refundNo, status, refundTime);
//                        // 如果处理退款业务成功，并且是扫码取票业务，则通知app 说明：通知业务写在退款逻辑里，是因为只有在获取到准确的退款结果后，才能向app发起退款成功/退款失败的通知
//                        if (refundFlag && StringUtils.equals(businessType, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())) {
//                            int i = saveNoticeAppRefundResultRecord(payOrderNo, refundAmount);
//                            log.info("保存通知app退款记录结束 i is {}", i);
//                            // 这里retryTimes写死为1，因为明确这里是第一次发送
//                            boolean b = noticeAppRefundResult(payOrderNo, status, refundDate, refundAmount, "1");
//                            log.info("通知app退款结束 b is {}", b);
//                        }
//                    }
//
//                    // 如果查到了支付成功或者失败的结果，停止轮询
//                    if (refundFlag) {
//
//                        return true;
//                    }
//
//                } else {
//                    log.info("查询失败，不做处理");
//                }
//                Thread.sleep(payTimeInterval * 1000);
//            }
//
//        } catch (Exception e) {
//            log.error("2.______查询结果异常 e is {}", e);
//            return false;
//        }
//        return false;
//    }

    @Override
    public BaseResult getPayCenterRefundResult(String refundNo) {
        log.info("1.______轮询开始...");
        // 这里借用bom的时间设置
        Integer timeOut = Integer.valueOf(environment.getProperty("bom.payTimeOut"));
        Integer payTimeInterval = Integer.valueOf(environment.getProperty("bom.payTimeInterval"));
        // 第min秒
        int min = 0;
        try {
            while (min < timeOut) {
                PayCenterResponse payCenterResponse = this.queryRefundResult(refundNo);

                // 如果查询返回成功
                if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {

                    Map<String, Object> data = payCenterResponse.getData();
                    String status = TransforUtils.getStringFromData(data, "status");
                    String refundTime = TransforUtils.getStringFromData(data, "refundTime");
                    String refundDate = refundTime.substring(0, 8);

                    JSONObject refundResult = new JSONObject();
                    refundResult.put("status",status);
                    refundResult.put("refundTime",refundTime);
                    refundResult.put("refundDate",refundDate);

                    if (StringUtils.equals(status, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())||StringUtils.equals(status, PayCenterRefundStatusEnum.REFUNDING_FAIL.getCode())) {
                        log.info("查询到退款成功/失败的结果");
                        return BaseResult.successData(refundResult);
                    } else {
                        log.info("查询到退款状态为 退款中/不明确 不做处理");
                    }

                } else {
                    log.info("查询失败，不做处理");
                }
                Thread.sleep(payTimeInterval * 1000);
            }

        } catch (Exception e) {
            log.error("2.______查询结果异常 e is {}", e);
            return BaseResult.error();
        }
        return BaseResult.error();
    }

//    private int saveNoticeAppRefundResultRecord(String orderNo, String refundAmount) {
//
//        Map<String, String> saveMap = new HashMap<>();
//        saveMap.put("orderNo", orderNo);
//        saveMap.put("refundType", "01");
//        saveMap.put("refundResult", ItpStatusEnum.REFUND_SUCCESS.getCode());
//        saveMap.put("refundResultDesc", ItpStatusEnum.REFUND_SUCCESS.getDesc());
//        saveMap.put("refundDate", DateUtils.getNowTimeByFormat("yyyyMMdd"));
//        saveMap.put("refundAmount", refundAmount);
//        saveMap.put("status", ItpCommon.NOTICE_INIT);
//        saveMap.put("createTime", DateUtils.getNowTime());
//        saveMap.put("retryTimes", "0");
//        int i = tvmNoticeAppMapper.insertRefundNotice(saveMap);
//        log.info("通知app记录保存成功 i is {}", i);
//        return i;
//    }

//    @Override
//    // 扫码取票业务 通知app退款结果
//    public boolean noticeAppRefundResult(String payOrderNo, String refundResult, String refundDate, String refundAmount, String retryTimes) {
//
//        boolean b = false;
//        log.info("开始通知app退款结果");
//        String refundResultDesc = "";
//        if (StringUtils.equals(refundResult, AppStatusEnum.REFUND_SUCCESS.getCode())) {
//            refundResultDesc = "refundResultDesc";
//        }
//        if (StringUtils.equals(refundResult, AppStatusEnum.REFUND_FAIL.getCode())) {
//            refundResultDesc = "refundResultDesc";
//        }
//        ItpCommonRequest<NoticeAppRefundDTO> request = payCenterCommon.buildNoticeAppRefundResultRequest(payOrderNo, refundResult, refundResultDesc, refundDate, refundAmount);
//
//        log.info("开始通知app退款 request is {}", request.toString());
//        String noticeAppRefundResultUrl = environment.getProperty("pay.center.notice-app-refundresult-url");
//        log.info("noticeAppRefundResultUrl is {}", noticeAppRefundResultUrl);
//
//        String httpResult = httpUtils.doPostFormData(noticeAppRefundResultUrl, request);
//
//        log.info("请求通知app退款结束 httpResult is {}", httpResult);
//        JSONObject httpResultJson = (JSONObject) JSONObject.parse(httpResult);
//        String retCode = String.valueOf(httpResultJson.get("retCode"));
//        log.info("请求通知app退款结束 retCode is {}", retCode);
//
//        Map<String, Object> upMap = new HashMap<>();
//        upMap.put("orderNo", payOrderNo);
//        upMap.put("updateTime", DateUtils.getNowTime());
//        // 此处是第一次推送，所以写死为1
//        upMap.put("retryTimes", retryTimes);
//
//        // 如果收到成功则修改数据库
//        if (StringUtils.equals(AppCodeEnum.SUCCESS.getCode(), retCode)) {
//            log.info("通知成功，修改通知记录状态为成功");
//            upMap.put("status", ItpCommon.NOTICE_SUCCESS);
//            b = true;
//        } else {
//            log.info("通知失败，修改通知状态为失败");
//            upMap.put("status", ItpCommon.NOTICE_FAIL);
//        }
//
//        int i = tvmNoticeAppMapper.updateRefundNoticeByOrderNo(upMap);
//        log.info("修改通知记录状态结束 i is {}", i);
//        return b;
//    }

//    private String getPayCenterRefundResult(String refundNo,String businessType){
//
//        // todo 轮询两分钟
//        PayCenterResponse payCenterResponse = this.queryRefundResult(refundNo);
//
//        // 如果查询返回成功
//        if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
//
//            Map<String, Object> data = payCenterResponse.getData();
//            String refundResult = TransforUtils.getStringFromData(data, "refundResult");
//            String refundTime = TransforUtils.getStringFromData(data, "refundDate");
//
//            // 处理业务
//            boolean b = dealRefundResult(refundNo,refundResult,refundTime);
//
//            // 如果查到了支付成功或者失败的结果，停止轮询
//            if(b){
//
//                //如果
//                if(StringUtils.equals(businessType, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())){
//
//                }
//
//            }
//
//        } else {
//            log.info("查询失败，不做处理");
//        }
//    }

//    private boolean dealRefundResult(String refundNo, String refundResult, String refundTime) {
//
//        boolean b = false;
//        String nowTime = DateUtils.getNowTime();
//
//        Map<String, String> upRefundOrder = new HashMap<>();
//        upRefundOrder.put("refundNo", refundNo);
//        upRefundOrder.put("updateTime", nowTime);
//
//        if (StringUtils.equals(refundResult, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())) {
//            log.info("查询到退款成功的结果");
//
//            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUND_SUCCESS.getCode());
//            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUND_SUCCESS.getDesc());
//            upRefundOrder.put("refundTime", refundTime);
//
//            int iRefund = refundOrderMapper.updateRefundStatus(upRefundOrder);
//            log.info("退款成功，修改退款订单结束 i is {}", iRefund);
//            b = true;
//
//        } else if (StringUtils.equals(refundResult, PayCenterRefundStatusEnum.REFUNDING_FAIL.getCode())) {
//            log.info("查询到退款状态为 退款失败");
//            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUNDING_FAIL.getCode());
//            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUNDING_FAIL.getDesc());
//            int iRefund = refundOrderMapper.updateRefundStatus(upRefundOrder);
//            log.info("退款失败，修改退款订单结束 i is {}", iRefund);
//            b = true;
//        } else {
//            log.info("查询到退款状态为 退款中/不明确 不做处理");
//        }
//        return b;
//    }

    private boolean dealRefundResult(String refundNo, String refundStatus, String refundTime) {

        boolean b = false;
        String nowTime = DateUtils.getNowTime();

        Map<String, String> upRefundOrder = new HashMap<>();
        upRefundOrder.put("refundNo", refundNo);
        upRefundOrder.put("updateTime", nowTime);

        if (StringUtils.equals(refundStatus, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())) {
            log.info("查询到退款成功的结果");

            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUND_SUCCESS.getCode());
            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUND_SUCCESS.getDesc());
            upRefundOrder.put("refundTime", refundTime);

            int iRefund = refundOrderMapper.updateRefundStatus(upRefundOrder);
            log.info("退款成功，修改退款订单结束 i is {}", iRefund);
            b = true;

        } else if (StringUtils.equals(refundStatus, PayCenterRefundStatusEnum.REFUNDING_FAIL.getCode())) {
            log.info("查询到退款状态为 退款失败");
            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUNDING_FAIL.getCode());
            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUNDING_FAIL.getDesc());
            int iRefund = refundOrderMapper.updateRefundStatus(upRefundOrder);
            log.info("退款失败，修改退款订单结束 i is {}", iRefund);
            b = true;
        } else {
            log.info("查询到退款状态为 退款中/不明确 不做处理");
        }
        return b;
    }

//    private boolean dealAppRefundResult(String payOrderNo, String refundNo, String refundResult, String refundTime) {
//
//        boolean b = false;
//        String nowTime = DateUtils.getNowTime();
//        if (StringUtils.equals(refundResult, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())) {
//            log.info("查询到app退款成功的结果");
//
//            Map<String, String> upRefundOrder = new HashMap<>();
//            upRefundOrder.put("refundNo", refundNo);
//            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUND_SUCCESS.getCode());
//            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUND_SUCCESS.getDesc());
//            upRefundOrder.put("refundTime", refundTime);
//            upRefundOrder.put("updateTime", nowTime);
//
//            int iRefund = appRefundOrderMapper.updateByRefundNo(upRefundOrder);
//            log.info("app退款成功，修改退款订单结束 i is {}", iRefund);
//
//            Map<String, String> upPayMap = new HashMap<>();
//            upPayMap.put("orderNo", payOrderNo);
//            upPayMap.put("rsv2", refundNo);
//            upPayMap.put("updateTime", nowTime);
//            int iPay = tvmAppOrderMapper.updateByOrderNo(upPayMap);
//            log.info("退款成功，修改原支付订单结束 i is {}", iPay);
//
//            // 返回itp的退款成功码
//            b = true;
//
//        } else if (StringUtils.equals(refundResult, PayCenterRefundStatusEnum.REFUNDING_FAIL.getCode())) {
//            log.info("查询到app退款状态为 退款失败");
//            Map<String, String> upRefundOrder = new HashMap<>();
//            upRefundOrder.put("refundNo", refundNo);
//            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUNDING_FAIL.getCode());
//            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUNDING_FAIL.getDesc());
//            upRefundOrder.put("updateTime", nowTime);
//
//            int iRefund = appRefundOrderMapper.updateByRefundNo(upRefundOrder);
//            log.info("app退款失败，修改退款订单结束 i is {}", iRefund);
//
//            b = true;
//        } else {
//            log.info("查询到app退款状态为 退款中/不明确 不做处理");
//        }
//        return b;
//    }

    @Override
    public PayCenterResponse queryRefundResult(String refundNo) {

        // 查询退款结果地址
        String queryRefundUrl = payCenterProperties.getQueryRefundUrl();

        log.info("查询退款结果地址 is {}", queryRefundUrl);

        Map<String, String> queryMap = new HashMap<>();
        queryMap.put("refundNo", refundNo);

        PayCenterRequest payCenterRequest = payCenterCommon.buildQueryRefundResultRequest(refundNo);
        PayCenterResponse payResponse = payCenterService.callPayCenter(queryRefundUrl, payCenterRequest);

        log.info("查询退款结果 payResponse is {}", payResponse);

        return payResponse;

    }

    public static String getRefundTime(String refundTime) throws ParseException {

        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(refundTime);

        String result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);

        return result;
    }
}
