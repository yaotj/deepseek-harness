package com.chinasofti.huateng.model.agm;

/**
 * AGM key material returned to a device during key synchronization.
 */
public class AgmKeyItemDTO {
    private String keyBathNumber;
    private String keyIdx;
    private String keyValue;
    private String keyEffectiveDate;
    private String kvc;
    private String reserve;

    public String getKeyBathNumber() { return keyBathNumber; }
    public void setKeyBathNumber(String keyBathNumber) { this.keyBathNumber = keyBathNumber; }
    public String getKeyIdx() { return keyIdx; }
    public void setKeyIdx(String keyIdx) { this.keyIdx = keyIdx; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
    public String getKeyEffectiveDate() { return keyEffectiveDate; }
    public void setKeyEffectiveDate(String keyEffectiveDate) { this.keyEffectiveDate = keyEffectiveDate; }
    public String getKvc() { return kvc; }
    public void setKvc(String kvc) { this.kvc = kvc; }
    public String getReserve() { return reserve; }
    public void setReserve(String reserve) { this.reserve = reserve; }
}
