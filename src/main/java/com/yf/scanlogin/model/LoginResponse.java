package com.yf.scanlogin.model;

import java.util.concurrent.CountDownLatch;

public class LoginResponse {
    public CountDownLatch latch;
    public String user;
    public  Long getCodeTime;
    public  Long expirTime=30000L;
}
