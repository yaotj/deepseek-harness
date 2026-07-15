package com.chinasofti.huateng.collectpay.common;

import com.chinasofti.huateng.collectpay.constant.ItpStatusEnum;
import com.chinasofti.huateng.collectpay.model.request.tvm.TopupCardResultNotiReqDTO;
import com.chinasofti.huateng.collectpay.utils.DateUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateDbMap {


    // 拉码 修改数据库记录 拉码成功 不修改状态
    public static Map<String,String> getLaMaUpdateSuccessDb(String orderNo, String payCenterOrderNo,String payCenterChannelOrderNo,String payUrl){
        Map<String,String> uMap = new HashMap<>();
        uMap.put("orderNo",orderNo);
        uMap.put("payCenterOrderNo",payCenterOrderNo);
        uMap.put("payCenterChannelOrderNo",payCenterChannelOrderNo);
        uMap.put("url",payUrl);
        uMap.put("updateTime", DateUtils.getNowTime());
        return uMap;
    }

    // 拉码 修改数据库记录 拉码失败 修改状态
    public static Map<String,String> getLaMaUpdateFailDb(String orderNo){
        Map<String,String> uMap = new HashMap<>();
        uMap.put("orderNo",orderNo);
        uMap.put("status", ItpStatusEnum.FAILED.getCode());
        uMap.put("msg",ItpStatusEnum.FAILED.getDesc());
        uMap.put("updateTime", DateUtils.getNowTime());
        return uMap;
    }

    // 查询 支付成功修改数据库记录
    public static Map<String,String> getQueryUpdateSuccessDb(String orderNo,String payCenterChannelOrderNo){
        Map<String,String> uMap = new HashMap<>();
        uMap.put("orderNo",orderNo);
        uMap.put("status", ItpStatusEnum.SUCCESS.getCode());
        uMap.put("msg",ItpStatusEnum.SUCCESS.getDesc());
        uMap.put("payCenterChannelOrderNo",payCenterChannelOrderNo);
        uMap.put("updateTime", DateUtils.getNowTime());
        return uMap;
    }

    // 查询 支付失败修改数据库记录
    public static Map<String,String> getQueryUpdateFailDb(String orderNo){
        Map<String,String> uMap = new HashMap<>();
        uMap.put("orderNo",orderNo);
        uMap.put("status",ItpStatusEnum.FAILED.getCode());
        uMap.put("msg",ItpStatusEnum.FAILED.getDesc());
        uMap.put("updateTime", DateUtils.getNowTime());
        return uMap;
    }

    // 充值 修改数据库记录 拉码成功 不修改状态
    public static Map<String,String> getTopupUpdateSuccessDb(String orderNo,String payCenterChannelOrderNo,String aftAmount){
        Map<String,String> uMap = new HashMap<>();
        uMap.put("orderNo",orderNo);
        uMap.put("status", ItpStatusEnum.SUCCESS.getCode());
        uMap.put("msg",ItpStatusEnum.SUCCESS.getDesc());
        uMap.put("payCenterChannelOrderNo",payCenterChannelOrderNo);
        uMap.put("afterAmount",aftAmount);
        uMap.put("updateTime", DateUtils.getNowTime());
        return uMap;
    }

    // 充值 修改数据库记录 充值失败 修改状态
    public static Map<String,String> getTopupUpdateFailDb(String orderNo,String afterAmount){
        Map<String,String> uMap = new HashMap<>();
        uMap.put("orderNo",orderNo);
        uMap.put("status", ItpStatusEnum.FAILED.getCode());
        uMap.put("msg",ItpStatusEnum.FAILED.getDesc());
        uMap.put("afterAmount",afterAmount);
        uMap.put("updateTime", DateUtils.getNowTime());
        return uMap;
    }

    // 充值查询 修改数据库记录 充值成功
    public static Map<String,String> getTopupNotiUpdateSuccessDb(TopupCardResultNotiReqDTO request){
        // 更新订单信息
        Map<String, String> uMap = new LinkedHashMap<>();
        uMap.put("orderNo", request.getOrderNo());
        uMap.put("ticketLogicNum", request.getTicketLogicNum());
        uMap.put("ticketPhysicsNum", request.getTicketPhysicsNum());
        uMap.put("transAmount", request.getTransAmount());
        uMap.put("afterAmount", request.getAfterAmount());
        uMap.put("updateTime", DateUtils.getNowTime());
        return uMap;
    }
    
}
