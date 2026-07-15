package com.chinasofti.huateng.accsecure.service;

import com.chinasofti.huateng.accsecure.model.request.RequestCaKeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestDpkReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestExportUserPriKeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestHecCardDateReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestQrLogicNumListReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestSignInsDataReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestSignPubkeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestUserSm2KeyReqDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestCaKeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestDpkRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestExportUserPriKeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestHecCardDateRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestQrLogicNumListRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestSignInsDataRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestSignPubkeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestUserSm2KeyRespDTO;

public interface AccSecureService {
    /**
     * 调用文档 IF7B-01，请求 ACC 生成或下发逻辑卡号批次。
     */
    RequestQrLogicNumListRespDTO requestQrLogicNumList(RequestQrLogicNumListReqDTO request);

    /**
     * 调用文档 IF7B-02，请求 ACC 生成地铁 CA 密钥。
     */
    RequestCaKeyRespDTO requestCaKey(RequestCaKeyReqDTO request);

    /**
     * 调用文档 IF7B-03，请求 ACC 生成用户 SM2 密钥对。
     */
    RequestUserSm2KeyRespDTO requestUserSm2Key(RequestUserSm2KeyReqDTO request);

    /**
     * 调用文档 IF7B-04，请求 ACC 对用户公钥进行签名。
     */
    RequestSignPubkeyRespDTO requestSignPubkey(RequestSignPubkeyReqDTO request);

    /**
     * 调用文档 IF7B-05，请求 ACC 按约定 KEK 导出用户私钥。
     */
    RequestExportUserPriKeyRespDTO requestExportUserPriKey(RequestExportUserPriKeyReqDTO request);

    /**
     * 调用文档 IF7B-06，请求 ACC 对行业数据进行签名。
     */
    RequestSignInsDataRespDTO requestSignInsData(RequestSignInsDataReqDTO request);

    /**
     * 调用文档 IF7B-07，请求 ACC 返回 HCE 卡片消费子密钥。
     */
    RequestDpkRespDTO requestDpk(RequestDpkReqDTO request);

    /**
     * 调用文档 IF7B-08，请求 ACC 发售 HCE 单程票数据。
     */
    RequestHecCardDateRespDTO requestHecCardDate(RequestHecCardDateReqDTO request);
}
