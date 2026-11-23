package com.chinasofti.huateng.heartbeat;

import com.chinasofti.huateng.heartbeat.controller.AgmHeartbeatController;
import com.chinasofti.huateng.heartbeat.controller.BomHeartbeatController;
import com.chinasofti.huateng.heartbeat.controller.TvmHeartbeatController;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备心跳服务集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceHeartbeatApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceHeartbeatService heartbeatService;

    @Test
    void testAgmHeartbeat() throws Exception {
        mockMvc.perform(post("/heartbeat/agm/deviceHeartbeat")
                        .param("deviceId", "06220801")
                        .param("bizData", "{\"test\":\"data\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void testTvmHeartbeat() throws Exception {
        mockMvc.perform(post("/heartbeat/tvm/notiDeviceHeard")
                        .param("deviceId", "06220802"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void testBomHeartbeat() throws Exception {
        mockMvc.perform(post("/heartbeat/bom/deviceHeartbeat")
                        .param("deviceId", "06220803")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"))
                .andExpect(jsonPath("$.retMsg").value("成功"));
    }

    @Test
    void testHeartbeatWithNullDeviceId() throws Exception {
        mockMvc.perform(post("/heartbeat/agm/deviceHeartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retCode").value("0000"));
    }

    @Test
    void testExtractStationCode() {
        assert "0622".equals(heartbeatService.extractStationCode("06220801"));
        assert "1128".equals(heartbeatService.extractStationCode("11280801"));
        assert null == heartbeatService.extractStationCode("01");
        assert null == heartbeatService.extractStationCode(null);
    }
}
