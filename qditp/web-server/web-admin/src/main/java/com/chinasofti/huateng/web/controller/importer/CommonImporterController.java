package com.chinasofti.huateng.web.controller.importer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chinasofti.huateng.common.annotation.Log;
import com.chinasofti.huateng.common.config.PointsConfig;
import com.chinasofti.huateng.common.core.controller.BaseController;
import com.chinasofti.huateng.common.core.domain.AjaxResult;
import com.chinasofti.huateng.common.enums.BusinessType;
import com.chinasofti.huateng.common.utils.ServletUtils;
import com.chinasofti.huateng.common.utils.file.FileUploadUtils;
import com.chinasofti.huateng.common.utils.poi.ExcelUtil;
import com.chinasofti.huateng.web.domain.importer.BizImportRecord;
import com.chinasofti.huateng.web.service.importer.IBizImportRecordService;

/**
 * 通用 Excel 导入/导出/模板入口。
 *
 * <h3>使用方式</h3>
 * 业务模块只需实现 {@link IImportExportHandler} 并注册到 {@link BizRouter}，
 * 然后通过 {@code /common/importer/*} 接口即可使用。
 *
 * <h3>Web 端调用约定</h3>
 * <ul>
 *   <li>导出：POST {@code /common/importer/export?biz=<bizCode>&queryJson=...}</li>
 *   <li>导入：POST {@code /common/importer/importData?biz=<bizCode>&updateSupport=false}，
 *       multipart/form-data，字段 {@code file}</li>
 *   <li>模板：GET {@code /common/importer/importTemplate?biz=<bizCode>}</li>
 * </ul>
 *
 * <h3>文件存储</h3>
 * 上传的 Excel 文件保存在 {@code points.profile}/import/{bizCode}/ 目录下，
 * 路径格式：{@code /home/import/{bizCode}/{batchNo}_{timestamp}_{originalName}.xlsx}
 * 数据库记录 {@code file_url} 字段存储相对路径（如 {@code /profile/import/xxx/...}），
 * 可通过 {@code /common/importer/download?fileUrl=...} 下载原文件。
 *
 * <h3>注册示例</h3>
 * <pre>{@code
 * @Component
 * public class SomeBizHandler implements IImportExportHandler<SomeBizEntity> {
 *     public Class<SomeBizEntity> getEntityClass() { return SomeBizEntity.class; }
 *     public String getEntityLabel(String biz) { return "某业务"; }
 *     public List<SomeBizEntity> getExportList(String biz, String queryJson) throws Exception { ... }
 *     public String handle(String biz, List<SomeBizEntity> list, boolean updateSupport, String operator) { ... }
 * }
 *
 * @Component
 * public class SomeBizInitializer {
 *     @Autowired private BizRouter router;
 *     @Autowired private SomeBizHandler handler;
 *     @PostConstruct public void init() { router.register("someBiz", handler); }
 * }
 * }</pre>
 *
 * <p>权限控制由业务 handler 内部或外部菜单 SQL 维护，本基类不加 {@code @PreAuthorize}，
 * 避免跨业务权限策略耦合。
 *
 * <p><b>安全红线（AGENTS.md §4.2）：</b>涉及支付/订单/扣款/签约/开户/卡池/对账链路的 biz，
 * handler 内部 MUST 做幂等兜底（DB 唯一索引 + DuplicateKeyException 处理）。
 * 网络层重试已由 ProxyWebClient.isNetworkRetryEnabled()=false 关闭。
 *
 * @author tuanjie
 */
