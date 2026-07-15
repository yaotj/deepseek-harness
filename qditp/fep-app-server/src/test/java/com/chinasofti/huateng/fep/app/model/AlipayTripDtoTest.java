package com.chinasofti.huateng.fep.app.model;

import com.chinasofti.huateng.model.alipaytrip.*;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlipayTrip 系列 DTO 的序列化/反序列化测试。
 */
class AlipayTripDtoTest {

    @Test
    void addContractReqDto_serialize_deserialize() {
        AlipayTripAddContractReqDTO dto = new AlipayTripAddContractReqDTO();
        dto.setChannel("05");
        dto.setThirdUserId("third001");
        dto.setAgreementCode("AGREE001");
        dto.setChannelAgreementCode("CH_AGREE001");
        dto.setChannelUserAccount("user_account_001");
        dto.setCardIssueCode("0007");

        String json = JSON.toJSONString(dto);
        AlipayTripAddContractReqDTO parsed = JSON.parseObject(json, AlipayTripAddContractReqDTO.class);

        assertEquals("05", parsed.getChannel());
        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("AGREE001", parsed.getAgreementCode());
        assertEquals("CH_AGREE001", parsed.getChannelAgreementCode());
        assertEquals("user_account_001", parsed.getChannelUserAccount());
        assertEquals("0007", parsed.getCardIssueCode());
    }

    @Test
    void addContractRespDto_serialize_contains_code() {
        AlipayTripAddContractRespDTO resp = new AlipayTripAddContractRespDTO();
        resp.setRetCode("0000");
        resp.setRetMsg("成功");

        String json = JSON.toJSONString(resp);
        assertTrue(json.contains("\"retCode\":\"0000\""));
        assertTrue(json.contains("\"retMsg\":\"成功\""));
    }

    @Test
    void terminateContractReqDto_serialize_deserialize() {
        AlipayTripTerminateContractReqDTO dto = new AlipayTripTerminateContractReqDTO();
        dto.setAgreementCode("AGREE001");
        dto.setMerchantNo("MERCHANT001");

        String json = JSON.toJSONString(dto);
        AlipayTripTerminateContractReqDTO parsed = JSON.parseObject(json, AlipayTripTerminateContractReqDTO.class);

        assertEquals("AGREE001", parsed.getAgreementCode());
        assertEquals("MERCHANT001", parsed.getMerchantNo());
    }

    @Test
    void requestApplicationReqDto_serialize_deserialize() {
        AlipayTripRequestApplicationReqDTO dto = new AlipayTripRequestApplicationReqDTO();
        dto.setThirdUserId("third001");
        dto.setCardType("02");
        dto.setMsisdn("13800138000");
        dto.setExtend1("ext1");
        dto.setExtend2("ext2");
        dto.setCardIssueCode("0007");

        String json = JSON.toJSONString(dto);
        AlipayTripRequestApplicationReqDTO parsed = JSON.parseObject(json, AlipayTripRequestApplicationReqDTO.class);

        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("02", parsed.getCardType());
        assertEquals("13800138000", parsed.getMsisdn());
        assertEquals("ext1", parsed.getExtend1());
        assertEquals("ext2", parsed.getExtend2());
        assertEquals("0007", parsed.getCardIssueCode());
    }

    @Test
    void requestApplicationRespDto_withCardId() {
        AlipayTripRequestApplicationRespDTO resp = new AlipayTripRequestApplicationRespDTO();
        resp.setRetCode("0000");
        resp.setRetMsg("成功");
        resp.setCardId("9900000000000001");

        String json = JSON.toJSONString(resp);
        assertTrue(json.contains("\"cardId\":\"9900000000000001\""));
    }

