package com.chinasofti.huateng.para.model;

import com.chinasofti.huateng.para.entity.calendar.FareTime;
import com.chinasofti.huateng.para.entity.calendar.SpecialDate;
import com.chinasofti.huateng.para.entity.calendar.TimeInterval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日历参数文件解析结果。
 */
public class CalendarParseResult {
    private String filePath;
    private String fileName;
    private Integer fileLength;
    private Map<String, Object> header;
    private Integer bodyReadBytes;
    private Integer bodyTotalBytes;
    private String md5InFile;
    private String md5Calculated;
    private Boolean md5Valid;
    private List<SpecialDate> specialDates = new ArrayList<>();
    private List<TimeInterval> timeIntervals = new ArrayList<>();
    private List<FareTime> fareTimes = new ArrayList<>();

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getFileLength() { return fileLength; }
    public void setFileLength(Integer fileLength) { this.fileLength = fileLength; }
    public Map<String, Object> getHeader() { return header; }
    public void setHeader(Map<String, Object> header) { this.header = header; }
    public Integer getBodyReadBytes() { return bodyReadBytes; }
    public void setBodyReadBytes(Integer bodyReadBytes) { this.bodyReadBytes = bodyReadBytes; }
    public Integer getBodyTotalBytes() { return bodyTotalBytes; }
    public void setBodyTotalBytes(Integer bodyTotalBytes) { this.bodyTotalBytes = bodyTotalBytes; }
    public String getMd5InFile() { return md5InFile; }
    public void setMd5InFile(String md5InFile) { this.md5InFile = md5InFile; }
    public String getMd5Calculated() { return md5Calculated; }
    public void setMd5Calculated(String md5Calculated) { this.md5Calculated = md5Calculated; }
    public Boolean getMd5Valid() { return md5Valid; }
    public void setMd5Valid(Boolean md5Valid) { this.md5Valid = md5Valid; }
    public List<SpecialDate> getSpecialDates() { return specialDates; }
    public void setSpecialDates(List<SpecialDate> specialDates) { this.specialDates = specialDates; }
    public List<TimeInterval> getTimeIntervals() { return timeIntervals; }
    public void setTimeIntervals(List<TimeInterval> timeIntervals) { this.timeIntervals = timeIntervals; }
    public List<FareTime> getFareTimes() { return fareTimes; }
    public void setFareTimes(List<FareTime> fareTimes) { this.fareTimes = fareTimes; }
}
