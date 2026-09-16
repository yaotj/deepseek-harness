package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.TvmAppOrder;
import com.chinasofti.huateng.collectpay.entity.TvmTakeTicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * TVM APP订单Mapper接口。
 * 提供APP订单的数据库操作方法。
 */
@Mapper
public interface TvmAppOrderMapper {

    /**
     * 根据订单号查询订单信息。
     *
     * @param orderNo 订单号
     * @return 订单实体对象，不存在返回null
     */
    TvmAppOrder selectByOrderNo(@Param("orderNo") String orderNo);
    List<TvmAppOrder> selectOrderLsByUserId(@Param("userId") String userId,@Param("activeFlag") String activeFlag);

    TvmAppOrder selectByOrderNoAndUserId(@Param("orderNo") String orderNo,@Param("userId") String userId);

    List<TvmAppOrder> selectByDeviceAndQrcode(@Param("deviceId") String deviceId,
                                              @Param("qrcodeGenDate") String qrcodeGenDate,
                                              @Param("randomFact") String randomFact);

    /**
     * 插入新订单。
     *
     * @param order 订单实体对象
     * @return 影响的行数
     */
    int insert(TvmAppOrder order);

    /**
     * 根据订单号更新订单信息。
     * 使用Map传参，支持动态更新字段。
     *
     * @param params 更新参数，必须包含orderNo字段
     * @return 影响的行数
     */
    int updateByOrderNo(Map<String, String> params);

    /**
     * 把仍待支付（{@code PAY_STATUS='0'}）的订单行置为支付失败，白名单写在 SQL 的 WHERE 里。
     *
     * <p>与 {@link #updateByOrderNo} 的区别是**它是有条件的**：那个的 WHERE 只有 {@code ORDER_NO}，
     * 会把已支付成功的行也一起改掉。关单场景 <b>MUST</b> 用本方法，
     * <b>NEVER</b> 图省事换成 {@code updateByOrderNo} —— 乘客付款与上游关单之间有毫秒级竞态，
     * 无条件更新会把「已收到的钱」抹成支付失败，钱收了却没人销账。</p>
     *
     * <p>影响 0 行是正常结果（该行已是终态），调用方 MUST 当成成功返回、
     * <b>NEVER</b> 当失败重试：关单的语义是「保证乘客付不了」，本来就付不了时目标已达成。</p>
     *
     * @return 影响的行数，0 表示该行不存在或已是终态
     */
    int closeUnpaidByOrderNo(@Param("orderNo") String orderNo, @Param("msg") String msg);


    List<TvmAppOrder> selectByCondition(Map<String,String> condition);
}