package com.chinasofti.huateng.collectpay.model.response.paycenter;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.constant.PayCenterErrorCodeEnum;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;
import lombok.Data;

/** IF2A-01 提交单程票订单应答报文（ITP -> TVM）。 */
@Data
public class PayCenterResult {

    private String code;
    private String msg;
    private String data;
    private String success;

    public static JSONObject success() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", PayCenterErrorCodeEnum.SUCCESS.getCode());
        jsonObject.put("msg",PayCenterErrorCodeEnum.SUCCESS.getMsg());
        return jsonObject;
    }

    public static JSONObject fail() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code",PayCenterErrorCodeEnum.FAIL.getCode());
        jsonObject.put("msg",PayCenterErrorCodeEnum.FAIL.getMsg());
        return jsonObject;
    }

    public static JSONObject failMessage(String msg) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code",PayCenterErrorCodeEnum.FAIL.getCode());
        jsonObject.put("msg",msg);
        return jsonObject;
    }

    public static JSONObject fail(String code,String msg) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code",code);
        jsonObject.put("msg",msg);
        return jsonObject;
    }


}