    @Test
    void requestIndustryDataReqDto_serialize_deserialize() {
        AlipayTripRequestIndustryDataReqDTO dto = new AlipayTripRequestIndustryDataReqDTO();
        dto.setThirdUserId("third001");
        dto.setCardId("9900000000000001");
        dto.setCardType("02");

        String json = JSON.toJSONString(dto);
        AlipayTripRequestIndustryDataReqDTO parsed = JSON.parseObject(json, AlipayTripRequestIndustryDataReqDTO.class);

        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("9900000000000001", parsed.getCardId());
        assertEquals("02", parsed.getCardType());
    }

    @Test
    void findTravelListReqDto_serialize_deserialize() {
        AlipayTripFindTravelListReqDTO dto = new AlipayTripFindTravelListReqDTO();
        dto.setThirdUserId("third001");
        dto.setPage("0");
        dto.setSize("10");
        dto.setDebitRequestResult("SUCCESS");
        dto.setInvoice("0");
        dto.setStartDate("20240101");
        dto.setEndDate("20241231");

        String json = JSON.toJSONString(dto);
        AlipayTripFindTravelListReqDTO parsed = JSON.parseObject(json, AlipayTripFindTravelListReqDTO.class);

        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("0", parsed.getPage());
        assertEquals("10", parsed.getSize());
        assertEquals("SUCCESS", parsed.getDebitRequestResult());
        assertEquals("0", parsed.getInvoice());
        assertEquals("20240101", parsed.getStartDate());
        assertEquals("20241231", parsed.getEndDate());
    }

    @Test
    void findTravelListRespDto_withPagination() {
        AlipayTripFindTravelListRespDTO resp = new AlipayTripFindTravelListRespDTO();
        resp.setRetCode("0000");
        resp.setRetMsg("成功");
        resp.setPageNumber(0);
        resp.setPageSize(10);
        resp.setTotalPage(1);
        resp.setTotalCount(5);
        resp.setTicketTransRecord(java.util.Collections.emptyList());

        String json = JSON.toJSONString(resp);
        assertTrue(json.contains("\"pageNumber\":0"));
        assertTrue(json.contains("\"pageSize\":10"));
        assertTrue(json.contains("\"totalPage\":1"));
        assertTrue(json.contains("\"totalCount\":5"));
    }

    @Test
    void findTravelDetailReqDto_serialize_deserialize() {
        AlipayTripFindTravelDetailReqDTO dto = new AlipayTripFindTravelDetailReqDTO();
        dto.setThirdUserId("third001");
        dto.setOrderNo("ORDER001");

        String json = JSON.toJSONString(dto);
        AlipayTripFindTravelDetailReqDTO parsed = JSON.parseObject(json, AlipayTripFindTravelDetailReqDTO.class);

        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("ORDER001", parsed.getOrderNo());
    }

    @Test
    void travelRecordDto_serialize_deserialize() {
        AlipayTripTravelRecordDTO record = new AlipayTripTravelRecordDTO();
        record.setEntryStationName("青岛站");
        record.setEntryDate("20240101120000");
        record.setExitStationName("青岛北站");
        record.setExitDate("20240101123500");
        record.setPayAmount("500");
        record.setTotalAmount("600");
        record.setOrderExpType("01");
        record.setTradeOrderNo("TRADE001");
        record.setPayTradeOrderNo("PAY001");
        record.setPayOrderNoDate("20240101");
        record.setPayChannelCode("05");
        record.setDebitRequestResult("SUCCESS");
        record.setDiscountFee("100");
        record.setDiscountInfo("支付宝优惠");
        record.setCompanionFlag("N");
        record.setCardNum("9900000000000001");
        record.setTicketCode("TICKET001");
        record.setCountingTimes("1");
        record.setCountingFlag("0");

        String json = JSON.toJSONString(record);
        AlipayTripTravelRecordDTO parsed = JSON.parseObject(json, AlipayTripTravelRecordDTO.class);

        assertEquals("青岛站", parsed.getEntryStationName());
        assertEquals("20240101120000", parsed.getEntryDate());
        assertEquals("青岛北站", parsed.getExitStationName());
        assertEquals("20240101123500", parsed.getExitDate());
        assertEquals("500", parsed.getPayAmount());
        assertEquals("600", parsed.getTotalAmount());
        assertEquals("01", parsed.getOrderExpType());
        assertEquals("TRADE001", parsed.getTradeOrderNo());
        assertEquals("PAY001", parsed.getPayTradeOrderNo());
        assertEquals("20240101", parsed.getPayOrderNoDate());
        assertEquals("05", parsed.getPayChannelCode());
        assertEquals("SUCCESS", parsed.getDebitRequestResult());
        assertEquals("100", parsed.getDiscountFee());
        assertEquals("支付宝优惠", parsed.getDiscountInfo());
        assertEquals("N", parsed.getCompanionFlag());
        assertEquals("9900000000000001", parsed.getCardNum());
        assertEquals("TICKET001", parsed.getTicketCode());
        assertEquals("1", parsed.getCountingTimes());
        assertEquals("0", parsed.getCountingFlag());
    }

