package com.sdwfqin.mvpdemo.model;

/**
 * Created by sdwfqin on 2017/1/13.
 */

public interface UserModel {
    public void login(String username, String password, OnLoginListener loginListener);
}