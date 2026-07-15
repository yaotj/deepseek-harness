package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.entity.network.LineInfo;
import com.chinasofti.huateng.para.entity.network.SectInfo;
import com.chinasofti.huateng.para.entity.network.StationInfo;
import com.chinasofti.huateng.para.entity.network.TsfInfo;
import com.chinasofti.huateng.para.entity.network.ZoneDtl;
import com.chinasofti.huateng.para.entity.network.ZoneInfo;
import com.chinasofti.huateng.para.model.RowNetworkParseResult;
import com.chinasofti.huateng.para.util.ParaFileReadUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路网拓扑参数文件本地解析。测试阶段只读取、解析、打印，不写入数据库。
 */
@Service
public class RowNetworkParser {

    private static final int HEADER_LENGTH = 22;
    private static final int MD5_LENGTH = 16;

    public RowNetworkParseResult parse(Path filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        if (fileBytes.length < HEADER_LENGTH + MD5_LENGTH) {
            throw new IllegalArgumentException("参数文件长度不足: " + fileBytes.length);
        }

        Cursor cursor = new Cursor(fileBytes);
        RowNetworkParseResult result = new RowNetworkParseResult();
        result.setFilePath(filePath.toString());
        result.setFileName(filePath.getFileName().toString());
        result.setFileLength(fileBytes.length);

        Map<String, Object> header = readHeader(cursor);
        result.setHeader(header);
        long paraVerNo = Long.parseLong(header.get("paraVerNo").toString());

        int bodyLength = fileBytes.length - HEADER_LENGTH - MD5_LENGTH;
        byte[] body = cursor.read(bodyLength);
        Cursor bodyCursor = new Cursor(body);
        readBody(bodyCursor, paraVerNo, result);
        result.setBodyReadBytes(bodyCursor.position());
        result.setBodyTotalBytes(body.length);

        byte[] md5Bytes = cursor.read(MD5_LENGTH);
        result.setMd5InFile(ParaFileReadUtils.bcdToHexString(md5Bytes));
        result.setMd5Calculated(md5Hex(fileBytes, 0, fileBytes.length - MD5_LENGTH));
        result.setMd5Valid(result.getMd5InFile().equalsIgnoreCase(result.getMd5Calculated()));
        return result;
    }

    private Map<String, Object> readHeader(Cursor cursor) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("fileType", Integer.toHexString(ParaFileReadUtils.byteToInt(cursor.readOne())));
        header.put("fileCreateDateTime", ParaFileReadUtils.bcdToString(cursor.read(7)));
        header.put("fileVerNo", Integer.toHexString(ParaFileReadUtils.byteToInt(cursor.readOne())));
        header.put("paraType", ParaFileReadUtils.leftPad(Integer.toHexString(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2))), 4, '0'));
        header.put("paraVerNo", String.valueOf(ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4))));
        header.put("validDateTime", ParaFileReadUtils.bcdToString(cursor.read(7)));
        return header;
    }

    private void readBody(Cursor cursor, long paraVerNo, RowNetworkParseResult result) {
        readLines(cursor, paraVerNo, result);
        readStations(cursor, paraVerNo, result);
        readTransfers(cursor, paraVerNo, result);
        readZones(cursor, paraVerNo, result);
        readSections(cursor, paraVerNo, result);
    }

    private void readLines(Cursor cursor, long paraVerNo, RowNetworkParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            LineInfo row = new LineInfo();
            row.setParaVerNo(paraVerNo);
            String nodeNo = readNodeNo(cursor);
            row.setLineCode(nodeNo.substring(0, 2));
            row.setLineENm(ParaFileReadUtils.readGb2312(cursor.read(40)));
            row.setLineNm(ParaFileReadUtils.readGb2312(cursor.read(40)));
            result.getLineInfos().add(row);
        }
    }

    private void readStations(Cursor cursor, long paraVerNo, RowNetworkParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            StationInfo row = new StationInfo();
            row.setParaVerNo(paraVerNo);
            String nodeNo = readNodeNo(cursor);
            row.setStationCode(nodeNo.substring(0, 4));
            row.setStationENm(ParaFileReadUtils.readGb2312(cursor.read(60)));
            row.setStationNm(ParaFileReadUtils.readGb2312(cursor.read(40)));
            nodeNo = readNodeNo(cursor);
            row.setOwnerLineId(nodeNo.substring(0, 2));
            row.setOwnerIncomeId("0");
            row.setStationType("0");
            result.getStationInfos().add(row);
        }
    }

    private void readTransfers(Cursor cursor, long paraVerNo, RowNetworkParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            TsfInfo row = new TsfInfo();
            row.setParaVerNo(paraVerNo);
            row.setFromStatCode(readNodeNo(cursor).substring(0, 4));
            row.setFromLineCode(readNodeNo(cursor).substring(0, 2));
            row.setToStatCode(readNodeNo(cursor).substring(0, 4));
            row.setToLineCode(readNodeNo(cursor).substring(0, 2));
            row.setTsfStationType(ParaFileReadUtils.leftPad(String.valueOf(ParaFileReadUtils.byteToInt(cursor.readOne())), 2, '0'));
            row.setTsfTime(60);
            row.setTsfDistance(0);
            result.getTsfInfos().add(row);
        }
    }

    private void readZones(Cursor cursor, long paraVerNo, RowNetworkParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            ZoneInfo zone = new ZoneInfo();
            zone.setParaVerNo(paraVerNo);
            zone.setZoneNo((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
            zone.setZoneEName(ParaFileReadUtils.readGb2312(cursor.read(40)));
            zone.setZoneName(ParaFileReadUtils.readGb2312(cursor.read(40)));
            result.getZoneInfos().add(zone);

            long detailSize = readCount(cursor);
            for (int j = 0; j < detailSize; j++) {
                ZoneDtl detail = new ZoneDtl();
                detail.setParaVerNo(paraVerNo);
                detail.setZoneNo(zone.getZoneNo());
                detail.setSeqNo(j + 1);
                detail.setStationCode(readNodeNo(cursor).substring(0, 4));
                result.getZoneDtls().add(detail);
            }
        }
    }

    private void readSections(Cursor cursor, long paraVerNo, RowNetworkParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            SectInfo row = new SectInfo();
            row.setParaVerNo(paraVerNo);
            row.setSectNo((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
            row.setSectEName(ParaFileReadUtils.readGb2312(cursor.read(40)));
            row.setSectName(ParaFileReadUtils.readGb2312(cursor.read(40)));
            row.setStatCode1(readNodeNo(cursor).substring(0, 4));
            row.setStatCode2(readNodeNo(cursor).substring(0, 4));
            result.getSectInfos().add(row);
        }
    }

    private long readCount(Cursor cursor) {
        return ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4));
    }

    private String readNodeNo(Cursor cursor) {
        return ParaFileReadUtils.leftPad(ParaFileReadUtils.bcdToString(cursor.read(4)), 8, '0');
    }

    private String md5Hex(byte[] bytes, int offset, int length) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(bytes, offset, length);
            byte[] digest = md5.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not found", e);
        }
    }

    private static class Cursor {
        private final byte[] bytes;
        private int position;

        private Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        private byte[] read(int length) {
            if (position + length > bytes.length) {
                throw new IllegalArgumentException("参数文件内容不足, position=" + position + ", length=" + length + ", total=" + bytes.length);
            }
            byte[] result = new byte[length];
            System.arraycopy(bytes, position, result, 0, length);
            position += length;
            return result;
        }

        private byte readOne() {
            return read(1)[0];
        }

        private int position() {
            return position;
        }
    }
    
}
