package com.damon.test.domain.order;

public interface OrderStatus {

    int CREATE = 0;

    int CANCEL = 5;

    int RECEIVED = 10;

    int WATING_PAY = 2;

    int CREATE_FAILED = 6;

}
