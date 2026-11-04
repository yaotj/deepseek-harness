package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.BomNoCashOrder;
import com.chinasofti.huateng.collectpay.model.page.BomOrderPageView;
import com.chinasofti.huateng.collectpay.model.page.DailyTopupStatView;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestTicketRefundReqDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** BOM非现金收款订单Mapper接口。 */
@Mapper
public interface BomNoCashOrderMapper {

    List<BomNoCashOrder> selectSaleOrdersByCondition(Map<String, String> condition);
    List<BomNoCashOrder> selectTopupByCondition(Map<String, String> condition);

    /**
     * 根据订单号查询订单信息。
     *
     * @param orderNo 订单号
     * @return 订单实体对象，不存在返回null
     */
    BomNoCashOrder selectByOrderNo(String orderNo);

    /**
     * 插入新订单。
     *
     * @param order 订单实体对象
     * @return 影响的行数
     */
    int insert(BomNoCashOrder order);

    int insertBomSale(BomNoCashOrder order);

    /**
     * 根据订单号更新订单信息。
     *
     * @param params 更新参数，必须包含orderNo字段
     * @return 影响的行数
     */
    int updateByOrderNo(Map<String, String> params);

    // 单程票退款 弃用 这个方法只限制了bom订单，改为全类型
    int insertTicketRefund(RequestTicketRefundReqDTO dto);

    // 单程票退款
    int insertTicketRefundRecord(RequestTicketRefundReqDTO dto);

    /** 运营端分页查询 BOM 非现金订单（transType 可选：01 购票发售 / 22 充值），按下单时间倒序。 */
    List<BomOrderPageView> selectBomOrderPage(@Param("orderNo") String orderNo,
                                              @Param("payCenterOrderNo") String payCenterOrderNo,
                                              @Param("deviceId") String deviceId,
                                              @Param("cardId") String cardId,
                                              @Param("transType") String transType,
                                              @Param("status") String status,
                                              @Param("paymentVendor") String paymentVendor,
                                              @Param("beginTime") String beginTime,
                                              @Param("endTime") String endTime,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /** 统计 selectBomOrderPage 同口径总条数。 */
    int countBomOrderPage(@Param("orderNo") String orderNo,
                          @Param("payCenterOrderNo") String payCenterOrderNo,
                          @Param("deviceId") String deviceId,
                          @Param("cardId") String cardId,
                          @Param("transType") String transType,
                          @Param("status") String status,
                          @Param("paymentVendor") String paymentVendor,
                          @Param("beginTime") String beginTime,
                          @Param("endTime") String endTime);

    /** 非现金充值数据查询：BOM 侧 TRANS_TYPE=22（充值）订单按下单日期聚合。 */
    List<DailyTopupStatView> selectNocashTopupDaily(@Param("dateStart") String dateStart,
                                                    @Param("dateEnd") String dateEnd);
}