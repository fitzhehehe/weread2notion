package com.huawei.hms.iap;

import android.app.Activity;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.iap.entity.PurchaseIntentReq;
import com.huawei.hms.iap.entity.PurchaseIntentResult;

public class IapClient {
    public Task<PurchaseIntentResult> createPurchaseIntent(PurchaseIntentReq req) { return new Task<>(); }
}
