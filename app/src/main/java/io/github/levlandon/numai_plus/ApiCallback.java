package io.github.levlandon.numai_plus;

interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(ApiError error);
}