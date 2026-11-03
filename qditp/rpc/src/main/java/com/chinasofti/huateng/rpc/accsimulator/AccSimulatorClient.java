package com.chinasofti.huateng.rpc.accsimulator;

import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.employee.AccEmployeeCardExportDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工卡 ACC 模拟器 RPC 客户端。
 */
@Service
public class AccSimulatorClient extends ProxyWebClient
{
    public AccSimulatorClient(@Value("${service.accSimulator.url:http://employee-card-acc-simulator-internal.itp.svc:8080}") String baseUrl,
                              @Value("${service.accSimulator.openLogger:true}") boolean openLogger,
                              WebClient.Builder webClientBuilder)
    {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 分页查询模拟员工卡（供异步导出使用）。
     */
    public List<AccEmployeeCardExportDTO> listAccSimulatorCards(String cardNo, String employeeName, Integer cardStatus)
    {
        Map<String, String> params = new HashMap<>();
        if (cardNo != null && !cardNo.isEmpty())
        {
            params.put("cardNo", cardNo);
        }
        if (employeeName != null && !employeeName.isEmpty())
        {
            params.put("employeeName", employeeName);
        }
        if (cardStatus != null)
        {
            params.put("cardStatus", String.valueOf(cardStatus));
        }
        int pageSize = 1000;
        int pageNum = 1;
        List<AccEmployeeCardExportDTO> all = new ArrayList<>();
        while (true)
        {
            params.put("pageNum", String.valueOf(pageNum));
            params.put("pageSize", String.valueOf(pageSize));
            String result = getAndGetResponse("/page/acc-simulator/cards", params, new HashMap<>());
            cn.hutool.json.JSONObject data = JSONUtil.parseObj(result).getJSONObject("data");
            if (data == null) {
                break;
            }
            List<AccEmployeeCardExportDTO> page = data.getBeanList("list", AccEmployeeCardExportDTO.class);
            if (page == null || page.isEmpty())
            {
                break;
            }
            all.addAll(page);
            if (page.size() < pageSize)
            {
                break;
            }
            pageNum++;
        }
        return all;
    }
}
