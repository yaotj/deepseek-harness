package com.chinasofti.huateng.fep.dev.keysync;

import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.model.KeyCurVerReqDTO;
import com.chinasofti.huateng.fep.dev.model.KeyCurVerRespDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestSynKeyListRespDTO;
import com.chinasofti.huateng.model.agm.AgmKeyCurVerDTO;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListReqDTO;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListResult;
import com.chinasofti.huateng.rpc.key.KeyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** IF1A-02 密钥同步处理。 */
@Component
public class KeySyncHandler {
    private static final Logger log = LoggerFactory.getLogger(KeySyncHandler.class);

    private final KeyClient keyClient;

    public KeySyncHandler(KeyClient keyClient) {
        this.keyClient = keyClient;
    }

    /**
     * IF1A-02 密钥同步。
     *
     * @param request 密钥同步业务参数
     * @param deviceId 设备编号
     * @param requestBizData 原始请求业务数据
     * @return 密钥同步响应
     */
    public RequestSynKeyListRespDTO requestSynKeyList(RequestSynKeyListReqDTO request, String deviceId, String requestBizData) {
        RequestSynKeyListRespDTO response = new RequestSynKeyListRespDTO();
        List<KeyCurVerReqDTO> requestList = request.getKeyCurVerList();
        if (requestList == null || requestList.isEmpty()) {
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("keyCurVerList不能为空");
            return response;
        }

        try {
            RequestAgmSynKeyListReqDTO keyServerRequest = new RequestAgmSynKeyListReqDTO();
            keyServerRequest.setDeviceId(deviceId);
            keyServerRequest.setRequestBizData(requestBizData);
            keyServerRequest.setKeyCurVerList(toAgmKeyCurVerList(requestList));

            RequestAgmSynKeyListResult keyServerResult = keyClient.requestAgmSynKeyList(keyServerRequest);
            if (keyServerResult == null) {
                response.setRetCode(FepDevErrorCodeEnum.SYSTEM_ERROR.getCode());
                response.setRetMsg("密钥服务无响应");
                return response;
            }
            response.setRetCode(keyServerResult.getRetCode());
            response.setRetMsg(keyServerResult.getRetMsg());
            response.setKeyCurVerList(toFepKeyCurVerList(keyServerResult.getKeyCurVerList()));
            log.info("IF1A-02 key-server处理完成, deviceId={}, retCode={}, keyVersionCount={}",
                    deviceId, response.getRetCode(), response.getKeyCurVerList() == null ? 0 : response.getKeyCurVerList().size());
            return response;
        } catch (Exception e) {
            log.error("IF1A-02 调用key-server同步AGM密钥异常, deviceId={}", deviceId, e);
            response.setRetCode(FepDevErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("密钥同步服务异常");
            return response;
        }
    }

    /** 将 fep 内部密钥版本请求 DTO 转换为 key-server 要求的 AgmKeyCurVerDTO 格式。 */
    private List<AgmKeyCurVerDTO> toAgmKeyCurVerList(List<KeyCurVerReqDTO> requestList) {
        List<AgmKeyCurVerDTO> result = new ArrayList<>(requestList.size());
        for (KeyCurVerReqDTO item : requestList) {
            AgmKeyCurVerDTO target = new AgmKeyCurVerDTO();
            target.setIssueChannelCode(item.getIssueChannelCode());
            target.setKeyId(item.getKeyId());
            target.setKeyBathNumber(item.getKeyBathNumber());
            result.add(target);
        }
        return result;
    }

    /** 将 key-server 返回的 AgmKeyCurVerDTO 转换为 fep 内部密钥版本响应 DTO。 */
    private List<KeyCurVerRespDTO> toFepKeyCurVerList(List<AgmKeyCurVerDTO> keyCurVerList) {
        if (keyCurVerList == null || keyCurVerList.isEmpty()) {
            return Collections.emptyList();
        }
        List<KeyCurVerRespDTO> result = new ArrayList<>(keyCurVerList.size());
        for (AgmKeyCurVerDTO item : keyCurVerList) {
            KeyCurVerRespDTO respItem = new KeyCurVerRespDTO();
            respItem.setIssueChannelCode(item.getIssueChannelCode());
            respItem.setKeyId(item.getKeyId());
            respItem.setKeyBathNumber(item.getKeyBathNumber());
            respItem.setNeedUpdateYN(item.getNeedUpdateYN());
            respItem.setKeyList(item.getKeyList());
            result.add(respItem);
        }
        return result;
    }
}
