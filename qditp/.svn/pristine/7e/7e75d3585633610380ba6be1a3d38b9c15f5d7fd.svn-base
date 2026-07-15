package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.entity.fare.*;
import com.chinasofti.huateng.para.model.RateParseResult;
import com.chinasofti.huateng.para.util.ParaFileReadUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RateParser {
    private static final int HEADER_LENGTH = 22;
    private static final int MD5_LENGTH = 16;
    private static final String DEFAULT_UPDATE_USER = "acc";

    public RateParseResult parse(Path filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        Cursor cursor = new Cursor(fileBytes);
        RateParseResult result = new RateParseResult();
        result.setFilePath(filePath.toString());
        result.setFileName(filePath.getFileName().toString());
        result.setFileLength(fileBytes.length);
        Map<String, Object> header = readHeader(cursor);
        result.setHeader(header);
        long paraVerNo = Long.parseLong(header.get("paraVerNo").toString());

        byte[] body = cursor.read(fileBytes.length - HEADER_LENGTH - MD5_LENGTH);
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

    private void readBody(Cursor cursor, long paraVerNo, RateParseResult result) {
        LocalDateTime now = LocalDateTime.now();
        AddPara addPara = new AddPara();
        addPara.setParaVerNo(paraVerNo);
        addPara.setBankMinAmt(readInt(cursor));
        addPara.setBankMaxAmt(readInt(cursor));
        addPara.setLastUpdUser(DEFAULT_UPDATE_USER);
        addPara.setLastUpdTms(now);
        result.setAddPara(addPara);

        readFareMatrices(cursor, paraVerNo, result);
        readTicketFares(cursor, paraVerNo, now, result);
        readFareGroups(cursor, paraVerNo, now, result);
        readBaseFares(cursor, paraVerNo, result);
    }

    private void readFareMatrices(Cursor cursor, long paraVerNo, RateParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            FareMatrix row = new FareMatrix();
            row.setParaVerNo(paraVerNo);
            row.setBeginStatCode(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(4)).substring(0, 4));
            row.setEndStatCode(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(4)).substring(0, 4));
            row.setFareTier(readInt(cursor));
            result.getFareMatrices().add(row);
        }
    }

    private void readTicketFares(Cursor cursor, long paraVerNo, LocalDateTime now, RateParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            TicketFare row = new TicketFare();
            row.setParaVerNo(paraVerNo);
            row.setChipType(ParaFileReadUtils.leftPad(Integer.toHexString(ParaFileReadUtils.byteToInt(cursor.readOne())), 2, '0'));
            row.setTicketType(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setFareGroupNo(readInt(cursor));
            row.setLastUpdUser(DEFAULT_UPDATE_USER);
            row.setLastUpdTms(now);
            result.getTicketFares().add(row);
        }
    }

    private void readFareGroups(Cursor cursor, long paraVerNo, LocalDateTime now, RateParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            FareGroup row = new FareGroup();
            row.setParaVerNo(paraVerNo);
            row.setFareType(readInt(cursor));
            row.setDateType(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setIntervalNo(readInt(cursor));
            row.setFareGroup(readInt(cursor));
            row.setLastUpdUser(DEFAULT_UPDATE_USER);
            row.setLastUpdTms(now);
            result.getFareGroups().add(row);
        }
    }

    private void readBaseFares(Cursor cursor, long paraVerNo, RateParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            BaseFare row = new BaseFare();
            row.setParaVerNo(paraVerNo);
            row.setFareType(readInt(cursor));
            row.setFareTier(readInt(cursor));
            row.setTicketPrice(readInt(cursor));
            result.getBaseFares().add(row);
        }
    }

    private long readCount(Cursor cursor) { return ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)); }
    private int readInt(Cursor cursor) { return (int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)); }

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
        private Cursor(byte[] bytes) { this.bytes = bytes; }
        private byte[] read(int length) {
            if (position + length > bytes.length) {
                throw new IllegalArgumentException("参数文件内容不足, position=" + position + ", length=" + length + ", total=" + bytes.length);
            }
            byte[] result = new byte[length];
            System.arraycopy(bytes, position, result, 0, length);
            position += length;
            return result;
        }
        private byte readOne() { return read(1)[0]; }
        private int position() { return position; }
    }
}
