package com.baidu.android.voicedemo.wakeup;

import android.os.Handler;
import android.os.Message;

import com.baidu.android.voicedemo.recognization.IStatus;

/**
 * Created by fujiayi on 2017/9/21.
 */

public class RecogWakeupListener extends SimpleWakeupListener implements IStatus {

    private static final String TAG = "RecogWakeupListener";

    private Handler handler;

    public RecogWakeupListener(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void onSuccess(String word, WakeUpResult result) {
        super.onSuccess(word, result);
        Message message = handler.obtainMessage(STATUS_WAKEUP_SUCCESS);
        message.obj = word;
        handler.sendMessage(message);
    }
}
