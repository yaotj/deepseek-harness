package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;

public interface ContractGatewayPort {

    GatewayReply requestContract(RequestSignInfoReqDTO request, String paymentVendor);

    GatewayReply creditQuery(String thirdUserId, String requestSignSeq, String paymentVendor);

    GatewayReply queryContractResult(String requestSignSeq);

    GatewayReply requestDismissal(String requestSignSeq);
}
