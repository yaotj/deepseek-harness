package com.chinasofti.huateng.fep.dev.model;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;

/**
 * IF1A-02 密钥同步响应业务参数。
 *
 * <p>2026-09-14：原先本类自带一份与 {@link CommonResult} 完全同名的 {@code retCode} / {@code retMsg}
 * 字段，是本模块四个响应类里<b>唯一</b>不继承 {@code CommonResult} 的一个，于是
 * {@code FepAgmController} 的「构造 INVALID_PARAM 响应」无法用一个泛型方法覆盖四条链路。
 * 现改为继承，字段名与个数不变（{@code CommonResult} 只有这两个字段、无任何序列化注解），
 * 因此<b>对闸机可见的 JSON 字段集合没有变化</b>——这一点由
 * {@code RequestSynKeyListRespDtoJsonShapeTest} 用 Fastjson2 实际序列化断言，NEVER 删那个测试。</p>
 *
 * <p><b>NEVER 在本类里重新声明 retCode / retMsg</b>：子类同名字段会遮蔽父类的，
 * Fastjson2 按 getter 取值时拿到的是哪一个取决于解析顺序，属于静默的取值错乱。</p>
 */
public class RequestSynKeyListRespDTO extends CommonResult {

    private List<KeyCurVerRespDTO> keyCurVerList;

    public List<KeyCurVerRespDTO> getKeyCurVerList() {
        return keyCurVerList;
    }

    public void setKeyCurVerList(List<KeyCurVerRespDTO> keyCurVerList) {
        this.keyCurVerList = keyCurVerList;
    }
}
