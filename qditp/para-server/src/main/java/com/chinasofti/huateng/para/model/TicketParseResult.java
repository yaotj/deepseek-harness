package com.chinasofti.huateng.para.model;

import com.chinasofti.huateng.para.entity.ticket.ChipType;
import com.chinasofti.huateng.para.entity.ticket.TicketType;
import com.chinasofti.huateng.para.entity.ticket.TotalSalePart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TicketParseResult {
    private String filePath;
    private String fileName;
    private Integer fileLength;
    private Map<String, Object> header;
    private Integer bodyReadBytes;
    private Integer bodyTotalBytes;
    private String md5InFile;
    private String md5Calculated;
    private Boolean md5Valid;
    private Integer singleTicketKeyVersion;
    private Integer totalSalePartCount;
    private List<ChipType> chipTypes = new ArrayList<>();
    private List<TicketType> ticketTypes = new ArrayList<>();
    private List<TotalSalePart> totalSaleParts = new ArrayList<>();

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
    public Integer getSingleTicketKeyVersion() { return singleTicketKeyVersion; }
    public void setSingleTicketKeyVersion(Integer singleTicketKeyVersion) { this.singleTicketKeyVersion = singleTicketKeyVersion; }
    public Integer getTotalSalePartCount() { return totalSalePartCount; }
    public void setTotalSalePartCount(Integer totalSalePartCount) { this.totalSalePartCount = totalSalePartCount; }
    public List<ChipType> getChipTypes() { return chipTypes; }
    public void setChipTypes(List<ChipType> chipTypes) { this.chipTypes = chipTypes; }
    public List<TicketType> getTicketTypes() { return ticketTypes; }
    public void setTicketTypes(List<TicketType> ticketTypes) { this.ticketTypes = ticketTypes; }
    public List<TotalSalePart> getTotalSaleParts() { return totalSaleParts; }
    public void setTotalSaleParts(List<TotalSalePart> totalSaleParts) { this.totalSaleParts = totalSaleParts; }
}
