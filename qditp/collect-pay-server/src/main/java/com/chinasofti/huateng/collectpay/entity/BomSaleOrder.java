package com.chinasofti.huateng.collectpay.entity;

import lombok.Data;

/** BOM非现金收款订单实体类。 */
@Data
public class BomSaleOrder {

    /** 订单号（主键）。 */
    private String orderNo;

    /** 设备编码。 */
    private String deviceId;

    /** 起点站点代码。 */
    private String inStationCode;

    /** 终点站点代码。 */
    private String outStationCode;

    /** 票价（单位：分）。 */
    private String ticketPrice;

    /** 购买数量。 */
    private Integer ticketNum;
    private Integer totalPrice;

    /** 购票类型：0-按站点购票，1-按固定票价购票。 */
    private String ticketType;
    private String payType;

    /** 创建时间。 */
    private String createTime;

    /** 更新时间（最后修改时间）。 */
    private String updateTime;


}