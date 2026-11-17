package com.chinasofti.huateng.acc.es.server.enumns;

import lombok.AllArgsConstructor;
import lombok.Getter;



@AllArgsConstructor
@Getter
public enum PidCdEnum {


    IDENTITY("00","居民身份证"),

    STUDENT("01","学生证"),

    PASSPORT("02","护照"),

    OFFICER("03","军官证"),

    SOLDIER("04","士兵证"),

    POLICE("05","警官证"),

    HONG_KONG_OR_MACAO("06","港澳通行证"),

    TAIWAN("07","台胞证"),

    DRIVING_LICENCE("09","驾驶证"),

    RESIDENCE("10","居住证"),


    SHIP("11","船名证"),


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
