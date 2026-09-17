package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 单程票明细 Mapper（表 F2F_TICKET）。 */
@Mapper
public interface F2fTicketMapper {

    /**
     * 插入单张票明细。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fTicket ticket);

    /**
     * 批量插入票明细，用于一次出票结果上报带多张票的场景。
     *
     * @param tickets 待插入的票明细列表，NEVER 传空集合（空集合会生成非法 SQL）
     * @return 影响行数，正常等于 tickets.size()
     */
    int batchInsert(@Param("tickets") List<F2fTicket> tickets);

    /**
     * 按订单号查该单下全部票明细，命中 IDX_F2F_TICKET_ORDER。
     *
     * @return 按 TRANS_DATE 升序的票列表；订单未出票时为空列表
     */
    List<F2fTicket> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按（逻辑卡号 + 交易日期）精确查一张票，命中唯一索引 UK_F2F_TICKET_LOGIC。
     *
     * @return 命中的票；不存在返回 null
     */
    F2fTicket selectByLogicNumAndTransDate(@Param("ticketLogicNum") String ticketLogicNum,
                                           @Param("transDate") String transDate);

    /**
     * 按逻辑卡号取交易日期最近的一笔票明细，命中 IDX_F2F_TICKET_RECENT。
     *
     * @return 最近一笔票；该卡无记录时返回 null
     */
    F2fTicket selectLatestByLogicNum(@Param("ticketLogicNum") String ticketLogicNum);

    /**
     * 票状态推进。
     *
     * @param fromStatuses 允许的前置状态，NEVER 传空集合
     * @return 影响行数；0 表示前置状态不满足，调用方 MUST 据此判断并拒绝
     */
    int updateStatus(@Param("ticketLogicNum") String ticketLogicNum,
                     @Param("transDate") String transDate,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus);

    /**
     * 退款成功时回填退款单号并把票状态置为 REFUNDED。
     *
     * @return 影响行数；0 表示票不存在或已是 REFUNDED，调用方 MUST 视为不可重复退款
     */
    int updateRefundNo(@Param("ticketLogicNum") String ticketLogicNum,
                       @Param("transDate") String transDate,
                       @Param("refundNo") String refundNo);
}
