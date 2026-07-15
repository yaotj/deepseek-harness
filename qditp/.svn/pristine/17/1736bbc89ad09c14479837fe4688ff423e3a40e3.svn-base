package com.chinasofti.huateng.acc.security.server.itp.service.impl;

import com.chinasofti.huateng.acc.security.server.exception.HsmUnavailableException;
import com.chinasofti.huateng.acc.security.server.itp.model.*;
import com.chinasofti.huateng.acc.security.server.itp.service.ItpLogicNumberService;
import com.chinasofti.huateng.acc.security.server.itp.service.ItpService;
import com.chinasofti.huateng.acc.security.server.itp.util.ItpCardUtils;
import com.chinasofti.huateng.acc.security.server.itp.util.ItpConstants;
import com.chinasofti.huateng.acc.security.server.itp.util.ItpHexUtils;
import com.chinasofti.huateng.acc.security.server.itp.util.ItpPboc3DesMacUtils;
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
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ITP接口统一实现。
 */
@Service
public class ItpServiceImpl implements ItpService {
    private static final Logger log = LoggerFactory.getLogger(ItpServiceImpl.class);

    private final ClientSendMsg clientSendMsg;
    private final ItpLogicNumberService logicNumberService;

    @Value("${itp.kekIdex:2}")
    private int kekIdex;
    @Value("${itp.mac1KeyIdex:192}")
    private int mac1KeyIdex;
    @Value("${itp.mac2KeyIdex:193}")
    private int mac2KeyIdex;
    @Value("${itp.tacKeyIdex:195}")
    private int tacKeyIdex;
    @Value("${itp.dpkIdex:178}")
    private int dpkIdex;

    public ItpServiceImpl(ClientSendMsg clientSendMsg, ItpLogicNumberService logicNumberService) {
        this.clientSendMsg = clientSendMsg;
        this.logicNumberService = logicNumberService;
    }

