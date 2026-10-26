package com.chinasofti.huateng.para.model.device;

import java.util.List;

/**
 * ACC 设备配置接口分页响应（{@code GET /api/v1/Config/device}）。
 * <p>结构：{@code {"body":{"totalCount":..,"currentPage":..,"pageSize":..,"data":[..]},
 * "success":true,"error":null}}，用 Fastjson2 反序列化。</p>
 */
public class AccDevicePageResult {
    private Body body;
    private Boolean success;
    private String error;

    public Body getBody() { return body; }
    public void setBody(Body body) { this.body = body; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    /** ACC 分页 body 段。 */
    public static class Body {
        private Integer totalCount;
        private Integer currentPage;
        private Integer pageSize;
        private List<AccDeviceDTO> data;

        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getCurrentPage() { return currentPage; }
        public void setCurrentPage(Integer currentPage) { this.currentPage = currentPage; }
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
        public List<AccDeviceDTO> getData() { return data; }
        public void setData(List<AccDeviceDTO> data) { this.data = data; }
    }
}
