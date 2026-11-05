package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface F2fTicketMapper {

    int insert(F2fTicket ticket);

    int batchInsert(@Param("tickets") List<F2fTicket> tickets);

    List<F2fTicket> selectByOrderNo(@Param("orderNo") String orderNo);

    F2fTicket selectByLogicNumAndTransDate(@Param("ticketLogicNum") String ticketLogicNum,
                                           @Param("transDate") String transDate);

    F2fTicket selectLatestByLogicNum(@Param("ticketLogicNum") String ticketLogicNum);

    int updateStatus(@Param("ticketLogicNum") String ticketLogicNum,
                     @Param("transDate") String transDate,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus);

    int updateRefundNo(@Param("ticketLogicNum") String ticketLogicNum,
                       @Param("transDate") String transDate,
                       @Param("refundNo") String refundNo);
}
