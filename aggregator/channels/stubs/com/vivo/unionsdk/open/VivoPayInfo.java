package com.vivo.unionsdk.open;

public class VivoPayInfo {
    public static class Builder {
        public Builder setAppId(String value) { return this; }
        public Builder setCpOrderNo(String value) { return this; }
        public Builder setNotifyUrl(String value) { return this; }
        public Builder setOrderAmount(String value) { return this; }
        public Builder setProductName(String value) { return this; }
        public Builder setProductDesc(String value) { return this; }
        public Builder setRoleId(String value) { return this; }
        public Builder setVivoSignature(String value) { return this; }
        public Builder setExtUid(String value) { return this; }
        public VivoPayInfo build() { return new VivoPayInfo(); }
    }
}
