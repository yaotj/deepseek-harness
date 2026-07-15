package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AlipayTripService;
import com.chinasofti.huateng.model.alipaytrip.*;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FepAlipayTripController 接口层测试。
 */
class AlipayTripControllerTest {

    private MockMvc mockMvc;
    private AlipayTripService alipayTripService;

    @BeforeEach
    void setUp() {
        FepAlipayTripController controller = new FepAlipayTripController();
        alipayTripService = Mockito.mock(AlipayTripService.class);
        ReflectionTestUtils.setField(controller, "alipayTripService", alipayTripService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void addContract_shouldReturnSuccess() throws Exception {
        AlipayTripAddContractReqDTO bizData = new AlipayTripAddContractReqDTO();
        bizData.setChannel("05");
        bizData.setThirdUserId("third001");
        bizData.setAgreementCode("AGREE001");
        bizData.setChannelAgreementCode("CH_AGREE001");
        bizData.setChannelUserAccount("user_account_001");
        bizData.setCardIssueCode("0007");

        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.addContract(Mockito.any(AlipayTripAddContractReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/channel/addContract")
                        .param("bizData", JSON.toJSONString(bizData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void terminateContract_shouldReturnSuccess() throws Exception {
        AlipayTripTerminateContractReqDTO bizData = new AlipayTripTerminateContractReqDTO();
        bizData.setAgreementCode("AGREE001");
        bizData.setMerchantNo("MERCHANT001");

        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.terminateContract(Mockito.any(AlipayTripTerminateContractReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/channel/terminateContract")
                        .param("bizData", JSON.toJSONString(bizData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void requestApplication_shouldReturnSuccess() throws Exception {
        AlipayTripRequestApplicationReqDTO bizData = new AlipayTripRequestApplicationReqDTO();
        bizData.setThirdUserId("third001");
        bizData.setCardType("02");
        bizData.setMsisdn("13800138000");
        bizData.setCardIssueCode("0007");

        AlipayTripRequestApplicationRespDTO response = new AlipayTripRequestApplicationRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");
        response.setCardId("9900000000000001");

        Mockito.when(alipayTripService.requestApplication(Mockito.any(AlipayTripRequestApplicationReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/channel/requestApplication")
                        .param("bizData", JSON.toJSONString(bizData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"))
                .andExpect(jsonPath("$.cardId").value("9900000000000001"));
    }

    @Test
    void requestIndustryData_shouldReturnSuccess() throws Exception {
        AlipayTripRequestIndustryDataReqDTO bizData = new AlipayTripRequestIndustryDataReqDTO();
        bizData.setThirdUserId("third001");
        bizData.setCardId("CARD001");
        bizData.setCardType("0441");

        AlipayTripRequestIndustryDataRespDTO response = new AlipayTripRequestIndustryDataRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.requestIndustryData(Mockito.any(AlipayTripRequestIndustryDataReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/channel/requestIndustryData")
                        .param("bizData", JSON.toJSONString(bizData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void findTravelList_shouldReturnSuccess() throws Exception {
        AlipayTripFindTravelListReqDTO bizData = new AlipayTripFindTravelListReqDTO();
        bizData.setThirdUserId("third001");
        bizData.setPage("0");
        bizData.setSize("10");

        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");
        response.setPageNumber(0);
        response.setPageSize(10);
        response.setTotalPage(0);
        response.setTotalCount(0);
        response.setTicketTransRecord(java.util.Collections.emptyList());

        Mockito.when(alipayTripService.findTravelList(Mockito.any(AlipayTripFindTravelListReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/channel/findTravelList")
                        .param("bizData", JSON.toJSONString(bizData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    void findTravelDetail_shouldReturnSuccess() throws Exception {
        AlipayTripFindTravelDetailReqDTO bizData = new AlipayTripFindTravelDetailReqDTO();
        bizData.setThirdUserId("third001");
        bizData.setOrderNo("ORDER001");

        AlipayTripFindTravelDetailRespDTO response = new AlipayTripFindTravelDetailRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.findTravelDetail(Mockito.any(AlipayTripFindTravelDetailReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/channel/findTravelDetail")
                        .param("bizData", JSON.toJSONString(bizData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"));
    }
}
