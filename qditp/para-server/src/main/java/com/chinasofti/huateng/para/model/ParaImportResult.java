package com.chinasofti.huateng.para.model;

import java.util.Map;

public class ParaImportResult {
    private Boolean imported;
    private String message;
    private Map<String, Object> header;
    private Object parseResult;

    public static ParaImportResult imported(Map<String, Object> header, Object parseResult) {
        ParaImportResult result = new ParaImportResult();
        result.setImported(true);
        result.setMessage("导入成功");
        result.setHeader(header);
        result.setParseResult(parseResult);
        return result;
    }

    public static ParaImportResult skipped(Map<String, Object> header, Long currentVerNo) {
        ParaImportResult result = new ParaImportResult();
        result.setImported(false);
        result.setMessage("版本回退，跳过解析入库，当前版本=" + currentVerNo);
        result.setHeader(header);
        return result;
    }

    /**
     * 版本号与库中一致、且文件内容 MD5 也一致 —— 真正的「无变更」，跳过。
     */
    public static ParaImportResult skippedSameContent(Map<String, Object> header, Long currentVerNo, String md5) {
        ParaImportResult result = new ParaImportResult();
        result.setImported(false);
        result.setMessage("版本与内容均未变更，跳过解析入库，当前版本=" + currentVerNo + ", md5=" + md5);
        result.setHeader(header);
        return result;
    }

    public Boolean getImported() { return imported; }
    public void setImported(Boolean imported) { this.imported = imported; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getHeader() { return header; }
    public void setHeader(Map<String, Object> header) { this.header = header; }
    public Object getParseResult() { return parseResult; }
    public void setParseResult(Object parseResult) { this.parseResult = parseResult; }
}
