package com.makotogo.mobile.framework;

import android.view.View;

/**
 * Created by sperry on 1/24/16.
 */
public interface ViewBinder<T> {

    void bind(T object, View view);


}