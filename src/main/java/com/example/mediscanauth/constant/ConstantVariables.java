package com.example.mediscanauth.constant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConstantVariables {
    public static String SIGNER_KEY;

    @Value("${constant.key.signer-key}")
    public void setSignerKey(String signerKey) {
        SIGNER_KEY = signerKey;
    }
}
