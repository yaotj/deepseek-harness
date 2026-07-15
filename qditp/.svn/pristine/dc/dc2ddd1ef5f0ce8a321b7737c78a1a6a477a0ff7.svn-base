package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.service.AlipayTripService;
import com.chinasofti.huateng.model.alipaytrip.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FepAlipayTripNotifyController 通知接口层测试。
 */
class AlipayTripNotifyControllerTest {

    private MockMvc mockMvc;
    private AlipayTripService alipayTripService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        FepAlipayTripNotifyController controller = new FepAlipayTripNotifyController();
        alipayTripService = Mockito.mock(AlipayTripService.class);
        ReflectionTestUtils.setField(controller, "alipayTripService", alipayTripService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void closeResultForAlipay_shouldReturnSuccess() throws Exception {
        AlipayTripCloseResultReqDTO request = new AlipayTripCloseResultReqDTO();
        request.setResult(Boolean.TRUE);
        request.setAgreementNo("AGREE001");

        AlipayTripCloseResultRespDTO response = new AlipayTripCloseResultRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.closeResultForAlipay(Mockito.any(AlipayTripCloseResultReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/notify/closeResultForAlipay")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retCode").value("0000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void pushTransData_shouldReturnSuccess() throws Exception {
        AlipayTripPushTransDataReqDTO request = new AlipayTripPushTransDataReqDTO();
        request.setThirdUserId("third001");
        request.setCardId("9900000000000001");
        request.setCardType("02");
        request.setTransData("{\"orderNo\":\"001\"}");

        AlipayTripPushTransDataRespDTO response = new AlipayTripPushTransDataRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.pushTransData(Mockito.any(AlipayTripPushTransDataReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/notify/pushTransData")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retCode").value("0000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void receiveCardDataFromItp_shouldReturnSuccess() throws Exception {
        AlipayTripReceiveCardDataReqDTO request = new AlipayTripReceiveCardDataReqDTO();
        request.setThirdUserId("third001");
        request.setCardId("9900000000000001");
        request.setCardType("02");
        request.setCardData("{\"industryData\":\"123\"}");

        AlipayTripReceiveCardDataRespDTO response = new AlipayTripReceiveCardDataRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.receiveCardDataFromItp(Mockito.any(AlipayTripReceiveCardDataReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/notify/receiveCardDataFromItp")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retCode").value("0000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void receiveBlackListFromItp_shouldReturnSuccess() throws Exception {
        AlipayTripReceiveBlackListReqDTO request = new AlipayTripReceiveBlackListReqDTO();
        request.setThirdUserId("third001");
        request.setCardId("9900000000000001");
        request.setBlacklistStatus("1");
        request.setReason("test");

        AlipayTripReceiveBlackListRespDTO response = new AlipayTripReceiveBlackListRespDTO();
        response.setRetCode("0000");
        response.setRetMsg("成功");

        Mockito.when(alipayTripService.receiveBlackListFromItp(Mockito.any(AlipayTripReceiveBlackListReqDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/notify/receiveBlackListFromItp")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retCode").value("0000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retMsg").value("成功"));
    }
}
