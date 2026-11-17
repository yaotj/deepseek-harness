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


@RestController
@RequestMapping("/common/importer")
public class CommonImporterController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(CommonImporterController.class);

    private static final String IMPORT_BASE_DIR = "import";

    @Autowired
    private BizRouter bizRouter;

    @Autowired
    private IBizImportRecordService importRecordService;


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


    @GetMapping("/importTemplate")
    public void importTemplate(@RequestParam("biz") String biz,
                               HttpServletResponse response)
    {
        @SuppressWarnings("unchecked")
        IImportExportHandler<Object> handler = bizRouter.getHandler(biz);
        ExcelUtil<Object> util = new ExcelUtil<>(handler.getEntityClass());
        util.importTemplateExcel(response, handler.getEntityLabel(biz));
    }


    @GetMapping("/download")
    public void downloadFile(@RequestParam("fileUrl") String fileUrl,
                             HttpServletResponse response)
    {
        try
        {
            String localPath = PointsConfig.getProfile() + fileUrl.substring("/profile".length());
            com.chinasofti.huateng.common.utils.file.FileUtils.writeBytes(localPath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载导入文件失败", e);
        }
    }


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

        BizImportRecord record = new BizImportRecord();
        record.setBizCode(biz);
        record.setFileName(fileName);
        record.setFileSize(file.getSize());
        record.setFileUrl(storedPath);
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

            String result = handler.handle(biz, list, updateSupport, operator);

            int success = 0, fail = 0;
            List<String> errorMessages = new ArrayList<>();
            if (result != null)
            {
                String[] parts = result.split("，");
                for (String part : parts)
                {
                    if (part.contains("成功"))
                    {
                        try { success = Integer.parseInt(part.replaceAll("\\D+", "")); } catch (NumberFormatException e) {  }
                    }
                    else if (part.contains("失败"))
                    {
                        try { fail = Integer.parseInt(part.replaceAll("\\D+", "")); } catch (NumberFormatException e) {  }
                    }
                    else if (part.contains("第"))
                    {
                        errorMessages.add(part.trim());
                    }
                }
            }

            record.setSuccessRows(success);
            record.setFailRows(fail);
            record.setStatus("1");
            record.setFinishTime(new Date());
            if (fail > 0 && errorMessages.isEmpty())
            {
                record.setErrorMsg("导入完成，失败 " + fail + " 条");
            }
            importRecordService.updateBizImportRecord(record);

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
