package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.BomBusResult;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * BOM业务操作结果通知Mapper接口。
 * 提供BOM业务操作结果通知的数据库操作方法。
 */
@Mapper
public interface BomBusResultMapper {

    /**
     * 根据通知ID查询通知信息。
     *
     * @param notifyId 通知ID
     * @return 通知实体对象，不存在返回null
     */
    BomBusResult selectByNotifyId(String notifyId);

    /**
     * 根据订单号查询通知信息。
     *
     * @param orderNo 订单号
     * @return 通知实体对象，不存在返回null
     */
    BomBusResult selectByOrderNo(String orderNo);

    /**
     * 插入新通知记录。
     *
     * @param busResult 通知实体对象
     * @return 影响的行数
     */
    int insert(BomBusResult busResult);

    /**
     * 根据通知ID更新通知信息。
     * 使用Map传参，支持动态更新字段。
     *
     * @param params 更新参数，必须包含notifyId字段
     * @return 影响的行数
     */
    int updateByNotifyId(Map<String, String> params);
}