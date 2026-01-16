package com.zx.sms.codec.smpp;

/**
 * 消息payLoad是放在UD里还是 OptionParameter里
 */
public enum SmppSplitType {
	UDHPARAM("UDHPARAM"), //smpp3.4支持
	UDH("UDH"),  //smpp3.3支持
	PAYLOAD("PAYLOAD"), //smpp3.4支持
	PAYLOADPARAM("PAYLOADPARAM") //smpp3.4支持
	;
	  
    private final String value;
    
    public String getValue() {
        return value;
    }
    
    private SmppSplitType(String value) {
        this.value = value;
    }
}
