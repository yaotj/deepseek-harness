package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 单程票明细 Mapper（表 F2F_TICKET）。
 *
 * <p>四条本表特有的约束，改这个接口前 MUST 先读：
 * <ul>
 *   <li><b>出票上报一次带多张票，落库走 {@link #batchInsert}。</b>XML 用 Oracle 的
 *       {@code INSERT ALL ... SELECT * FROM DUAL} 形式，NEVER 改成 MySQL 的
 *       多值 VALUES 语法——Oracle 不支持。</li>
 *   <li><b>只 INSERT，不先查后插。</b>重复上报靠
 *       {@code UK_F2F_TICKET_LOGIC (TICKET_LOGIC_NUM, TRANS_DATE)} 抛
 *       {@code DuplicateKeyException}，由 application 层捕获后幂等返回。</li>
 *   <li><b>{@link #selectLatestByLogicNum} 必须带排序与取一行。</b>SQL 已写死
 *       {@code ORDER BY TRANS_DATE DESC} + {@code FETCH FIRST 1 ROWS ONLY}，
 *       命中 IDX_F2F_TICKET_RECENT。旧实现同类查询既无排序也无取一行，
 *       多笔命中直接抛 {@code TooManyResultsException}，这是已记录的缺陷。</li>
 *   <li><b>状态推进走白名单。</b>{@link #updateStatus} 的 {@code fromStatuses} 必填，
 *       NEVER 写成「非终态即可更新」。</li>
 * </ul>
 */
@Mapper
public interface F2fTicketMapper {

    /**
     * 插入单张票明细。重复的（TICKET_LOGIC_NUM, TRANS_DATE）由唯一索引抛
     * DuplicateKeyException，不在此处判重。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fTicket ticket);

    /**
     * 批量插入票明细，用于一次出票结果上报带多张票的场景。
     * XML 生成 Oracle 的 INSERT ALL ... SELECT * FROM DUAL。
     *
     * @param tickets 待插入的票明细列表，NEVER 传空集合（空集合会生成非法 SQL）
     * @return 影响行数，正常等于 tickets.size()
     */
    int batchInsert(@Param("tickets") List<F2fTicket> tickets);

    /**
     * 按订单号查该单下全部票明细，命中 IDX_F2F_TICKET_ORDER。
     * 供出票结果核对与按票退款时枚举使用。
     *
     * @return 按 TRANS_DATE 升序的票列表；订单未出票时为空列表
     */
    List<F2fTicket> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按（逻辑卡号 + 交易日期）精确查一张票，命中唯一索引 UK_F2F_TICKET_LOGIC。
     * 对应 BOM 单程票原路退款按这两个要素定位原交易。
     *
     * @return 命中的票；不存在返回 null
     */
    F2fTicket selectByLogicNumAndTransDate(@Param("ticketLogicNum") String ticketLogicNum,
                                           @Param("transDate") String transDate);

    /**
     * 按逻辑卡号取交易日期最近的一笔票明细，命中 IDX_F2F_TICKET_RECENT。
     * 用于设备只传逻辑卡号、未传交易日期时的退款定位。
     *
     * <p>SQL 带显式 ORDER BY TRANS_DATE DESC + FETCH FIRST 1 ROWS ONLY，
     * NEVER 去掉——多笔命中会抛 TooManyResultsException。
     *
     * @return 最近一笔票；该卡无记录时返回 null
     */
    F2fTicket selectLatestByLogicNum(@Param("ticketLogicNum") String ticketLogicNum);

    /**
     * 票状态推进。仅当当前状态在 fromStatuses 白名单内才更新。
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
     * 前置状态白名单为 REFUNDING / ISSUED / FAULT。
     *
     * @return 影响行数；0 表示票不存在或已是 REFUNDED，调用方 MUST 视为不可重复退款
     */
    int updateRefundNo(@Param("ticketLogicNum") String ticketLogicNum,
                       @Param("transDate") String transDate,
                       @Param("refundNo") String refundNo);
}
