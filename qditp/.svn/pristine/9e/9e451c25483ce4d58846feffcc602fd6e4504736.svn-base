package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.entity.ticket.ChipType;
import com.chinasofti.huateng.para.entity.ticket.TicketType;
import com.chinasofti.huateng.para.entity.ticket.TotalSalePart;
import com.chinasofti.huateng.para.model.TicketParseResult;
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
public class TicketParser {
    private static final int HEADER_LENGTH = 22;
    private static final int MD5_LENGTH = 16;
    private static final String DEFAULT_UPDATE_USER = "acc";

    public TicketParseResult parse(Path filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        Cursor cursor = new Cursor(fileBytes);
        TicketParseResult result = new TicketParseResult();
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

    private void readBody(Cursor cursor, long paraVerNo, TicketParseResult result) {
        LocalDateTime now = LocalDateTime.now();
        result.setSingleTicketKeyVersion((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
        readChipTypes(cursor, paraVerNo, now, result);
        readTicketTypes(cursor, paraVerNo, now, result);
    }

    private void readChipTypes(Cursor cursor, long paraVerNo, LocalDateTime now, TicketParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            ChipType row = new ChipType();
            row.setParaVerNo(paraVerNo);
            row.setChipType(ParaFileReadUtils.leftPad(Integer.toHexString(ParaFileReadUtils.byteToInt(cursor.readOne())), 2, '0'));
            row.setDurationDays((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
            row.setMaxIoTimes((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
            row.setLastUpdUser(DEFAULT_UPDATE_USER);
            row.setLastUpdTms(now);
            result.getChipTypes().add(row);
        }
    }

    private void readTicketTypes(Cursor cursor, long paraVerNo, LocalDateTime now, TicketParseResult result) {
        long size = readCount(cursor);
        int totalSalePartCount = 0;
        for (int i = 0; i < size; i++) {
            TicketType row = new TicketType();
            row.setParaVerNo(paraVerNo);
            row.setTicketType(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setTicketDescChPtr(ParaFileReadUtils.readGb2312(cursor.read(32)));
            row.setTicketDescEn(ParaFileReadUtils.readGb2312(cursor.read(32)));
            row.setTicketMainType(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setIssuerType(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setPriceType(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setAreaFlag(ParaFileReadUtils.leftPad(String.valueOf(ParaFileReadUtils.byteToInt(cursor.readOne())), 2, '0'));
            row.setPassengerTime(readInt(cursor));
            row.setSaleAmt(readInt(cursor));
            row.setSignTerm(ParaFileReadUtils.byteToBinary(cursor.readOne()) + ParaFileReadUtils.byteToBinary(cursor.readOne()));
            row.setReturnTicketBatch(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(5)));
            row.setMaxUseNum(readInt(cursor));
            row.setMaxUseDays(readInt(cursor));
            row.setSoundType(readInt(cursor));
            row.setLampColorType(readInt(cursor));
            row.setAddMinValue(readInt(cursor));
            row.setMaxRemainValue(readInt(cursor));
            row.setEnterMinValue(readInt(cursor));
            row.setExtMinValue(readInt(cursor));
            row.setDepAmt(readInt(cursor));
            row.setDepreciationAmt(readInt(cursor));
            row.setMaxReturnAmt(readInt(cursor));
            row.setReturnFee(readInt(cursor));
            row.setMaxTimes(readInt(cursor));
            row.setMaxTimesCycle(0);
            row.setDurationType(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setDurationDays(readInt(cursor));
            row.setActiveValidDays(readInt(cursor));
            row.setFixEndDate(ParaFileReadUtils.leftPad(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(4)), 8, '0'));
            row.setMaxReentrPeriod(readInt(cursor));
            row.setMaxReentrFee(readInt(cursor));
            row.setOvertimeFee(readInt(cursor));
            row.setNoExitFee(readInt(cursor));
            row.setMismatchFee(readInt(cursor));
            row.setTwiceTxnTime(readInt(cursor));
            row.setSaleWay(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(8)));
            row.setContSafTime(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2)));
            row.setContSafErrorTime(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2)));
            row.setContAmt(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2)));
            row.setTotalSaleWay(ParaFileReadUtils.leftPad(Integer.toHexString(ParaFileReadUtils.byteToInt(cursor.readOne())), 2, '0'));
            row.setUnfinishedChargeAmt(readInt(cursor));
            row.setInterconnUpdateCtrl(ParaFileReadUtils.byteToInt(cursor.readOne()));
            row.setLastUpdUser(DEFAULT_UPDATE_USER);
            row.setLastUpdTms(now);
            fillStringDefaults(row);
            result.getTicketTypes().add(row);

            long partSize = readCount(cursor);
            totalSalePartCount += (int) partSize;
            for (int j = 0; j < partSize; j++) {
                TotalSalePart part = new TotalSalePart();
                part.setParaVerNo(paraVerNo);
                part.setTicketType(row.getTicketType());
                part.setPartSeqNo(j + 1);
                part.setBeginTotalSale(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2)));
                part.setEndTotalSale(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2)));
                part.setSaleRatio(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2)));
                part.setLastUpdUser(DEFAULT_UPDATE_USER);
                part.setLastUpdTms(now);
                result.getTotalSaleParts().add(part);
            }
            cursor.read(32);
        }
        result.setTotalSalePartCount(totalSalePartCount);
    }

    private long readCount(Cursor cursor) {
        return ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4));
    }

    private int readInt(Cursor cursor) {
        return (int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4));
    }

    private void fillStringDefaults(TicketType row) {
        row.setTicketDescChPtr(defaultString(row.getTicketDescChPtr()));
        row.setTicketDescEn(defaultString(row.getTicketDescEn()));
        row.setAreaFlag(defaultString(row.getAreaFlag()));
        row.setSignTerm(defaultString(row.getSignTerm()));
        row.setReturnTicketBatch(defaultString(row.getReturnTicketBatch()));
        row.setFixEndDate(defaultString(row.getFixEndDate()));
        row.setSaleWay(defaultString(row.getSaleWay()));
        row.setTotalSaleWay(defaultString(row.getTotalSaleWay()));
    }

    private String defaultString(String value) {
        return value == null || value.isBlank() ? "0" : value;
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
