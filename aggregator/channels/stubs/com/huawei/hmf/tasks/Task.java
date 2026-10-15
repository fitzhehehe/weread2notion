package com.huawei.hmf.tasks;

public class Task<T> {
    public Task<T> addOnSuccessListener(OnSuccessListener<T> listener) { return this; }
    public Task<T> addOnFailureListener(OnFailureListener listener) { return this; }
}