    @Override
    public ResultVO requestCaKey(Map<String, String> param) {
        try {
            String keyIdx = param.get("keyIdx");
            if (keyIdx == null || keyIdx.length() != 4) {
                return ResultMapper.error("keyIdx format err");
            }
            RawSocketResponse response = clientSendMsg.sendRawMsg(
                    new byte[]{(byte) 0xD3, 0x02, 0x01, 0x01, 0x00, (byte) 0xFF, (byte) 0xFF},
                    new RawResponseSpec(false, List.of(
                            RawResponseFieldSpec.fixed(2),
                            RawResponseFieldSpec.dynamic(0),
                            RawResponseFieldSpec.fixed(32),
                            RawResponseFieldSpec.fixed(32),
                            RawResponseFieldSpec.fixed(32))));
            if (response.ansCode() != 'A') {
                return errorResponse(response);
            }
            ItpCaKeyResponse result = new ItpCaKeyResponse();
            result.setSm2KeyPair(hexField(response, 1));
            result.setPublicKey(hexField(response, 2) + hexField(response, 3));
            result.setPrivateKey(hexField(response, 4));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("requestCaKey hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (Exception e) {
            log.error("requestCaKey failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    @Override
    public ResultVO requestUserSm2Key(Map<String, String> param) {
        try {
            RawSocketResponse response = clientSendMsg.sendRawMsg(
                    new byte[]{(byte) 0xD3, 0x02, 0x01, 0x01, 0x00, (byte) 0xFF, (byte) 0xFF},
                    new RawResponseSpec(false, List.of(
                            RawResponseFieldSpec.fixed(2),
                            RawResponseFieldSpec.dynamic(0),
                            RawResponseFieldSpec.fixed(32),
                            RawResponseFieldSpec.fixed(32),
                            RawResponseFieldSpec.fixed(32))));
            if (response.ansCode() != 'A') {
                return errorResponse(response);
            }
            ItpUserSm2KeyResponse result = new ItpUserSm2KeyResponse();
            result.setSm2KeyPair(hexField(response, 1));
            result.setPublicKey(hexField(response, 2) + hexField(response, 3));
            result.setPrivateKey(hexField(response, 4));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("requestUserSm2Key hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (Exception e) {
            log.error("requestUserSm2Key failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    @Override
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
            RawSocketResponse response = clientSendMsg.sendRawMsg(sendMsg,
                    new RawResponseSpec(false, List.of(RawResponseFieldSpec.fixed(32), RawResponseFieldSpec.fixed(32))));
            if (response.ansCode() != 'A') {
                return errorResponse(response);
            }
            ItpSignPubkeyResponse result = new ItpSignPubkeyResponse();
            result.setSignData(TransformUtils.bytesToHex(response.fields().get(0))
                    + TransformUtils.bytesToHex(response.fields().get(1)));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("requestSignPubkey hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (Exception e) {
            log.error("requestSignPubkey failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    @Override
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
            RawSocketResponse response = clientSendMsg.sendRawMsg(sendMsg,
                    new RawResponseSpec(true, List.of(RawResponseFieldSpec.fixed(8))));
            if (response.ansCode() != 'A') {
                return errorResponse(response);
            }
            ItpSignInsDataResponse result = new ItpSignInsDataResponse();
            String tac = TransformUtils.bytesToHex(response.fields().get(0));
            result.setIndustryDataSign(tac.substring(0, 8));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("requestSignInsData hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (Exception e) {
            log.error("requestSignInsData failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    @Override
    public ResultVO requestExportUserPriKey(Map<String, String> param) {
        try {
            String privateKey = param.get("privateKey");
            if (privateKey == null || privateKey.length() != 64) {
                return ResultMapper.error("privateKey format err");
            }
            byte[] privateKeyArr = ItpHexUtils.hexStringToByte(privateKey);
            byte[] request = new byte[]{0x04, 0x01, (byte) 0xFF, (byte) 0xFF, 0x01, 0x10,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    (byte) ((kekIdex >> 8) & 0xFF), (byte) (kekIdex & 0xFF), 0x01};

            System.arraycopy(privateKeyArr, 0, request, 6, 16);
            RawSocketResponse first = clientSendMsg.sendRawMsg(request,
                    new RawResponseSpec(false, List.of(RawResponseFieldSpec.fixed(1), RawResponseFieldSpec.dynamic(0))));
            if (first.ansCode() != 'A') {
                return errorResponse(first);
            }

            System.arraycopy(privateKeyArr, 16, request, 6, 16);
            RawSocketResponse second = clientSendMsg.sendRawMsg(request,
                    new RawResponseSpec(false, List.of(RawResponseFieldSpec.fixed(1), RawResponseFieldSpec.dynamic(0))));
            if (second.ansCode() != 'A') {
                return errorResponse(second);
            }

            ItpExportPriKeyResponse result = new ItpExportPriKeyResponse();
            result.setUserPrivateKeyByKes(hexField(first, 1) + hexField(second, 1));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("requestExportUserPriKey hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (UnsupportedOperationException e) {
            log.error("hexString format err", e);
            return ResultMapper.error("hexString format err");
        } catch (Exception e) {
            log.error("requestExportUserPriKey failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    @Override
    public ResultVO requestDPK(Map<String, String> param) {
        try {
            String logicNum = param.get("logicNum");
            if (logicNum == null || logicNum.length() != 16) {
                return ResultMapper.error("ticket id length not equal 16");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(new byte[]{(byte) 0xB0, (byte) 0x61, 0, 0, 0, 0, 0, 0, 0, 0,
                    (byte) ((dpkIdex >> 8) & 0xFF), (byte) (dpkIdex & 0xFF),
                    0x02, 0x05, 0x32, (byte) 0xFF, 0, 0, 0, 0, 0});
            bos.write(ItpHexUtils.hexStringToByte(logicNum));
            RawSocketResponse b061 = clientSendMsg.sendRawMsg(bos.toByteArray(),
                    new RawResponseSpec(true, List.of(RawResponseFieldSpec.fixed(16), RawResponseFieldSpec.fixed(4))));
            if (b061.ansCode() != 'A') {
                return errorResponse(b061);
            }

            bos = new ByteArrayOutputStream();
            bos.write(new byte[]{0x72, (byte) ((kekIdex >> 8) & 0xFF), (byte) (kekIdex & 0xFF),
                    0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0x00, 0x00, 0x10});
            bos.write(b061.fields().get(0));
            RawSocketResponse hsm74 = clientSendMsg.sendRawMsg(bos.toByteArray(),
                    new RawResponseSpec(false, List.of(RawResponseFieldSpec.fixed(2), RawResponseFieldSpec.fixed(16))));
            if (hsm74.ansCode() != 'A') {
                return errorResponse(hsm74);
            }

            ItpDpkResponse result = new ItpDpkResponse();
            result.setDpkByKek(hexField(hsm74, 1));
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("requestDPK hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (Exception e) {
            log.error("requestDPK failed", e);
            return ResultMapper.error("get socket timeout");
        }
    }

    @Override
    public ResultVO requestHceCardData(Map<String, String> param) {
        try {
            String iptUserId = param.get("iptUserId");
            if (iptUserId == null || iptUserId.length() != 14) {
                return ResultMapper.error("csnId format err");
            }
            byte[] csnId = ItpHexUtils.hexStringToByte(iptUserId);
            byte[] singleData = buildBaseCardData(csnId, param.get("ticketCard"));

            RawSocketResponse mac1 = clientSendMsg.sendRawMsg(buildMac1Request(singleData, csnId),
                    new RawResponseSpec(true, List.of(RawResponseFieldSpec.fixed(2), RawResponseFieldSpec.dynamic(0))));
            if (mac1.ansCode() != 'A') {
                return errorResponse(mac1);
            }
            System.arraycopy(mac1.fields().get(1), 0, singleData, 24, 4);

            RawSocketResponse dis = clientSendMsg.sendRawMsg(buildDisRequest(singleData, csnId),
                    new RawResponseSpec(true, List.of(RawResponseFieldSpec.fixed(2), RawResponseFieldSpec.dynamic(0))));
            if (dis.ansCode() != 'A') {
                return errorResponse(dis);
            }

            byte[] disMac = new byte[16];
            System.arraycopy(dis.fields().get(1), 0, disMac, 0, 6);
            disMac[6] = 0x05;
            disMac[7] = 0x32;
            for (int i = 0; i < 8; i++) {
                disMac[i + 8] = (byte) ~disMac[i];
            }

            byte[] mac2Data = new byte[14];
            System.arraycopy(singleData, 28, mac2Data, 0, 14);
            byte[] mac2Tmp = ItpPboc3DesMacUtils.calculatePboc3desMAC(mac2Data, disMac, ItpPboc3DesMacUtils.ZERO_IVC);
            byte[] mac2 = new byte[]{(byte) (mac2Tmp[0] ^ mac2Tmp[2]), (byte) (mac2Tmp[1] ^ mac2Tmp[3])};
            System.arraycopy(mac2, 0, singleData, 42, 2);

            byte[] mac3Data = new byte[16];
            System.arraycopy(singleData, 44, mac3Data, 0, 16);
            byte[] mac3 = ItpPboc3DesMacUtils.calculatePboc3desMAC(mac3Data, disMac, ItpPboc3DesMacUtils.ZERO_IVC);
            System.arraycopy(mac3, 0, singleData, 60, 4);

            String logicNum = logicNumberService.nextLogicNumber();
            if (logicNum == null || logicNum.length() != 16) {
                return ResultMapper.error("get logic err");
            }
            System.arraycopy(ItpCardUtils.getIssueDate(logicNum.substring(2, 8)), 0, singleData, 18, 2);
            System.arraycopy(ItpHexUtils.hexStringToByte(logicNum.substring(8)), 0, singleData, 20, 4);

            ItpHceCardResponse result = new ItpHceCardResponse();
            result.setHceData(TransformUtils.bytesToHex(singleData));
            result.setLogicNum(logicNum);
            return ResultMapper.ok(result);
        } catch (HsmUnavailableException e) {
            log.error("requestHceCardData hsm unavailable", e);
            return ResultMapper.hsmUnavailable();
        } catch (UnsupportedOperationException e) {
            log.error("hexString format err", e);
            return ResultMapper.error("hexString format err");
        } catch (Exception e) {
            log.error("requestHceCardData failed", e);
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
        bos.write(new byte[]{(byte) 0xB0, (byte) 0x83, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x01, 0x00, (byte) ((tacKeyIdex & 0xFF00) >> 8), (byte) (tacKeyIdex & 0xFF), 0x01});
        bos.write(ItpHexUtils.hexStringToByte(logicNum));
        bos.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});

        int industryDataLen = industryData.length() / 2;
        bos.write(new byte[]{(byte) ((industryDataLen >> 8) & 0xFF), (byte) (industryDataLen & 0xFF)});
        bos.write(ItpHexUtils.hexStringToByte(industryData));
        bos.flush();
        return bos.toByteArray();
    }

    private byte[] buildBaseCardData(byte[] csnId, String ticketCard) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(csnId, 0, 3);
        out.write(new byte[]{0});
        out.write(csnId, 3, 4);
        out.write(new byte[]{0, 0, (byte) 0xFF, (byte) 0xFF});
        out.write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        out.write("02".equals(ticketCard) ? new byte[]{0x02, 0x04} : new byte[]{0x01, 0x04});
        out.write(new byte[]{0, 0, 0, 0, 0, 0});
        out.write(new byte[]{0, 0, 0, 0});
        out.write(new byte[]{0x42, 0x03, 0, 0});
        out.write(new byte[]{0, 0, 0, 0});
        long ticketTime = new Date().getTime() / 1000 - ItpCardUtils.fixedDateSecond();
        out.write(intToBytes((int) (ticketTime & 0xffffffffL)));
        out.write(new byte[]{0x01, (byte) 0xFF, 0, 0});
        out.write(new byte[20]);
        return out.toByteArray();
    }

    private byte[] buildMac1Request(byte[] singleData, byte[] csnId) {
        byte[] request = new byte[]{
                (byte) 0xB0, 0x11, 0, 0, 0, 0, 0, 0, 0, 0,
                (byte) ((mac1KeyIdex >> 8) & 0xFF), (byte) (mac1KeyIdex & 0xFF),
                0, 0x08, singleData[16], 0, 0, 0, 0, 0, 0, 0
        };
        System.arraycopy(csnId, 0, request, 15, 7);
        return request;
    }

    private byte[] buildDisRequest(byte[] singleData, byte[] csnId) {
        byte[] request = new byte[]{
                (byte) 0xB0, (byte) 0x91, 0, 0, 0, 0, 0, 0, 0, 0,
                (byte) ((mac2KeyIdex >> 8) & 0xFF), (byte) (mac2KeyIdex & 0xFF),
                0x00, 0x00, 0x08, 0, 0, 0, 0, 0, 0, singleData[24], singleData[0]
        };
        System.arraycopy(csnId, 1, request, 15, 6);
        return request;
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    private String hexField(RawSocketResponse response, int index) {
        return TransformUtils.bytesToHex(response.fields().get(index));
    }

    private ResultVO errorResponse(RawSocketResponse response) {
        String err = response.errCode() == null ? "" : TransformUtils.bytesToHex(response.errCode());
        return ResultMapper.error("errcode = " + err);
    }
}
