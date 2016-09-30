package com.kingfisher.proxy;

/**
 * Created by weili5 on 7/1/2016.
 */
public interface Rule {

    boolean meet(Context context);

    void process(Context context);

}
