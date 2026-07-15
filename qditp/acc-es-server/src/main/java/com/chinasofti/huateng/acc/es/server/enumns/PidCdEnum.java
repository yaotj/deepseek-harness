package com.chinasofti.huateng.acc.es.server.enumns;

import lombok.AllArgsConstructor;
import lombok.Getter;


/**
 * @author rxwnc
 */
@AllArgsConstructor
@Getter
public enum PidCdEnum {

    /**
     * 身份证
     */
    IDENTITY("00","居民身份证"),
    /**
     * 学生证
     */
    STUDENT("01","学生证"),
    /**
     * 护照
     */
    PASSPORT("02","护照"),
    /**
     * 军官证
     */
    OFFICER("03","军官证"),
    /**
     * 士兵证
     */
    SOLDIER("04","士兵证"),
    /**
     * 警官证
     */
    POLICE("05","警官证"),
    /**
     * 港澳通行证
     */
    HONG_KONG_OR_MACAO("06","港澳通行证"),
    /**
     * 台胞证
     */
    TAIWAN("07","台胞证"),
    /**
     * 驾驶证
     */
    DRIVING_LICENCE("09","驾驶证"),
    /**
     *居住证
     */
    RESIDENCE("10","居住证"),

    /**
     * 船名证
     */
    SHIP("11","船名证"),

    /**
     * 员工证
     */
    EMPLOYEE("12","员工证");


    private final String pidCode;

    private final String pidName;

    public static String getPidCode(String pidName) {
        for(PidCdEnum pic : PidCdEnum.values()) {
            if(pidName.equals(pic.getPidName())) {
                return pic.pidCode;
            }
        }
        return null;
    }


}
