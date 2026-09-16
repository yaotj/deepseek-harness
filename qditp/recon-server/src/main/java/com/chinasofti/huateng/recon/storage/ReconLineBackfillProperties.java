package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * PAY 汇总文件「线路段」补齐的配置。
 *
 * <p>2026-09-16 起，线路段由 recon-server 在聚合完成、写文件之前统一补齐，四个源服务不再各自
 * {@code LEFT JOIN STATION_INFO}（背景与判据见 {@code ReconStationMapper} 的类注释）。</p>
 *
 * <p><b>为什么下标是配置项而不是常量</b>：{@code ReconFileGenerationService} 的类注释写着
 * 「NEVER 在本类里硬编码下标」—— 段数一律取自 {@code ReconFileTypeEnum}。线路段与车站段的位置
 * 是甲方 21 段行格式里的语义位置，{@code ReconFileTypeEnum} 只给「键有几段、度量有几段」、
 * 给不出「第几段是线路」，所以它必须来自本类；写成配置而不是常量，是为了
 * <b>出问题时能一键关闭（{@code enabled=false}）退回到「原样写出源上送的线路段」</b>，
 * 不必回滚镜像。</p>
 *
 * <p><b>覆盖是无条件的，这正是它能安全上线的原因</b>：补齐逻辑不判断源上送的线路段是空还是有值，
 * 一律按车站码重算并覆盖。因为线路是车站的函数，对「还没改、仍自带线路」的旧源来说，
 * 覆盖进去的是同一个值、文件逐字节不变；对「已改、线路留空」的新源才是真正填上。
 * 于是 <b>四个源与 recon-server 的部署顺序完全自由</b>，不存在「必须同批上线」的窗口。
 * <b>NEVER 把它改成「只在源上送为空时才补」</b> —— 那样就重新引入了顺序依赖，而且一旦某个源
 * 把线路取错，错值会被当成「有值」原样放过。</p>
 */
@ConfigurationProperties(prefix = "recon.line-backfill")
public class ReconLineBackfillProperties {

    /** 总开关。关掉即完全不查 {@code STATION_INFO}、原样写出源上送的键段。 */
    private boolean enabled = true;

    /**
     * 需要补线路的文件类型名（{@code ReconFileType} 的枚举名）。
     *
     * <p>只有 PAY 有「线路 / 车站」这两段。BUS 只有 1 段键（日期），EXP 与 DETAIL 走明细路径、
     * 是字节流式拼接、全程不解析行内容，<b>因此明细文件的线路段无法在本模块补</b>，
     * 往这里加 EXP 或 DETAIL 不会生效也不会报错，NEVER 加。</p>
     */
    private Set<String> fileTypes = new LinkedHashSet<>(Set.of("PAY"));

    /** 线路段在行中的下标（0 基）。甲方 21 段 PAY 行格式的第 2 段。 */
    private int lineFieldIndex = 1;

    /** 车站段在行中的下标（0 基）。甲方 21 段 PAY 行格式的第 3 段，补线路时按它查维表。 */
    private int stationFieldIndex = 2;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> getFileTypes() { return fileTypes; }
    public void setFileTypes(Set<String> fileTypes) {
        this.fileTypes = fileTypes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(fileTypes);
    }
    public int getLineFieldIndex() { return lineFieldIndex; }
    public void setLineFieldIndex(int lineFieldIndex) { this.lineFieldIndex = lineFieldIndex; }
    public int getStationFieldIndex() { return stationFieldIndex; }
    public void setStationFieldIndex(int stationFieldIndex) { this.stationFieldIndex = stationFieldIndex; }

    /** 该文件类型是否需要补线路。类型名大小写不敏感，避免配置里写成小写就静默失效。 */
    public boolean appliesTo(String fileTypeName) {
        if (!enabled || fileTypeName == null) return false;
        return fileTypes.stream().anyMatch(fileTypeName::equalsIgnoreCase);
    }
}
