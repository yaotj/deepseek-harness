package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.TvmTopupOrder;
import com.chinasofti.huateng.collectpay.model.page.DailyTopupStatView;
import com.chinasofti.huateng.collectpay.model.page.TvmTopupOrderPageView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TvmTopupOrderMapper {
    /** 根据订单号查询订单。 */
    TvmTopupOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /** 插入订单。 */
    int insert(TvmTopupOrder record);

    /** 根据orderNo动态更新订单。 */
    int updateByOrderNo(Map<String, String> params);

    int insertNotiy(Map<String, String> params);

    int insertFailtNotiy(Map<String, String> params);

    List<TvmTopupOrder> selectByCondition(Map<String, String> params);

    /** 运营端分页查询 TVM 充值订单（带最新一条充值回执态），按下单时间倒序。 */
    List<TvmTopupOrderPageView> selectTopupOrderPage(@Param("orderNo") String orderNo,
                                                     @Param("payCenterOrderNo") String payCenterOrderNo,
                                                     @Param("deviceId") String deviceId,
                                                     @Param("ticketLogicNum") String ticketLogicNum,
                                                     @Param("status") String status,
                                                     @Param("channel") String channel,
                                                     @Param("topupStatus") String topupStatus,
                                                     @Param("beginTime") String beginTime,
                                                     @Param("endTime") String endTime,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    /** 统计 selectTopupOrderPage 同口径总条数。 */
    int countTopupOrderPage(@Param("orderNo") String orderNo,
                            @Param("payCenterOrderNo") String payCenterOrderNo,
                            @Param("deviceId") String deviceId,
                            @Param("ticketLogicNum") String ticketLogicNum,
                            @Param("status") String status,
                            @Param("channel") String channel,
                            @Param("topupStatus") String topupStatus,
                            @Param("beginTime") String beginTime,
                            @Param("endTime") String endTime);

    /** TVM 充值日报表：按下单日期聚合（dateStart/dateEnd 为 yyyy-MM-dd 00:00:00/23:59:59 闭区间）。 */
    List<DailyTopupStatView> selectTvmTopupDaily(@Param("dateStart") String dateStart,
                                                 @Param("dateEnd") String dateEnd);
}