@RestController
@RequestMapping("/common/importer")
public class CommonImporterController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(CommonImporterController.class);

    /** 导入文件存储子目录 */
    private static final String IMPORT_BASE_DIR = "import";

    @Autowired
    private BizRouter bizRouter;

    @Autowired
    private IBizImportRecordService importRecordService;

    /**
     * 导出 Excel。
     *
     * @param biz       业务标识，须已在 {@link BizRouter} 注册
     * @param response  HTTP 响应
     * @param queryJson 前端传入的查询条件（可选，JSON 字符串），由 handler 解析
     * @throws IllegalArgumentException 若 biz 未注册
     */
    @Log(title = "导入导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@RequestParam("biz") String biz,
                       HttpServletResponse response,
                       @RequestParam(required = false) String queryJson) throws Exception
    {
        @SuppressWarnings("unchecked")
        IImportExportHandler<Object> handler = bizRouter.getHandler(biz);
        List<Object> list = handler.getExportList(biz, queryJson);
        ExcelUtil<Object> util = new ExcelUtil<>(handler.getEntityClass());
        util.exportExcel(response, list, handler.getEntityLabel(biz));
    }

    /**
     * 下载导入模板（仅含列头 + 1 行空示例，不含业务数据）。
     *
     * @param biz      业务标识，须已在 {@link BizRouter} 注册
     * @param response HTTP 响应
     * @throws IllegalArgumentException 若 biz 未注册
     */
    @GetMapping("/importTemplate")
    public void importTemplate(@RequestParam("biz") String biz,
                               HttpServletResponse response)
    {
        @SuppressWarnings("unchecked")
        IImportExportHandler<Object> handler = bizRouter.getHandler(biz);
        ExcelUtil<Object> util = new ExcelUtil<>(handler.getEntityClass());
        util.importTemplateExcel(response, handler.getEntityLabel(biz));
    }

    /**
     * 下载已上传的导入原文件（供复查）。
     *
     * @param fileUrl  相对路径，格式如 {@code /profile/import/someBiz/xxx.xlsx}
     * @param response HTTP 响应
     */
    @GetMapping("/download")
    public void downloadFile(@RequestParam("fileUrl") String fileUrl,
                             HttpServletResponse response)
    {
        try
        {
            // fileUrl 格式: /profile/import/bizCode/filename
            // 转换为本地路径: points.profile/import/bizCode/filename
            String localPath = PointsConfig.getProfile() + fileUrl.substring("/profile".length());
            com.chinasofti.huateng.common.utils.file.FileUtils.writeBytes(localPath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载导入文件失败", e);
        }
    }

    /**
     * 上传 Excel 并落库。
     *
     * <p>流程：
     * <ol>
     *   <li>生成批次号 batchNo（UUID）</li>
     *   <li>上传文件到 {@code points.profile/import/{bizCode}/} 目录</li>
     *   <li>创建导入记录（status=0 处理中）</li>
     *   <li>解析 Excel 并调用 handler.handle() 落库</li>
     *   <li>更新导入记录（统计成功/失败行数）</li>
     *   <li>返回结果</li>
     * </ol>
     *
     * <p><b>安全红线：</b>涉及支付/订单/扣款的 biz，handler 内部须做幂等校验。
     *
     * @param biz             业务标识，须已在 {@link BizRouter} 注册
     * @param file            上传的 Excel 文件（.xlsx / .xls）
     * @param updateSupport   已存在时是否更新（由 handler 决定是否生效）
     * @throws IllegalArgumentException 若 biz 未注册或文件为空
     */
    @Log(title = "导入", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(@RequestParam("biz") String biz,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false, defaultValue = "false") boolean updateSupport)
            throws IOException
    {
        if (file.isEmpty())
        {
            return error("上传文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.toLowerCase().endsWith(".xlsx")
                && !fileName.toLowerCase().endsWith(".xls")))
        {
            return error("仅支持 .xlsx / .xls 文件");
        }

        String operator = getUsername();
        String clientIp = ServletUtils.getRequest().getRemoteAddr();
        String batchNo = UUID.randomUUID().toString().replace("-", "");

        // 上传文件到本地目录
        String uploadDir = PointsConfig.getImportPath() + "/" + biz;
        String storedPath;
        try
        {
            storedPath = FileUploadUtils.upload(uploadDir, file);
        }
        catch (Exception e)
        {
            log.error("文件上传失败，biz={}", biz, e);
            return error("文件上传失败：" + e.getMessage());
        }

        // 创建导入记录（状态：处理中）
        BizImportRecord record = new BizImportRecord();
        record.setBizCode(biz);
        record.setFileName(fileName);
        record.setFileSize(file.getSize());
        record.setFileUrl(storedPath);  // 相对路径，如 /profile/import/someBiz/2026-10-27/xxx.xlsx
        record.setImportBy(operator);
        record.setClientIp(clientIp);
        record.setBatchNo(batchNo);
        record.setUpdateSupport(updateSupport ? "1" : "0");
        record.setStatus("0");
        record.setCreateTime(new Date());
        record.setCreateBy(operator);
        importRecordService.insertBizImportRecord(record);

        try
        {
            @SuppressWarnings("unchecked")
            IImportExportHandler<Object> handler = bizRouter.getHandler(biz);
            ExcelUtil<Object> util = new ExcelUtil<>(handler.getEntityClass());
            List<Object> list = util.importExcel(file.getInputStream());

            if (list == null || list.isEmpty())
            {
                record.setStatus("2");
                record.setErrorMsg("文件中无有效数据行");
                record.setFinishTime(new Date());
                importRecordService.updateBizImportRecord(record);
                return error("文件中无有效数据行");
            }

            record.setTotalRows(list.size());

            // 调用业务 handler 处理
            String result = handler.handle(biz, list, updateSupport, operator);

            // 解析统计结果（格式："成功 N 条，失败 M 条"）
            int success = 0, fail = 0;
            List<String> errorMessages = new ArrayList<>();
            if (result != null)
            {
                String[] parts = result.split("，");
                for (String part : parts)
                {
                    if (part.contains("成功"))
                    {
                        try { success = Integer.parseInt(part.replaceAll("\\D+", "")); } catch (NumberFormatException e) { /* ignore */ }
                    }
                    else if (part.contains("失败"))
                    {
                        try { fail = Integer.parseInt(part.replaceAll("\\D+", "")); } catch (NumberFormatException e) { /* ignore */ }
                    }
                    else if (part.contains("第"))
                    {
                        errorMessages.add(part.trim());
                    }
                }
            }

            record.setSuccessRows(success);
            record.setFailRows(fail);
            record.setStatus("1"); // 无论成败都标记为已完成
            record.setFinishTime(new Date());
            if (fail > 0 && errorMessages.isEmpty())
            {
                record.setErrorMsg("导入完成，失败 " + fail + " 条");
            }
            importRecordService.updateBizImportRecord(record);

            // 返回结果
            AjaxResult ajax = success(result);
            ajax.put("batchNo", batchNo);
            ajax.put("fileUrl", storedPath);
            if (fail > 0)
            {
                ajax.put("failRows", fail);
                ajax.put("errorMessages", errorMessages.isEmpty()
                        ? List.of("请检查导入数据格式")
                        : errorMessages.stream().limit(10).collect(Collectors.toList()));
            }
            return ajax;
        }
        catch (Exception e)
        {
            log.error("Excel 导入失败，biz={}", biz, e);
            record.setStatus("2");
            record.setErrorMsg("导入失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            record.setFinishTime(new Date());
            importRecordService.updateBizImportRecord(record);
            return error("导入失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }
}
