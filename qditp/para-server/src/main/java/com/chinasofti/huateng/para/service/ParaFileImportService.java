package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.para.entity.ParaVersion;
import com.chinasofti.huateng.para.mapper.ParaVersionMapper;
import com.chinasofti.huateng.para.model.CalendarParseResult;
import com.chinasofti.huateng.para.model.ParaImportResult;
import com.chinasofti.huateng.para.model.RateParseResult;
import com.chinasofti.huateng.para.model.RowNetworkParseResult;
import com.chinasofti.huateng.para.model.TicketParseResult;
import com.chinasofti.huateng.para.util.ParaFileReadUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ParaFileImportService {

    private static final int HEADER_LENGTH = 22;
    private static final int MD5_LENGTH = 16;
    private static final String DEFAULT_UPDATE_USER = "itp";

    private final RowNetworkImportService rowNetworkImportService;
    private final CalendarImportService calendarImportService;
    private final TicketImportService ticketImportService;
    private final RateImportService rateImportService;
    private final ParaVersionMapper paraVersionMapper;

    public ParaFileImportService(RowNetworkImportService rowNetworkImportService,
                                 CalendarImportService calendarImportService,
                                 TicketImportService ticketImportService,
                                 RateImportService rateImportService,
                                 ParaVersionMapper paraVersionMapper) {
        this.rowNetworkImportService = rowNetworkImportService;
        this.calendarImportService = calendarImportService;
        this.ticketImportService = ticketImportService;
        this.rateImportService = rateImportService;
        this.paraVersionMapper = paraVersionMapper;
    }

    /**
     * 解析并导入本地参数文件。
     *
     * <p>入库判据是「版本号 + MD5」（2026-09-08 由「仅版本号」改成本形态）：</p>
     * <ul>
     *   <li>库中无该 paraType，或文件版本号更高 → 导入</li>
     *   <li>文件版本号更低 → 跳过（版本回退不处理）</li>
     *   <li>版本号相同：MD5 不同 → 导入；MD5 相同 → 跳过；库中 MD5 为空 → 导入（补齐 MD5）</li>
     * </ul>
     *
     * <p>改成带 MD5 的原因：ACC 实际出现过同一版本号两份不同内容的文件（0001 版本 41 有 4 段命名与
     * 5 段命名两份，MD5 不同）。只比版本号时后到的那份永远进不来，两边数据无法收敛。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ParaImportResult importLocalFile(String filePath) {
        Path path = Path.of(filePath);
        byte[] fileBytes = readAllBytes(path);
        Map<String, Object> header = readHeader(fileBytes, path);
        String paraType = header.get("paraType").toString();
        Long fileVerNo = Long.parseLong(header.get("paraVerNo").toString());
        String fileMd5 = ParaFileReadUtils.md5Hex(fileBytes, 0, fileBytes.length - MD5_LENGTH);

        ParaVersion current = paraVersionMapper.selectByParaType(paraType);
        if (current != null && current.getCurrentVerNo() != null) {
            long currentVerNo = current.getCurrentVerNo();
            if (fileVerNo < currentVerNo) {
                return ParaImportResult.skipped(header, currentVerNo);
            }
            if (fileVerNo == currentVerNo
                    && current.getMd5Value() != null
                    && !current.getMd5Value().isBlank()
                    && current.getMd5Value().equalsIgnoreCase(fileMd5)) {
                return ParaImportResult.skippedSameContent(header, currentVerNo, fileMd5);
            }
        }

        Object parseResult;
        switch (paraType) {
            case "0001":
                parseResult = rowNetworkImportService.importLocalFile(filePath);
                break;
            case "0002":
                parseResult = calendarImportService.importLocalFile(filePath);
                break;
            case "0003":
                parseResult = ticketImportService.importLocalFile(filePath);
                break;
            case "0004":
                parseResult = rateImportService.importLocalFile(filePath);
                break;
            default:
                throw new IllegalArgumentException("暂不支持的参数文件类型: " + paraType);
        }
        upsertVersion(path, header, parseResult);
        return ParaImportResult.imported(header, parseResult);
    }

    private byte[] readAllBytes(Path filePath) {
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("参数文件读取失败: " + filePath, e);
        }
    }

    private Map<String, Object> readHeader(byte[] fileBytes, Path filePath) {
        if (fileBytes.length < HEADER_LENGTH + MD5_LENGTH) {
            throw new IllegalArgumentException("参数文件长度不足（头 22 字节 + 尾 16 字节 MD5）: " + filePath);
        }

        Cursor cursor = new Cursor(fileBytes);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("fileType", Integer.toHexString(ParaFileReadUtils.byteToInt(cursor.readOne())));
        header.put("fileCreateDateTime", ParaFileReadUtils.bcdToString(cursor.read(7)));
        header.put("fileVerNo", Integer.toHexString(ParaFileReadUtils.byteToInt(cursor.readOne())));
        header.put("paraType", ParaFileReadUtils.leftPad(Integer.toHexString(ParaFileReadUtils.twoBytesToIntLittle(cursor.read(2))), 4, '0'));
        header.put("paraVerNo", String.valueOf(ParaFileReadUtils.fourBytesToIntLittle(cursor.read(4))));
        header.put("validDateTime", ParaFileReadUtils.bcdToString(cursor.read(7)));
        return header;
    }

    private void upsertVersion(Path path, Map<String, Object> header, Object parseResult) {
        ParaVersion paraVersion = new ParaVersion();
        paraVersion.setParaType(header.get("paraType").toString());
        paraVersion.setCurrentVerNo(Long.parseLong(header.get("paraVerNo").toString()));
        paraVersion.setCurrentFileName(path.getFileName().toString());
        paraVersion.setValidDateTime(header.get("validDateTime").toString());
        paraVersion.setMd5Value(readMd5(parseResult));
        paraVersion.setLastUpdUser(DEFAULT_UPDATE_USER);
        paraVersion.setLastUpdTms(LocalDateTime.now());
        paraVersionMapper.upsert(paraVersion);
    }

    private String readMd5(Object parseResult) {
        if (parseResult instanceof RowNetworkParseResult result) {
            return result.getMd5Calculated();
        }
        if (parseResult instanceof CalendarParseResult result) {
            return result.getMd5Calculated();
        }
        if (parseResult instanceof TicketParseResult result) {
            return result.getMd5Calculated();
        }
        if (parseResult instanceof RateParseResult result) {
            return result.getMd5Calculated();
        }
        return null;
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
    }
}
