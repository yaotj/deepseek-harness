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

/**
 * 对账文件上传管理
 */
@RestController
@RequestMapping("/recon/upload-file")
public class ReconUploadFileController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(ReconUploadFileController.class);

    /** 对账文件落盘目录，复用 web-admin.yaml 中 app-logs-v2 PVC 挂载点 /home/javaapp/app/export */
    public static final String UPLOAD_DIR = "/home/javaapp/app/export/recon-upload/";

    /**
     * ITP 侧五类对账文件名，与 recon-server 的 ReconFtpService 投递校验同一口径
     * （那边是 {@code ITP\.(EXP|PAY|BUS|DETAIL|TXNDIFF)\.\d{8}}、无扩展名）；
     * 本处按业主裁决额外允许可选后缀，但基名与账期段的判定完全一致。
     */
    private static final Pattern ITP_NAME =
            Pattern.compile("^(ITP\\.(EXP|PAY|BUS|DETAIL|TXNDIFF)\\.(\\d{8}))(?:\\.([A-Za-z0-9]{1,10}))?$");

    /** 渠道日账单文件名，对应 recon.thirdparty.bill-file-pattern 默认 glob {@code reconciliation-bill-*.csv}。 */
    private static final Pattern BILL_NAME =
            Pattern.compile("^(reconciliation-bill-(\\d{8}))(?:\\.([A-Za-z0-9]{1,10}))?$");

    /** 渠道账单的类别码（ITP 五类的类别码直接取文件名里的类型段）。 */
    private static final String CATEGORY_BILL = "BILL";

    @Autowired
    private IReconUploadFileService reconUploadFileService;

    /**
     * 查询对账文件上传列表（按创建时间倒序）
     */
    @PreAuthorize("@ss.hasPermi('recon:upload-file:query')")
    @GetMapping("/list")
    public TableDataInfo list(ReconUploadFile file)
    {
        startPage();
        List<ReconUploadFile> list = reconUploadFileService.selectReconUploadFileList(file);
        return getDataTable(list);
    }

    /**
     * 上传对账文件
     */
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

        // 落盘名只由白名单正则的捕获组重新拼出，原始文件名一个字都不进路径 ⇒ 不存在 ../ 穿越面。
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
                    // 旧文件没能留存就中止：覆盖不可逆，宁可让运营重试，也不能悄悄丢掉上一版账单。
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
            // 并发下两人都通过了 select-then-write：唯一索引 UK_RUF_CATEGORY_DATE 才是最终裁判。
            // 落盘文件保留（写的就是规范名，后到者覆盖），只拒绝这条记录。
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

    /**
     * 下载对账文件
     */
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

    /**
     * 删除对账文件记录及磁盘文件
     */
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

    /**
     * 按两套既有约定解析文件名，得到「类别 + 账期 + 落盘规范名 + 后缀」。
     *
     * @return 不匹配任何一套时返回 {@code null}（调用方据此拒绝上传、不落盘、不写库）
     */
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
            // 「八位数字但不是合法日期」（如 20261399）必须拒掉，
            // 否则会生成一条按账期永远查不到、也无法与 FTP 投递对齐的记录。
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

    /** 文件名解析结果；四个字段全部来自白名单正则的捕获组，可安全用于拼路径。 */
    private record ReconFileName(String category, String reconDate, String canonicalName, String extension) { }
}
