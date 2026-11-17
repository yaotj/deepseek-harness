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

    RequestQrLogicNumListRespDTO requestQrLogicNumList(RequestQrLogicNumListReqDTO request);


    RequestCaKeyRespDTO requestCaKey(RequestCaKeyReqDTO request);


    RequestUserSm2KeyRespDTO requestUserSm2Key(RequestUserSm2KeyReqDTO request);


    RequestSignPubkeyRespDTO requestSignPubkey(RequestSignPubkeyReqDTO request);


    RequestExportUserPriKeyRespDTO requestExportUserPriKey(RequestExportUserPriKeyReqDTO request);


    RequestSignInsDataRespDTO requestSignInsData(RequestSignInsDataReqDTO request);


    RequestDpkRespDTO requestDpk(RequestDpkReqDTO request);


    RequestHecCardDateRespDTO requestHecCardDate(RequestHecCardDateReqDTO request);
}
