package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.entity.calendar.FareTime;
import com.chinasofti.huateng.para.entity.calendar.SpecialDate;
import com.chinasofti.huateng.para.entity.calendar.TimeInterval;
import com.chinasofti.huateng.para.model.CalendarParseResult;
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

/**
 * 日历参数文件解析。
 */
@Service
public class CalendarParser {

    private static final int HEADER_LENGTH = 22;
    private static final int MD5_LENGTH = 16;
    private static final String DEFAULT_UPDATE_USER = "acc";

    public CalendarParseResult parse(Path filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        if (fileBytes.length < HEADER_LENGTH + MD5_LENGTH) {
            throw new IllegalArgumentException("参数文件长度不足: " + fileBytes.length);
        }

        Cursor cursor = new Cursor(fileBytes);
        CalendarParseResult result = new CalendarParseResult();
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

    private void readBody(Cursor cursor, long paraVerNo, CalendarParseResult result) {
        LocalDateTime now = LocalDateTime.now();
        readSpecialDates(cursor, paraVerNo, now, result);
        readTimeIntervals(cursor, paraVerNo, now, result);
        readFareTimes(cursor, paraVerNo, now, result);
    }

    private void readSpecialDates(Cursor cursor, long paraVerNo, LocalDateTime now, CalendarParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            SpecialDate row = new SpecialDate();
            row.setParaVerNo(paraVerNo);
            row.setSeqNo(i);
            row.setDateType(ParaFileReadUtils.leftPad(String.valueOf(ParaFileReadUtils.byteToInt(cursor.readOne())), 2, '0'));
            row.setSpecialDate(ParaFileReadUtils.leftPad(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(4)), 8, '0'));
            row.setLastUpdUser(DEFAULT_UPDATE_USER);
            row.setLastUpdTms(now);
            result.getSpecialDates().add(row);
        }
    }

    private void readTimeIntervals(Cursor cursor, long paraVerNo, LocalDateTime now, CalendarParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            TimeInterval row = new TimeInterval();
            row.setParaVerNo(paraVerNo);
            row.setIntervalNo((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
            row.setBeginTime(ParaFileReadUtils.leftPad(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(3)), 6, '0'));
            row.setEndTime(ParaFileReadUtils.leftPad(ParaFileReadUtils.bcdToStringKeepLeadingZero(cursor.read(3)), 6, '0'));
            row.setLastUpdUser(DEFAULT_UPDATE_USER);
            row.setLastUpdTms(now);
            result.getTimeIntervals().add(row);
        }
    }

    private void readFareTimes(Cursor cursor, long paraVerNo, LocalDateTime now, CalendarParseResult result) {
        long size = readCount(cursor);
        for (int i = 0; i < size; i++) {
            FareTime row = new FareTime();
            row.setParaVerNo(paraVerNo);
            row.setFareTier((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
            row.setAllowedTime((int) ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4)));
            row.setLastUpdUser(DEFAULT_UPDATE_USER);
            row.setLastUpdTms(now);
            result.getFareTimes().add(row);
        }
    }

    private long readCount(Cursor cursor) {
        return ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4));
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
