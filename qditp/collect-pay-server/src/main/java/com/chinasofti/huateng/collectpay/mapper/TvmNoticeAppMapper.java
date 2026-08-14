package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.NoticeRefundRecord;
import com.chinasofti.huateng.collectpay.entity.NoticeTakeTicketFailureRecord;
import com.chinasofti.huateng.collectpay.entity.NoticeTakeTicketRecord;
import com.chinasofti.huateng.collectpay.entity.TvmMainTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TvmNoticeAppMapper {

    int insertTakeNotice(Map<String,String> map);


    int updateTakeNoticeByOrderNo(Map<String,Object> map);

    List<NoticeTakeTicketRecord> selectSendFailTakeTicketLs(Map<String,String> map);


    int insertRefundNotice(Map<String,String> map);


    int updateRefundNoticeByOrderNo(Map<String,Object> map);

    List<NoticeRefundRecord> selectSendFailRefundLs(Map<String,String> map);

    int insertTakeFailureNotice(Map<String,String> map);


    int updateTakeFailureNoticeByOrderNo(Map<String,Object> map);

    List<NoticeTakeTicketFailureRecord> selectSendFailTakeTicketFailureLs(Map<String,String> map);


}
