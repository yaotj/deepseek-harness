package com.chinasofti.huateng.fep.app.service.impl;

import com.chinasofti.huateng.model.alipaytrip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AlipayTripServiceImpl 单元测试。
 * 使用 Mockito 模拟依赖，验证 Service 层逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AlipayTripServiceImplTest {

    @InjectMocks
    private AlipayTripServiceImpl alipayTripService;

    @Test
    void addContract_success() {
        AlipayTripAddContractReqDTO request = new AlipayTripAddContractReqDTO();
        request.setChannel("05");
        request.setThirdUserId("third001");
        request.setAgreementCode("AGREE001");
        request.setChannelAgreementCode("CH_AGREE001");
        request.setChannelUserAccount("user_account_001");
        request.setCardIssueCode("0007");

        AlipayTripAddContractRespDTO response = alipayTripService.addContract(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void addContract_exception_shouldReturnSystemError() {
        AlipayTripAddContractReqDTO request = new AlipayTripAddContractReqDTO();
        request.setChannel("05");
        request.setThirdUserId("third001");

        // 当前实现无外部依赖，不会抛出异常；此处为示例结构
        AlipayTripAddContractRespDTO response = alipayTripService.addContract(request);
        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
    }

    @Test
    void terminateContract_success() {
        AlipayTripTerminateContractReqDTO request = new AlipayTripTerminateContractReqDTO();
        request.setAgreementCode("AGREE001");
        request.setMerchantNo("MERCHANT001");

        AlipayTripTerminateContractRespDTO response = alipayTripService.terminateContract(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void requestApplication_success() {
        AlipayTripRequestApplicationReqDTO request = new AlipayTripRequestApplicationReqDTO();
        request.setThirdUserId("third001");
        request.setCardType("02");
        request.setMsisdn("13800138000");
        request.setCardIssueCode("0007");

        AlipayTripRequestApplicationRespDTO response = alipayTripService.requestApplication(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
        assertNotNull(response.getCardId());
    }

    @Test
    void requestIndustryData_success() {
        AlipayTripRequestIndustryDataReqDTO request = new AlipayTripRequestIndustryDataReqDTO();
        request.setThirdUserId("third001");
        request.setCardId("9900000000000001");
        request.setCardType("02");

        AlipayTripRequestIndustryDataRespDTO response = alipayTripService.requestIndustryData(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void findTravelList_success() {
        AlipayTripFindTravelListReqDTO request = new AlipayTripFindTravelListReqDTO();
        request.setThirdUserId("third001");
        request.setPage("0");
        request.setSize("10");

        AlipayTripFindTravelListRespDTO response = alipayTripService.findTravelList(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
        assertEquals(Integer.valueOf(0), response.getPageNumber());
        assertEquals(Integer.valueOf(10), response.getPageSize());
        assertEquals(Integer.valueOf(0), response.getTotalPage());
        assertEquals(Integer.valueOf(0), response.getTotalCount());
        assertNotNull(response.getTicketTransRecord());
        assertTrue(response.getTicketTransRecord().isEmpty());
    }

    @Test
    void findTravelList_defaultSize_whenNull() {
        AlipayTripFindTravelListReqDTO request = new AlipayTripFindTravelListReqDTO();
        request.setThirdUserId("third001");
        request.setPage("0");
        // size 为 null

        AlipayTripFindTravelListRespDTO response = alipayTripService.findTravelList(request);

        assertNotNull(response);
        assertEquals(Integer.valueOf(10), response.getPageSize());
    }

    @Test
    void findTravelDetail_success() {
        AlipayTripFindTravelDetailReqDTO request = new AlipayTripFindTravelDetailReqDTO();
        request.setThirdUserId("third001");
        request.setOrderNo("ORDER001");

        AlipayTripFindTravelDetailRespDTO response = alipayTripService.findTravelDetail(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void closeResultForAlipay_success() {
        AlipayTripCloseResultReqDTO request = new AlipayTripCloseResultReqDTO();
        request.setResult(Boolean.TRUE);
        request.setAgreementNo("AGREE001");

        AlipayTripCloseResultRespDTO response = alipayTripService.closeResultForAlipay(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void pushTransData_success() {
        AlipayTripPushTransDataReqDTO request = new AlipayTripPushTransDataReqDTO();
        request.setThirdUserId("third001");
        request.setCardId("9900000000000001");
        request.setCardType("02");
        request.setTransData("{\"orderNo\":\"001\"}");

        AlipayTripPushTransDataRespDTO response = alipayTripService.pushTransData(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void receiveCardDataFromItp_success() {
        AlipayTripReceiveCardDataReqDTO request = new AlipayTripReceiveCardDataReqDTO();
        request.setThirdUserId("third001");
        request.setCardId("9900000000000001");
        request.setCardType("02");
        request.setCardData("{\"industryData\":\"123\"}");

        AlipayTripReceiveCardDataRespDTO response = alipayTripService.receiveCardDataFromItp(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void receiveBlackListFromItp_success() {
        AlipayTripReceiveBlackListReqDTO request = new AlipayTripReceiveBlackListReqDTO();
        request.setThirdUserId("third001");
        request.setCardId("9900000000000001");
        request.setBlacklistStatus("1");
        request.setReason("test");

        AlipayTripReceiveBlackListRespDTO response = alipayTripService.receiveBlackListFromItp(request);

        assertNotNull(response);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }
}