    @Test
    void closeResultReqDto_serialize_deserialize() {
        AlipayTripCloseResultReqDTO dto = new AlipayTripCloseResultReqDTO();
        dto.setResult(Boolean.TRUE);
        dto.setAgreementNo("AGREE001");

        String json = JSON.toJSONString(dto);
        AlipayTripCloseResultReqDTO parsed = JSON.parseObject(json, AlipayTripCloseResultReqDTO.class);

        assertEquals(Boolean.TRUE, parsed.getResult());
        assertEquals("AGREE001", parsed.getAgreementNo());
    }

    @Test
    void pushTransDataReqDto_serialize_deserialize() {
        AlipayTripPushTransDataReqDTO dto = new AlipayTripPushTransDataReqDTO();
        dto.setThirdUserId("third001");
        dto.setCardId("9900000000000001");
        dto.setCardType("02");
        dto.setTransData("{\"orderNo\":\"001\"}");

        String json = JSON.toJSONString(dto);
        AlipayTripPushTransDataReqDTO parsed = JSON.parseObject(json, AlipayTripPushTransDataReqDTO.class);

        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("9900000000000001", parsed.getCardId());
        assertEquals("02", parsed.getCardType());
        assertEquals("{\"orderNo\":\"001\"}", parsed.getTransData());
    }

    @Test
    void receiveCardDataReqDto_serialize_deserialize() {
        AlipayTripReceiveCardDataReqDTO dto = new AlipayTripReceiveCardDataReqDTO();
        dto.setThirdUserId("third001");
        dto.setCardId("9900000000000001");
        dto.setCardType("02");
        dto.setCardData("{\"industryData\":\"123\"}");

        String json = JSON.toJSONString(dto);
        AlipayTripReceiveCardDataReqDTO parsed = JSON.parseObject(json, AlipayTripReceiveCardDataReqDTO.class);

        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("9900000000000001", parsed.getCardId());
        assertEquals("02", parsed.getCardType());
        assertEquals("{\"industryData\":\"123\"}", parsed.getCardData());
    }

    @Test
    void receiveBlackListReqDto_serialize_deserialize() {
        AlipayTripReceiveBlackListReqDTO dto = new AlipayTripReceiveBlackListReqDTO();
        dto.setThirdUserId("third001");
        dto.setCardId("9900000000000001");
        dto.setBlacklistStatus("1");
        dto.setReason("test");

        String json = JSON.toJSONString(dto);
        AlipayTripReceiveBlackListReqDTO parsed = JSON.parseObject(json, AlipayTripReceiveBlackListReqDTO.class);

        assertEquals("third001", parsed.getThirdUserId());
        assertEquals("9900000000000001", parsed.getCardId());
        assertEquals("1", parsed.getBlacklistStatus());
        assertEquals("test", parsed.getReason());
    }
}
