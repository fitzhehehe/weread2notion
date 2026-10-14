package com.huawei.hms.support.account.service;

import android.content.Intent;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.support.account.result.AuthAccount;

public class AccountAuthService {
    public Task<AuthAccount> silentSignIn() { return new Task<>(); }
    public Intent getSignInIntent() { return new Intent(); }
}
