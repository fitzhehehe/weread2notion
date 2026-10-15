package com.huawei.hms.support.account;

import android.app.Activity;
import android.content.Intent;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.support.account.request.AccountAuthParams;
import com.huawei.hms.support.account.result.AuthAccount;
import com.huawei.hms.support.account.service.AccountAuthService;

public class AccountAuthManager {
    public static AccountAuthService getService(Activity activity, AccountAuthParams params) {
        return new AccountAuthService();
    }

    public static Task<AuthAccount> parseAuthResultFromIntent(Intent data) {
        return new Task<>();
    }
}
