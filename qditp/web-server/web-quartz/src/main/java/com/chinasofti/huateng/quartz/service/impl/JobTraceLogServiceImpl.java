package com.chinasofti.huateng.quartz.service.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.common.exception.ServiceException;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.quartz.domain.SysJobLog;
import com.chinasofti.huateng.quartz.service.IJobTraceLogService;
import com.chinasofti.huateng.quartz.service.ISysJobLogService;

/**
 * 按 traceId 从 VictoriaLogs 反查一次调度执行的全链路日志。
 *
 * <p>约束（改动前必读）：
 * <ul>
 *   <li>地址由 `vlogs.query-url` 注入（环境变量 `VLOGS_QUERY_URL`），**为空即功能禁用**并给出明确提示，
 *       NEVER 写死集群地址——测试与生产的 VictoriaLogs Service 名与端口不同。</li>
 *   <li>只做只读检索，超时按秒级封顶：全服务开了虚拟线程，请求线程上 NEVER 长时间阻塞。</li>
 *   <li>时间窗由 `sys_job_log.create_time` 与 `job_message` 里的耗时反推。该表**没有** start_time /
 *       stop_time 列（见 `SysJobLogMapper.xml` 的 resultMap），勿指望从实体上直接取。</li>
 * </ul>
 *
 * @author zmzhang
 */
@Service
public class JobTraceLogServiceImpl implements IJobTraceLogService
{
    private static final Logger log = LoggerFactory.getLogger(JobTraceLogServiceImpl.class);
    /** `AbstractQuartzJob.after()` 写进 job_message 的形态是 `，traceId=<32位hex>`。 */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("traceId=([0-9a-fA-F]{32})");

    /** 同一处写入的 `总共耗时：<n>毫秒`，用于把检索时间窗收窄到本次执行区间。 */
    private static final Pattern COST_PATTERN = Pattern.compile("耗时：(\\d+)毫秒");

    /** 只给出 scheme://host:port 时补全的查询路径。 */
    private static final String DEFAULT_QUERY_PATH = "/select/logsql/query";

    /**
     * 复用单例：HttpClient 每 new 一个都会带起 selector 线程，按请求新建会在管理后台被反复点击时堆积。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Autowired
    private ISysJobLogService jobLogService;

    @Value("${vlogs.query-url:}")
    private String queryUrl;

    @Value("${vlogs.query-timeout-millis:8000}")
    private long queryTimeoutMillis;

    /**
     * 执行开始点**之前**再往前取多久。开始点本身已由「耗时」反推出来，这里只是覆盖时钟偏差，
     * 所以不需要很大。
     */
    @Value("${vlogs.query-window-before-millis:300000}")
    private long windowBeforeMillis;

    /**
     * 执行结束点**之后**再往后取多久。批处理型任务常在自己返回后下游仍在跑（异步线程池、
     * `@Scheduled` 补偿、超时重试），这些日志的时间戳会晚于 `create_time`，
     * 尾部余量太小就查不到——因此默认值明显大于前置余量。
     */
    @Value("${vlogs.query-window-after-millis:1800000}")
    private long windowAfterMillis;

    /**
     * 单次返回上限。**NEVER 想当然认为截断后留下的是最早的 N 条**：VictoriaLogs 的 `limit`
     * 不保证顺序，排序是本端拿到结果后再做的，所以截断是随机丢弃。放宽时间窗时这个值要同步跟上。
     */
    @Value("${vlogs.query-limit:2000}")
    private int lineLimit;

    @Override
    public JSONObject selectTraceLogByJobLogId(Long jobLogId)
    {
        SysJobLog jobLog = jobLogService.selectJobLogById(jobLogId);
        if (jobLog == null)
        {
            throw new ServiceException("调度日志不存在：" + jobLogId);
        }
        String traceId = matchFirst(TRACE_ID_PATTERN, jobLog.getJobMessage());
        if (StringUtils.isEmpty(traceId))
        {
            throw new ServiceException("该条调度日志未记录 traceId，无法检索执行日志");
        }
        String endpoint = normalizeEndpoint(queryUrl);
        if (endpoint == null)
        {
            throw new ServiceException("未配置日志检索地址（vlogs.query-url / 环境变量 VLOGS_QUERY_URL）");
        }

        long createMillis = jobLog.getCreateTime() == null
                ? System.currentTimeMillis() : jobLog.getCreateTime().getTime();
        String cost = matchFirst(COST_PATTERN, jobLog.getJobMessage());
        long costMillis = StringUtils.isEmpty(cost) ? 0L : Long.parseLong(cost);
        long startMillis = createMillis - costMillis - windowBeforeMillis;
        long endMillis = createMillis + windowAfterMillis;

        List<JSONObject> lines = query(endpoint, traceId, startMillis, endMillis);
        JSONObject result = new JSONObject();
        result.put("traceId", traceId);
        result.put("startTime", Instant.ofEpochMilli(startMillis).toString());
        result.put("endTime", Instant.ofEpochMilli(endMillis).toString());
        result.put("truncated", lines.size() >= lineLimit);
        result.put("lines", lines);
        return result;
    }

    /**
     * 调 VictoriaLogs `/select/logsql/query`。响应是 JSON Lines，每行一条日志事件。
     *
     * <p>LogsQL 里 traceId 是普通字段过滤（`_stream_fields` 只有 app / host），选择性足够；
     * 仍带上 start / end 是为了避免全量扫描。</p>
     */
    private List<JSONObject> query(String endpoint, String traceId, long startMillis, long endMillis)
    {
        String body = "query=" + URLEncoder.encode("traceId:\"" + traceId + "\"", StandardCharsets.UTF_8)
                + "&start=" + URLEncoder.encode(toRfc3339(startMillis), StandardCharsets.UTF_8)
                + "&end=" + URLEncoder.encode(toRfc3339(endMillis), StandardCharsets.UTF_8)
                + "&limit=" + lineLimit;
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(queryTimeoutMillis))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try
        {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("日志检索被中断");
        }
        catch (Exception e)
        {
            log.error("检索 VictoriaLogs 失败, traceId={}", traceId, e);
            throw new ServiceException("日志系统不可达，请检查 vlogs.query-url 与网络连通性");
        }
        if (response.statusCode() / 100 != 2)
        {
            throw new ServiceException("日志系统返回异常状态：" + response.statusCode());
        }
        return parseLines(response.body());
    }

    /** 按 `_time` 升序排列：VictoriaLogs 不保证返回顺序，前台要按时间读。 */
    private List<JSONObject> parseLines(String responseBody)
    {
        List<JSONObject> lines = new ArrayList<>();
        if (StringUtils.isEmpty(responseBody))
        {
            return lines;
        }
        for (String line : responseBody.split("\n"))
        {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            try
            {
                lines.add(JSON.parseObject(trimmed));
            }
            catch (Exception e)
            {
                log.warn("跳过无法解析的日志行：{}", StringUtils.substring(trimmed, 0, 200));
            }
        }
        lines.sort(Comparator.comparing((JSONObject item) -> StringUtils.nvl(item.getString("_time"), "")));
        return lines;
    }

    /** 只给出 scheme://host:port 时补全查询路径，已带 `/select/` 的原样使用。 */
    private String normalizeEndpoint(String url)
    {
        if (StringUtils.isEmpty(url))
        {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.contains("/select/"))
        {
            return trimmed;
        }
        return trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1) + DEFAULT_QUERY_PATH
                : trimmed + DEFAULT_QUERY_PATH;
    }

    private String matchFirst(Pattern pattern, String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String toRfc3339(long epochMillis)
    {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis));
    }
}
