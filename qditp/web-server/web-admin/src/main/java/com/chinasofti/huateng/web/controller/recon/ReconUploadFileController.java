package com.chinasofti.huateng.web.controller.recon;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chinasofti.huateng.common.annotation.Log;
import com.chinasofti.huateng.common.core.controller.BaseController;
import com.chinasofti.huateng.common.core.domain.AjaxResult;
import com.chinasofti.huateng.common.core.page.TableDataInfo;
import com.chinasofti.huateng.common.enums.BusinessType;
import com.chinasofti.huateng.common.utils.SecurityUtils;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.file.FileUtils;
import com.chinasofti.huateng.web.domain.recon.ReconUploadFile;
import com.chinasofti.huateng.web.service.recon.IReconUploadFileService;


@RestController
@RequestMapping("/recon/upload-file")
public class ReconUploadFileController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(ReconUploadFileController.class);

    public static final String UPLOAD_DIR = "/home/javaapp/app/export/recon-upload/";


    private static final Pattern ITP_NAME =
            Pattern.compile("^(ITP\\.(EXP|PAY|BUS|DETAIL|TXNDIFF)\\.(\\d{8}))(?:\\.([A-Za-z0-9]{1,10}))?$");

    private static final Pattern BILL_NAME =
            Pattern.compile("^(reconciliation-bill-(\\d{8}))(?:\\.([A-Za-z0-9]{1,10}))?$");

    private static final String CATEGORY_BILL = "BILL";

    @Autowired
    private IReconUploadFileService reconUploadFileService;


    @PreAuthorize("@ss.hasPermi('recon:upload-file:query')")
    @GetMapping("/list")
    public TableDataInfo list(ReconUploadFile file)
    {
        startPage();
        List<ReconUploadFile> list = reconUploadFileService.selectReconUploadFileList(file);
        return getDataTable(list);
    }


    @PreAuthorize("@ss.hasPermi('recon:upload-file:upload')")
    @Log(title = "对账文件上传", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "reconDate", required = false) String reconDate,
                             @RequestParam(value = "remark", required = false) String remark)
    {
        if (file == null || file.isEmpty())
        {
            return AjaxResult.error("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename))
        {
            return AjaxResult.error("原始文件名为空");
        }

        ReconFileName parsed = parseReconFileName(originalFilename);
        if (parsed == null)
        {
            return AjaxResult.error("文件名不符合对账命名约定：" + originalFilename
                    + "。应为 ITP.EXP|PAY|BUS|DETAIL|TXNDIFF.yyyyMMdd（如 ITP.PAY.20261105）"
                    + "或 reconciliation-bill-yyyyMMdd（如 reconciliation-bill-20261105.csv），后缀可选。");
        }
        if (StringUtils.isEmpty(reconDate))
        {
            return AjaxResult.error("对账日期不能为空，且必须与文件名里的账期一致");
        }
        if (!reconDate.trim().equals(parsed.reconDate()))
        {
            return AjaxResult.error("对账日期 " + reconDate.trim() + " 与文件名账期 " + parsed.reconDate() + " 不一致");
        }

        File destDir = new File(UPLOAD_DIR);
        if (!destDir.exists())
        {
            destDir.mkdirs();
        }

        File target = new File(UPLOAD_DIR + parsed.canonicalName());
        File staging = new File(UPLOAD_DIR + parsed.canonicalName() + ".uploading");
        try
        {
            file.transferTo(staging);
        }
        catch (Exception e)
        {
            log.error("对账文件暂存失败, fileName={}", originalFilename, e);
            deleteQuietly(staging);
            return AjaxResult.error("文件保存失败：" + e.getMessage());
        }

        ReconUploadFile existing = reconUploadFileService
                .selectByCategoryAndDate(parsed.category(), parsed.reconDate());
        if (existing != null && StringUtils.isNotEmpty(existing.getFilePath())
                && existing.getFilePath().startsWith(UPLOAD_DIR))
        {
            File old = new File(existing.getFilePath());
            if (old.exists())
            {
                File kept = new File(old.getAbsolutePath() + ".replaced-"
                        + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
                if (!old.renameTo(kept))
                {
                    deleteQuietly(staging);
                    log.error("对账旧文件留存改名失败, oldPath={}", old.getAbsolutePath());
                    return AjaxResult.error("旧对账文件留存失败，本次上传已中止，请重试");
                }
            }
        }
        if (!staging.renameTo(target))
        {
            deleteQuietly(staging);
            return AjaxResult.error("对账文件落盘失败，请重试");
        }

        ReconUploadFile record = new ReconUploadFile();
        Date now = new Date();
        record.setId(existing == null ? null : existing.getId());
        record.setFileName(originalFilename);
        record.setFilePath(target.getAbsolutePath());
        record.setFileSize(file.getSize());
        record.setFileType(parsed.extension());
        record.setFileCategory(parsed.category());
        record.setReconDate(parsed.reconDate());
        record.setStatus("UPLOADED");
        record.setRemark(remark);
        record.setUpdateBy(SecurityUtils.getUsername());
        record.setUpdateTime(now);

        try
        {
            if (existing == null)
            {
                record.setCreateBy(SecurityUtils.getUsername());
                record.setCreateTime(now);
                reconUploadFileService.insertReconUploadFile(record);
            }
            else
            {
                reconUploadFileService.updateReconUploadFile(record);
            }
        }
        catch (DuplicateKeyException e)
        {
            log.warn("对账文件并发替换被唯一索引拦下, category={}, reconDate={}", parsed.category(), parsed.reconDate());
            return AjaxResult.error("该账期的同类对账文件刚刚已被上传，本次记录未写入，请刷新列表后确认");
        }

        AjaxResult ajax = AjaxResult.success(existing == null ? "上传成功" : "已替换同账期同类对账文件");
        ajax.put("id", record.getId());
        ajax.put("fileName", record.getFileName());
        ajax.put("filePath", record.getFilePath());
        ajax.put("fileCategory", record.getFileCategory());
        return ajax;
    }


    @PreAuthorize("@ss.hasPermi('recon:upload-file:download')")
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response)
    {
        try
        {
            ReconUploadFile file = reconUploadFileService.selectReconUploadFileById(id);
            if (file == null)
            {
                throw new Exception("文件记录不存在");
            }
            String filePath = file.getFilePath();
            if (StringUtils.isEmpty(filePath))
            {
                throw new Exception("文件路径为空");
            }
            if (!filePath.startsWith(UPLOAD_DIR))
            {
                throw new Exception("非法文件路径");
            }
            File f = new File(filePath);
            if (!f.exists() || !f.isFile())
            {
                throw new Exception("文件不存在或已被清理");
            }

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, file.getFileName());
            FileUtils.writeBytes(filePath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载对账文件失败, id={}", id, e);
        }
    }


    @PreAuthorize("@ss.hasPermi('recon:upload-file:remove')")
    @Log(title = "对账文件上传", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        ReconUploadFile file = reconUploadFileService.selectReconUploadFileById(id);
        if (file == null)
        {
            return AjaxResult.error("文件记录不存在");
        }
        String filePath = file.getFilePath();
        if (StringUtils.isNotEmpty(filePath) && filePath.startsWith(UPLOAD_DIR))
        {
            FileUtils.deleteFile(filePath);
        }
        return toAjax(reconUploadFileService.deleteReconUploadFileById(id));
    }


    private ReconFileName parseReconFileName(String originalFilename)
    {
        Matcher itp = ITP_NAME.matcher(originalFilename);
        if (itp.matches())
        {
            return buildFileName(itp.group(1), itp.group(2), itp.group(3), itp.group(4));
        }
        Matcher bill = BILL_NAME.matcher(originalFilename);
        if (bill.matches())
        {
            return buildFileName(bill.group(1), CATEGORY_BILL, bill.group(2), bill.group(3));
        }
        return null;
    }

    private ReconFileName buildFileName(String canonicalBase, String category, String dateText, String extension)
    {
        try
        {
            LocalDate.parse(dateText, DateTimeFormatter.BASIC_ISO_DATE);
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
        String ext = StringUtils.lowerCase(StringUtils.defaultString(extension));
        String diskName = StringUtils.isEmpty(ext) ? canonicalBase : canonicalBase + "." + ext;
        return new ReconFileName(category, dateText, diskName, ext);
    }

    private void deleteQuietly(File file)
    {
        if (file != null && file.exists() && !file.delete())
        {
            log.warn("对账上传临时文件清理失败, path={}", file.getAbsolutePath());
        }
    }

    private record ReconFileName(String category, String reconDate, String canonicalName, String extension) { }
}
