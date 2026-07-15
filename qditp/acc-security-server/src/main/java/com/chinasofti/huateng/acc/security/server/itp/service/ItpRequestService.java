package com.chinasofti.huateng.acc.security.server.itp.service;

import com.chinasofti.huateng.acc.security.server.exception.HsmUnavailableException;
import com.chinasofti.huateng.acc.security.server.itp.model.ItpResponseModel;
import com.chinasofti.huateng.acc.security.server.itp.model.ItpSignInsDataResponse;
import com.chinasofti.huateng.acc.security.server.itp.model.ItpSignPubkeyResponse;
import com.chinasofti.huateng.acc.security.server.itp.util.ItpConstants;
import com.chinasofti.huateng.acc.security.server.itp.util.ItpHexUtils;
import com.chinasofti.huateng.acc.security.server.socket.ClientSendMsg;
import com.chinasofti.huateng.acc.security.server.socket.RawResponseFieldSpec;
import com.chinasofti.huateng.acc.security.server.socket.RawResponseSpec;
import com.chinasofti.huateng.acc.security.server.socket.RawSocketResponse;
import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ItpRequestService {
    private static final Logger log = LoggerFactory.getLogger(ItpRequestService.class);

    private final ClientSendMsg clientSendMsg;

    @Value("${itp.tacKeyIdex:195}")
    private int tacKeyIdex;

    public ItpRequestService(ClientSendMsg clientSendMsg) {
        this.clientSendMsg = clientSendMsg;
    }

    public ResultVO requestSignPubkey(Map<String, String> param) {
        byte[] sendMsg;
        try {
            sendMsg = buildSignPubkeyRequest(param);
        } catch (NumberFormatException e) {
            log.error("hex string is odd", e);
            return ResultMapper.error("hex string format error");
        } catch (UnsupportedOperationException e) {
            log.error("hex string format err", e);
            return ResultMapper.error("hexString format err");
        } catch (IOException e) {
            log.error("byte array out exception", e);
            return ResultMapper.error("byte array out exception");
        }

        try {
            RawSocketResponse response = clientSendMsg.sendRawMsg(sendMsg, new RawResponseSpec(false, List.of(RawResponseFieldSpec.fixed(32), RawResponseFieldSpec.fixed(32))));
            if (response.ansCode() != 'A') {
                return errorResponse(response);
            }
            ItpSignPubkeyResponse result = new ItpSignPubkeyResponse();
            result.setSignData(TransformUtils.bytesToHex(response.fields().get(0)) + TransformUtils.bytesToHex(response.fields().get(1)));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("request sign pubkey hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (Exception e) {
            log.error("request sign pubkey failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    public ResultVO requestSignInsData(Map<String, String> param) {
        byte[] sendMsg;
        try {
            sendMsg = buildSignInsDataRequest(param);
        } catch (NumberFormatException e) {
            log.error("sign ins data number format err", e);
            return ResultMapper.error("hexString length odd");
        } catch (UnsupportedOperationException e) {
            log.error("hex string format err", e);
            return ResultMapper.error("hexString format err");
        } catch (IOException e) {
            log.error("byte array out exception", e);
            return ResultMapper.error("byte array out exception");
        }

        try {
            RawSocketResponse response = clientSendMsg.sendRawMsg(sendMsg, new RawResponseSpec(true, List.of(RawResponseFieldSpec.fixed(8))));
            if (response.ansCode() != 'A') {
                return errorResponse(response);
            }
            ItpSignInsDataResponse result = new ItpSignInsDataResponse();
            String tac = TransformUtils.bytesToHex(response.fields().get(0));
            result.setIndustryDataSign(tac.substring(0, 8));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("request sign ins data hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (Exception e) {
            log.error("request sign ins data failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    private byte[] buildSignPubkeyRequest(Map<String, String> param) throws IOException {
        String caSm2KeyPair = param.get("caSm2KeyPair");
        String publicKeyX = param.get("publicKeyX");
        String publicKeyEffectiveDate = param.get("publicKeyEffectiveDate");
        String userId = param.get("userId");
        String strData = publicKeyX + publicKeyEffectiveDate + userId;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new byte[]{(byte) 0xD3, 0x06});
        bos.write(new byte[]{(byte) 0xFF, (byte) 0xFF});

        int extKeyLen = caSm2KeyPair.length() / 2;
        bos.write(new byte[]{(byte) ((extKeyLen >> 8) & 0xFF), (byte) (extKeyLen & 0xFF)});
        bos.write(ItpHexUtils.hexStringToByte(caSm2KeyPair));
        bos.write(new byte[]{0x01});
        bos.write(new byte[]{0x02});

        int userIdLen = userId.length() / 2;
        bos.write(new byte[]{(byte) ((userIdLen >> 8) & 0xFF), (byte) (userIdLen & 0xFF)});
        bos.write(ItpHexUtils.hexStringToByte(userId));

        int strDataLen = strData.length() / 2;
        bos.write(new byte[]{(byte) ((strDataLen >> 8) & 0xFF), (byte) (strDataLen & 0xFF)});
        bos.write(ItpHexUtils.hexStringToByte(strData));
        bos.flush();
        return bos.toByteArray();
    }

    private byte[] buildSignInsDataRequest(Map<String, String> param) throws IOException {
        String industryData = param.get("industryData");
        String logicNum = param.get("logicNum");

        if (logicNum == null || logicNum.length() != 16) {
            throw new NumberFormatException("ticket id length not equal 16");
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new byte[]{(byte) 0xB0, (byte) 0x83, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) ((tacKeyIdex & 0xFF00) >> 8), (byte) (tacKeyIdex & 0xFF), 0x01});
        bos.write(ItpHexUtils.hexStringToByte(logicNum));
        bos.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});

        int industryDataLen = industryData.length() / 2;
        bos.write(new byte[]{(byte) ((industryDataLen >> 8) & 0xFF), (byte) (industryDataLen & 0xFF)});
        bos.write(ItpHexUtils.hexStringToByte(industryData));
        bos.flush();
        return bos.toByteArray();
    }

    private ResultVO errorResponse(RawSocketResponse response) {
        String err = response.errCode() == null ? "" : TransformUtils.bytesToHex(response.errCode());
        return ResultMapper.error("errcode = " + err);
    }
}
