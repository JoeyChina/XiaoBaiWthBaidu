package com.baidu.android.voicedemo.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.android.voicedemo.control.MyRecognizer;
import com.baidu.android.voicedemo.control.MyWakeup;
import com.baidu.android.voicedemo.pinyin.PinyinSimilarity;
import com.baidu.android.voicedemo.recognization.ChainRecogListener;
import com.baidu.android.voicedemo.recognization.CommonRecogParams;
import com.baidu.android.voicedemo.recognization.IStatus;
import com.baidu.android.voicedemo.recognization.MessageStatusRecogListener;
import com.baidu.android.voicedemo.recognization.offline.OfflineRecogParams;
import com.baidu.android.voicedemo.recognization.online.OnlineRecogParams;
import com.baidu.android.voicedemo.wakeup.IWakeupListener;
import com.baidu.android.voicedemo.wakeup.RecogWakeupListener;
import com.baidu.speech.asr.SpeechConstant;
import com.baidu.speech.xiaobai.R;
import com.baidu.voicerecognition.android.ui.BaiduASRDigitalDialog;
import com.baidu.voicerecognition.android.ui.DigitalDialogInput;
import com.baidu.voicerecognition.android.ui.SimpleTransApplication;
import com.canall.gateway.beans.GatewayBean;
import com.canall.gateway.beans.SceneBean;
import com.canall.gateway.beans.StatusBean;
import com.canall.gateway.beans.SubDeviceBean;
import com.canall.gateway.net.ReqCallBack;
import com.canall.gateway.util.CanallGateway;
import com.canall.gateway.util.MD5Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyActivity extends Activity implements View.OnClickListener,IStatus {
    private String TAG = "MyActivity";
    private Button btn_start;


    /**
     * 识别控制器，使用MyRecognizer控制识别的流程
     */
    protected MyRecognizer myRecognizer;

    /*
     * Api的参数类，仅仅用于生成调用START的json字符串，本身与SDK的调用无关
     */
    protected CommonRecogParams apiParams;

    /*
     * 本Activity中是否需要调用离线命令词功能。根据此参数，判断是否需要调用SDK的ASR_KWS_LOAD_ENGINE事件
     */
    protected boolean enableOffline = false;

    /**
     * 对话框界面的输入参数
     */
    private DigitalDialogInput input;
    /**
     * 有2个listner，一个是用户自己的业务逻辑，如MessageStatusRecogListener。另一个是UI对话框的。
     * 使用这个ChainRecogListener把两个listener和并在一起
     */
    private ChainRecogListener listener;

    boolean running = false;

    /**
     * 控制UI按钮的状态
     */
    protected int status;

    protected Handler handler;
    protected MyWakeup myWakeup;

    private ScrollView scrollViewLog;
    private EditText et_input;
    private TextView log, clear;
    private Button btn_login, btn_getGateway, btn_getDevice, btn_getScene, btn_getUser, btn_CurrentGateway, btn_logout;
    private Button btn_clear, btn_runDevice, btn_runScene, btn_setGateway;
    CanallGateway canallGateway;
    private String[] errCode = {"操作成功",//0
            "操作失败",             // 1
            "用户名或密码错误",      //2
            "未定义",            //3
            "未定义",            //4
            "未定义",            //5
            "未定义",            //6
            "用户不存在",               //7
            "未定义",               //8
            "未定义",               //9
            "未定义",               //10
            "未定义",               //11
            "用户未登录",               //12
            "网关未设置",               //13
            "网关不存在",               //14
            "二维码失效",               //15

    };

    List<GatewayBean> gatewaylist = new ArrayList<>();
    List<SubDeviceBean> devicelist = new ArrayList<>();
    List<SceneBean> scenelist = new ArrayList<>();
    MediaPlayer mediaPlayer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        initView();
        initPermission();
        initRecog();
        handler = new Handler() {

            /*
             * @param msg
             */
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                handleMsg(msg);
            }

        };


        /*语音唤醒【语音指令】【语音控制】【听过指令】*/
        IWakeupListener listener = new RecogWakeupListener(handler);
        // 改为 SimpleWakeupListener 后，不依赖handler，但将不会在UI界面上显示
        myWakeup = new MyWakeup(this, listener);



        /*提示音*/
        setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mediaPlayer.seekTo(0);
            }
        });

        AssetFileDescriptor fileDescriptor = getResources().openRawResourceFd(R.raw.beep);

        try {
            mediaPlayer.setDataSource(fileDescriptor.getFileDescriptor(),
                    fileDescriptor.getStartOffset(),fileDescriptor.getLength());
            fileDescriptor.close();
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            mediaPlayer = null;
        }



    }

    private void initView() {
        btn_start = (Button) findViewById(R.id.btn_start);
        btn_start.setOnClickListener(this);

        scrollViewLog = (ScrollView) findViewById(R.id.sv_log);
        log = (TextView) findViewById(R.id.log);
        btn_clear = (Button) findViewById(R.id.btn_clear);
        btn_login = (Button) findViewById(R.id.btn_login);
        et_input = (EditText) findViewById(R.id.et_input);
        btn_clear = (Button) findViewById(R.id.btn_clear);
        clear = (TextView) findViewById(R.id.clear);
        btn_getGateway = (Button) findViewById(R.id.btn_getGateway);
        btn_getDevice = (Button) findViewById(R.id.btn_getDevice);
        btn_getScene = (Button) findViewById(R.id.btn_getScene);
        btn_getUser = (Button) findViewById(R.id.btn_getUser);
        btn_CurrentGateway = (Button) findViewById(R.id.btn_CurrentGateway);
        btn_logout = (Button) findViewById(R.id.btn_logout);
        btn_setGateway = (Button) findViewById(R.id.btn_setGateway);
        btn_runDevice = (Button) findViewById(R.id.btn_runDevice);
        btn_runScene = (Button) findViewById(R.id.btn_runScene);


        btn_login.setOnClickListener(this);
        log.setOnClickListener(this);
        btn_clear.setOnClickListener(this);
        clear.setOnClickListener(this);
        btn_getGateway.setOnClickListener(this);
        btn_getDevice.setOnClickListener(this);
        btn_getScene.setOnClickListener(this);
        btn_getUser.setOnClickListener(this);
        btn_CurrentGateway.setOnClickListener(this);
        btn_logout.setOnClickListener(this);
        btn_setGateway.setOnClickListener(this);
        btn_runDevice.setOnClickListener(this);
        btn_runScene.setOnClickListener(this);


        canallGateway = new CanallGateway();
        canallGateway.init(this);

    }

    @Override
    public void onClick(View v) {
        //隐藏键盘
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        String input = et_input.getText().toString();

        switch (v.getId()) {
            case R.id.btn_start:
                switch (status) {
                    case STATUS_NONE: // 初始状态
                        start();
                        status = STATUS_WAITING_READY;
                        updateBtnTextByStatus();
                        break;
                    case STATUS_WAITING_READY: // 调用本类的start方法后，即输入START事件后，等待引擎准备完毕。
                    case STATUS_READY: // 引擎准备完毕。
                    case STATUS_SPEAKING:
                    case STATUS_FINISHED: // 长语音情况
                    case STATUS_RECOGNITION:
                        stop();
                        status = STATUS_STOPPED; // 引擎识别中
                        updateBtnTextByStatus();
                        break;
                    case STATUS_STOPPED: // 引擎识别中
                        cancel();
                        status = STATUS_NONE; // 识别结束，回到初始状态
                        updateBtnTextByStatus();
                        break;
                    default:
                        break;
                }
                break;


            case R.id.btn_clear:
                log.setText("");
                break;
            case R.id.clear:
                et_input.setText("");
                break;
            case R.id.btn_login:
                login(input);
                break;
            case R.id.btn_getGateway:
                getGateway();
                break;
            case R.id.btn_getDevice:
                getDevice();
                break;
            case R.id.btn_getScene:
                getScene();
                break;
            case R.id.btn_getUser:
                setlog("当前用户 ", canallGateway.getCurrentUname());
                break;
            case R.id.btn_CurrentGateway:
                setlog("当前网关 ", "" + canallGateway.getCurrentgateway());
                break;
            case R.id.btn_logout:
                logout();
                break;
            case R.id.btn_setGateway:
                setGateway();
                break;
            case R.id.btn_runDevice:
                runDevice();
                break;
            case R.id.btn_runScene:
                runScene();
                break;
        }
    }


    /**
     * 在onCreate中调用。初始化识别控制类MyRecognizer
     */
    protected void initRecog() {
        listener = new ChainRecogListener();
        // DigitalDialogInput 输入 ，MessageStatusRecogListener可替换为用户自己业务逻辑的listener
        listener.addListener(new MessageStatusRecogListener(handler));
        myRecognizer = new MyRecognizer(this, listener); // DigitalDialogInput 输入
        apiParams = getApiParams();
        status = STATUS_NONE;
        if (enableOffline) {
            myRecognizer.loadOfflineEngine(OfflineRecogParams.fetchOfflineParams());
        }
    }


    /**
     * 开始录音，点击“开始”按钮后调用。
     */

    protected void start() {
        if(mediaPlayer!=null){
            mediaPlayer.start();
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, Object> params = apiParams.fetch(sp);  // params可以手动填入

        // BaiduASRDigitalDialog的输入参数
        input = new DigitalDialogInput(myRecognizer, listener, params);

        Intent intent = new Intent(this, BaiduASRDigitalDialog.class);
        // 在BaiduASRDialog中读取, 因此需要用 SimpleTransApplication传递input参数
        ((SimpleTransApplication) getApplicationContext()).setDigitalDialogInput(input);

        // 修改对话框样式
        // intent.putExtra(BaiduASRDigitalDialog.PARAM_DIALOG_THEME, BaiduASRDigitalDialog.THEME_ORANGE_DEEPBG);

        running = true;
        startActivityForResult(intent, 2);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        running = false;
        Log.i(TAG, "requestCode" + requestCode);
        if (requestCode == 2) {
            String message = "";
            if (resultCode == RESULT_OK) {
                ArrayList results = data.getStringArrayListExtra("results");
                if (results != null && results.size() > 0) {
                    message += results.get(0);
                }
            } else {
                message += "没有结果";
            }
            Message msg = handler.obtainMessage();
            msg.what = STATUS_FINISHED;
            msg.arg2 = 1;
            msg.obj = message;
            handler.sendMessage(msg);
        }

    }





    private CommonRecogParams getApiParams() {
        return new OnlineRecogParams(this);
    }


    void handleMsg(Message msg) {
        switch (msg.what) { // 处理MessageStatusRecogListener中的状态回调
            case STATUS_FINISHED:
                if (msg.arg2 == 1) {
///                   et_result.setText(msg.obj.toString());

                    setlogNoSpace("语音指令: " + msg.obj.toString(), changeToOurWords(msg.obj.toString())+"\n");

                    handlerBaiduYuyin(msg.obj.toString());

                }
                status = STATUS_NONE;
                updateBtnTextByStatus();
                break;
            case STATUS_NONE:
            case STATUS_READY:
            case STATUS_SPEAKING:
            case STATUS_RECOGNITION:
                status = msg.what;
                updateBtnTextByStatus();
                break;
            case STATUS_WAKEUP_SUCCESS:
                Log.i(TAG,"HHHH 语音唤醒成功");
                switch (status) {
                    case STATUS_NONE: // 初始状态
                        start();
                        status = STATUS_WAITING_READY;
                        updateBtnTextByStatus();
                        break;
                }
                break;
            default:
                break;

        }
    }

    /*
        转换为拼音
     */
    String changeToOurWords(String input){
        String output=input;

        output = new PinyinSimilarity(false).changeOurWordsWithPinyin(output);

        return output;
    }


    private void updateBtnTextByStatus() {
        switch (status) {
            case STATUS_NONE:
                btn_start.setText("语音控制");
                btn_start.setEnabled(true);
                break;
            case STATUS_WAITING_READY:
            case STATUS_READY:
            case STATUS_SPEAKING:
            case STATUS_RECOGNITION:
                btn_start.setText("停止录音");
                btn_start.setEnabled(true);
                break;

            case STATUS_STOPPED:
                btn_start.setText("取消整个识别过程");
                btn_start.setEnabled(true);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startWakeup();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopWakeup();
    }

    /**
     * 销毁时需要释放识别资源。
     */
    @Override
    protected void onDestroy() {
        myRecognizer.release();
        Log.i(TAG, "onDestory");
        super.onDestroy();
                if (!running) {
            myRecognizer.release();
        }

    }

    /**
     * 开始录音后，手动停止录音。SDK会识别在此过程中的录音。点击“停止”按钮后调用。
     */
    private void stop() {
        myRecognizer.stop();
    }

    /**
     * 开始录音后，取消这次录音。SDK会取消本次识别，回到原始状态。点击“取消”按钮后调用。
     */
    private void cancel() {
        myRecognizer.cancel();
    }


    /**
     * android 6.0 以上需要动态申请权限
     */
    private void initPermission() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        ArrayList<String> toApplyList = new ArrayList<String>();

        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
                // 进入到这里代表没有权限.

            }
        }
        String[] tmpList = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123);
        }

    }


    private void runScene() {
        if (et_input.getText() == null) {
            Toast.makeText(this, "输入为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String scene = et_input.getText().toString();
        SceneBean sceneBean = null;
        if (scenelist.size() > 0) {
            for (int i = 0; i < scenelist.size(); i++) {
                if (scene.equals(scenelist.get(i).getName()) ||
                        scene.equals(scenelist.get(i).getId())) {
                    sceneBean = scenelist.get(i);
                }
            }
            if (sceneBean == null) {
                setlog("执行场景 " + scene, "场景不存在");
            } else {
                final SceneBean finalSceneBean = sceneBean;
                canallGateway.runScene(sceneBean.getId(), new ReqCallBack<String>() {
                    @Override
                    public void onReqSuccess(String s) {
                        setlog("执行场景 " + finalSceneBean.getName(), "成功");
                    }

                    @Override
                    public void onReqFailed(String s) {
                        setlog("执行场景 " + finalSceneBean.getName(), printFail(s));
                    }

                    @Override
                    public void onReqError(Exception e) {
                        setlog("执行场景 " + finalSceneBean.getName(), "出错 " + e.toString());
                    }
                });
            }

        } else {
            Toast.makeText(this, "场景列表为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void runScene(String cmd) {
        if (TextUtils.isEmpty(cmd)) {
            Toast.makeText(this, "输入为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String scene = cmd;
        SceneBean sceneBean = null;
        if (scenelist.size() > 0) {
            for (int i = 0; i < scenelist.size(); i++) {
                if (scene.equals(scenelist.get(i).getName()) ||
                        scene.equals(scenelist.get(i).getId())) {
                    sceneBean = scenelist.get(i);
                }
            }
            if(sceneBean == null){
                for (int i = 0; i < scenelist.size(); i++) {
                    String pinyin_scene = changeToOurWords(scene);
                    String pinyin_sceneInList = changeToOurWords(scenelist.get(i).getName());
                    if (pinyin_scene.equals(pinyin_sceneInList)) {
                        sceneBean = scenelist.get(i);
                    }
                }
            }
            if (sceneBean == null) {
                setlog("执行场景 " + scene, "场景不存在");
            } else {
                final SceneBean finalSceneBean = sceneBean;
                canallGateway.runScene(sceneBean.getId(), new ReqCallBack<String>() {
                    @Override
                    public void onReqSuccess(String s) {
                        setlog("执行场景 " + finalSceneBean.getName(), "成功");
                    }

                    @Override
                    public void onReqFailed(String s) {
                        setlog("执行场景 " + finalSceneBean.getName(), printFail(s));
                    }

                    @Override
                    public void onReqError(Exception e) {
                        setlog("执行场景 " + finalSceneBean.getName(), "出错 " + e.toString());
                    }
                });
            }

        } else {
            Toast.makeText(this, "场景列表为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void runDevice() {
        int action = 0;
        String device;
        boolean ishave = false;
        SubDeviceBean deviceBean = null;
        if (et_input.getText() == null) {
            Toast.makeText(this, "输入为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (et_input.getText().toString().substring(0, 1).equals("开")) {
            action = 1;
            device = et_input.getText().toString().substring(1);
            if (devicelist.size() > 0) {
                for (int j = 0; j < devicelist.size(); j++) {
                    if (device.equals(devicelist.get(j).getAlias())) {
                        ishave = true;
                        deviceBean = devicelist.get(j);
                    }
                }
            } else {
                Toast.makeText(this, "设备列表为空", Toast.LENGTH_SHORT).show();
            }

        } else if (et_input.getText().toString().substring(0, 1).equals("关")) {
            action = 2;
            device = et_input.getText().toString().substring(1);
            if (devicelist.size() > 0) {
                for (int j = 0; j < devicelist.size(); j++) {
                    if (device.equals(devicelist.get(j).getAlias())) {
                        ishave = true;
                        deviceBean = devicelist.get(j);
                    }
                }
            } else {
                Toast.makeText(this, "设备列表为空", Toast.LENGTH_SHORT).show();
            }
        } else {
            action = 3;
            device = et_input.getText().toString();
            if (devicelist.size() > 0) {
                for (int j = 0; j < devicelist.size(); j++) {
                    if (device.equals(devicelist.get(j).getAlias())) {
                        ishave = true;
                        deviceBean = devicelist.get(j);
                    }
                }
            } else {
                Toast.makeText(this, "设备列表为空", Toast.LENGTH_SHORT).show();
            }
        }


        if (action != 0 && !TextUtils.isEmpty(device) && ishave && (deviceBean != null)) {
            List<StatusBean> statuslist = new ArrayList<>();
            StatusBean status = new StatusBean();
            status.setType(1);
            String ac = "未知操作";
            if (action == 1) {
                status.setValue("01");
                ac = "开";
            } else if (action == 2) {
                status.setValue("02");
                ac = "关";
            } else if (action == 3) {
                status.setValue("03");
                ac = "反转";
            }
            statuslist.add(status);

            final String finalAc = ac;
            final SubDeviceBean finalDeviceBean = deviceBean;
            canallGateway.setSubDevState(deviceBean.getParentId(), deviceBean.getSubDevType(), deviceBean.getSubDevId()
                    , statuslist, new ReqCallBack<String>() {
                        @Override
                        public void onReqSuccess(String s) {
                            setlog(finalAc + "设备 " + finalDeviceBean.getAlias(), "成功");
                        }

                        @Override
                        public void onReqFailed(String s) {
                            setlog(finalAc + "设备 " + finalDeviceBean.getAlias(), printFail(s));
                        }

                        @Override
                        public void onReqError(Exception e) {
                            setlog(finalAc + "设备 " + finalDeviceBean.getAlias(), "出错 " + e.toString());
                        }
                    });


        } else {
            Toast.makeText(this, "设备不存在", Toast.LENGTH_SHORT).show();
        }
    }

    private void runDevice(String cmd) {
        int action = 0;
        String device = null;
        boolean ishave = false;
        SubDeviceBean deviceBean = null;
        if (TextUtils.isEmpty(cmd)) {
            Toast.makeText(this, "输入为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cmd.substring(0, 2).equals("打开")) {
            action = 1;
            device = cmd.substring(2);
            if (devicelist.size() > 0) {
                for (int j = 0; j < devicelist.size(); j++) {
                    if (device.equals(devicelist.get(j).getAlias())) {
                        ishave = true;
                        deviceBean = devicelist.get(j);
                    }
                }
                if(!ishave){
                    for (int j = 0; j < devicelist.size(); j++) {
                        if(TextUtils.isEmpty(devicelist.get(j).getAlias()))
                            continue;
                        String pinyin_device = changeToOurWords(device);
                        String pinyin_deviceInList = changeToOurWords(devicelist.get(j).getAlias());
                        if (pinyin_device.equals(pinyin_deviceInList)) {
                            ishave = true;
                            deviceBean = devicelist.get(j);
                        }
                    }
                }
            } else {
                Toast.makeText(this, "设备列表为空", Toast.LENGTH_SHORT).show();
            }

        } else if (cmd.substring(0, 2).equals("关闭")) {
            action = 2;
            device = cmd.substring(2);
            if (devicelist.size() > 0) {
                for (int j = 0; j < devicelist.size(); j++) {
                    if (device.equals(devicelist.get(j).getAlias())) {
                        ishave = true;
                        deviceBean = devicelist.get(j);
                    }
                }

                if(!ishave){
                    for (int j = 0; j < devicelist.size(); j++) {
                        if(TextUtils.isEmpty(devicelist.get(j).getAlias()))
                            continue;
                        String pinyin_device = changeToOurWords(device);
                        String pinyin_deviceInList = changeToOurWords(devicelist.get(j).getAlias());
                        if (pinyin_device.equals(pinyin_deviceInList)) {
                            ishave = true;
                            deviceBean = devicelist.get(j);
                        }
                    }
                }
            } else {
                Toast.makeText(this, "设备列表为空", Toast.LENGTH_SHORT).show();
            }
        }


        if (action != 0 && !TextUtils.isEmpty(device) && ishave && (deviceBean != null)) {
            List<StatusBean> statuslist = new ArrayList<>();
            StatusBean status = new StatusBean();
            status.setType(1);
            String ac = "未知操作";
            if (action == 1) {
                status.setValue("01");
                ac = "开";
            } else if (action == 2) {
                status.setValue("02");
                ac = "关";
            } else if (action == 3) {
                status.setValue("03");
                ac = "反转";
            }
            statuslist.add(status);

            final String finalAc = ac;
            final SubDeviceBean finalDeviceBean = deviceBean;
            canallGateway.setSubDevState(deviceBean.getParentId(), deviceBean.getSubDevType(), deviceBean.getSubDevId()
                    , statuslist, new ReqCallBack<String>() {
                        @Override
                        public void onReqSuccess(String s) {
                            setlog(finalAc + "设备 " + finalDeviceBean.getAlias(), "成功");
                        }

                        @Override
                        public void onReqFailed(String s) {
                            setlog(finalAc + "设备 " + finalDeviceBean.getAlias(), printFail(s));
                        }

                        @Override
                        public void onReqError(Exception e) {
                            setlog(finalAc + "设备 " + finalDeviceBean.getAlias(), "出错 " + e.toString());
                        }
                    });


        } else {
            Toast.makeText(this, "设备不存在", Toast.LENGTH_SHORT).show();
            setlogNoSpace("设备 "+device,"不存在");
        }
    }


    private void setGateway() {
        String gateway = et_input.getText().toString();
        if (TextUtils.isEmpty(gateway)) {
            canallGateway.SetGateway(new ReqCallBack<String>() {
                @Override
                public void onReqSuccess(String s) {
                    setlog("登录网关 " + canallGateway.getCurrentgateway().getAlias(), "成功");
                }

                @Override
                public void onReqFailed(String s) {
                    setlog("登录网关 " + canallGateway.getCurrentgateway(), printFail(s));
                }

                @Override
                public void onReqError(Exception e) {
                    setlog("登录网关 " + canallGateway.getCurrentgateway(), "出错 " + e.toString());
                }
            });


        } else {
            GatewayBean gatewayBean = null;
            if (gatewaylist.size() > 0) {
                for (int i = 0; i < gatewaylist.size(); i++) {
                    if (gatewaylist.get(i).getAlias().equals(gateway) ||
                            gatewaylist.get(i).getMacAddr().equals(gateway)) {
                        gatewayBean = gatewaylist.get(i);
                    }
                }
                if (gatewayBean == null) {
                    Toast.makeText(this, gateway + "网关不存在", Toast.LENGTH_SHORT).show();
                } else {
                    final GatewayBean finalGatewayBean = gatewayBean;
                    canallGateway.SetGateway(gatewayBean.getMacAddr(), new ReqCallBack<String>() {
                        @Override
                        public void onReqSuccess(String s) {
                            setlog("登录网关 " + finalGatewayBean.getAlias(), "成功");
                        }

                        @Override
                        public void onReqFailed(String s) {
                            setlog("登录网关 " + finalGatewayBean.getAlias(), printFail(s));
                        }

                        @Override
                        public void onReqError(Exception e) {
                            setlog("登录网关 " + finalGatewayBean.getAlias(), "出错 " + e.toString());
                        }
                    });
                }

            } else {
                Toast.makeText(this, "网关列表为空", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setGateway(String cmd) {
        String gateway = cmd;
        if (TextUtils.isEmpty(gateway)) {
            canallGateway.SetGateway(new ReqCallBack<String>() {
                @Override
                public void onReqSuccess(String s) {
                    setlog("登录网关 " + canallGateway.getCurrentgateway().getAlias(), "成功");
                }

                @Override
                public void onReqFailed(String s) {
                    setlog("登录网关 " + canallGateway.getCurrentgateway(), printFail(s));
                }

                @Override
                public void onReqError(Exception e) {
                    setlog("登录网关 " + canallGateway.getCurrentgateway(), "出错 " + e.toString());
                }
            });


        } else {
            GatewayBean gatewayBean = null;
            if (gatewaylist.size() > 0) {
                for (int i = 0; i < gatewaylist.size(); i++) {
                    if (gatewaylist.get(i).getAlias().equals(gateway) ||
                            gatewaylist.get(i).getMacAddr().equals(gateway)) {
                        gatewayBean = gatewaylist.get(i);
                    }
                }
                if(gatewayBean ==null){
                    for (int i = 0; i < gatewaylist.size(); i++) {
                        String pinyin_gateway = changeToOurWords(gateway);
                        String pinyin_gatewayInList = changeToOurWords(gatewaylist.get(i).getAlias());
                        if (pinyin_gateway.equals(pinyin_gatewayInList) ) {
                            gatewayBean = gatewaylist.get(i);
                        }
                    }
                }

                if (gatewayBean == null) {
//                    Toast.makeText(this, gateway + "网关不存在", Toast.LENGTH_SHORT).show();
                    setlogNoSpace("切换网关 " + gateway, "不存在");
                } else {
                    final GatewayBean finalGatewayBean = gatewayBean;
                    canallGateway.SetGateway(gatewayBean.getMacAddr(), new ReqCallBack<String>() {
                        @Override
                        public void onReqSuccess(String s) {
                            setlog("登录网关 " + finalGatewayBean.getAlias(), "成功");
                        }

                        @Override
                        public void onReqFailed(String s) {
                            setlog("登录网关 " + finalGatewayBean.getAlias(), printFail(s));
                        }

                        @Override
                        public void onReqError(Exception e) {
                            setlog("登录网关 " + finalGatewayBean.getAlias(), "出错 " + e.toString());
                        }
                    });
                }

            } else {
                Toast.makeText(this, "网关列表为空", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void logout() {
        canallGateway.logout(new ReqCallBack<String>() {
            @Override
            public void onReqSuccess(String s) {
                setlog("登出 ", "成功");
            }

            @Override
            public void onReqFailed(String s) {
                setlog("登出 ", printFail(s));
            }

            @Override
            public void onReqError(Exception e) {
                setlog("登出 ", "出错 " + e.toString());
            }
        });
    }

    private void getScene() {
        scenelist.clear();
        canallGateway.getSceneList(new ReqCallBack<List<SceneBean>>() {
            @Override
            public void onReqSuccess(List<SceneBean> sceneBeans) {
                setlog("获取场景 ", "成功\n" + sceneBeans.toString());
                scenelist.addAll(sceneBeans);
            }

            @Override
            public void onReqFailed(String s) {
                setlog("获取场景 ", printFail(s));
            }

            @Override
            public void onReqError(Exception e) {
                setlog("获取场景 ", "出错 " + e.toString());
            }
        });
    }

    private void getDevice() {
        devicelist.clear();
        canallGateway.GetDevsList(new ReqCallBack<List<SubDeviceBean>>() {
            @Override
            public void onReqSuccess(List<SubDeviceBean> subDeviceBeans) {
                StringBuffer buffer = new StringBuffer();
                if (subDeviceBeans.size() > 0)
                    for (int i = 0; i < subDeviceBeans.size(); i++) {
                        if (!TextUtils.isEmpty(subDeviceBeans.get(i).getAlias())) {
                            buffer.append(subDeviceBeans.get(i).getAlias() + "\n");
                        }
                    }
                setlog("获取设备 ", "成功\n" + subDeviceBeans.toString() + "\n" + buffer);
                devicelist.addAll(subDeviceBeans);
            }

            @Override
            public void onReqFailed(String s) {
                setlog("获取设备 ", printFail(s));
            }

            @Override
            public void onReqError(Exception e) {
                setlog("获取设备 ", "出错 " + e.toString());
            }
        });
    }

    private void getGateway() {
        gatewaylist.clear();
        canallGateway.getGateway(new ReqCallBack<List<GatewayBean>>() {
            @Override
            public void onReqSuccess(List<GatewayBean> gatewayBeans) {
                setlog("获取网关 ", "成功\n" + gatewayBeans.toString());
                gatewaylist.addAll(gatewayBeans);
            }

            @Override
            public void onReqFailed(String s) {
                setlog("获取网关 ", printFail(s));
            }

            @Override
            public void onReqError(Exception e) {
                setlog("获取网关 ", "出错 " + e.toString());
            }
        });
    }

    private void login(String input) {
        final String user, pwd;
        if (!TextUtils.isEmpty(input)) {
            try {
                JSONObject json = new JSONObject(input);
                user = json.optJSONObject("data").optString("USER");
                pwd = json.optJSONObject("data").optString("PWD");
                if (!TextUtils.isEmpty(user) && !TextUtils.isEmpty(pwd)) {
                    login(user, pwd);
                } else {
                    setlog("登录", "错误的二维码格式");
                }

            } catch (JSONException e) {
                // e.printStackTrace();
                int dotIndex = -1;
                dotIndex = input.indexOf(",");
                if(dotIndex>0){
                    final String mUser = input.substring(0,dotIndex);
                    String mPwd = input.substring(dotIndex+1);
                    Log.i(TAG,"{mUser:"+mUser+" mPwd:"+mPwd+"}");
                    if(!TextUtils.isEmpty(mUser) && !TextUtils.isEmpty(mPwd)){
                        canallGateway.mlogin(mUser, MD5Util.md5(mPwd), new ReqCallBack<String>() {
                            @Override
                            public void onReqSuccess(String s) {
                                setlog("登录用户 " + mUser, "成功");
                            }

                            @Override
                            public void onReqFailed(String s) {
                                setlog("登录用户 " + mUser, printFail(s));
                            }

                            @Override
                            public void onReqError(Exception e) {
                                setlog("登录用户 " + mUser, "出错 " + e.toString());
                            }
                        });
                    }

                }else {
                    setlog("登录", "错误的二维码格式");
                }
            }
        } else {

            canallGateway.login(new ReqCallBack<String>() {
                @Override
                public void onReqSuccess(String s) {
                    setlog("登录保存用户 " + canallGateway.getCurrentUname(), "成功");
                }

                @Override
                public void onReqFailed(String s) {
                    setlog("登录保存用户 ", "失败  " + s + errCode[Integer.parseInt(s)]);
                }

                @Override
                public void onReqError(Exception e) {
                    setlog("登录保存用户 ", "出错  " + e.toString());
                }
            });
        }


    }

    private void login(final String user, String pwd) {
        canallGateway.login(user, pwd, new ReqCallBack<String>() {
            @Override
            public void onReqSuccess(String s) {
                setlog("登录用户 " + user, "成功");
            }

            @Override
            public void onReqFailed(String s) {
                setlog("登录用户 " + user, printFail(s));
            }

            @Override
            public void onReqError(Exception e) {
                setlog("登录用户 " + user, "出错 " + e.toString());
            }
        });
    }

    private void lognextLine() {
        log.setText(log.getText() + "\n\n\n");
    }


    private void logcurrentTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS  ");// HH:mm:ss
//获取当前时间
        Date date = new Date(System.currentTimeMillis());
        String time = simpleDateFormat.format(date);
        log.setText(log.getText() + time);
    }


    private void setlog(String reques, String result) {
        logcurrentTime();
        log.setText(log.getText() + "\t" + reques + "\n");
        log.setText(log.getText() + result);
        lognextLine();

        handler.post(new Runnable() {
            @Override
            public void run() {
                scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });

    }

    private void setlogNoSpace(String reques, String result) {
        logcurrentTime();
        log.setText(log.getText() + "\t" + reques + "\n");
        log.setText(log.getText() + result);
        handler.post(new Runnable() {
            @Override
            public void run() {
                scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });

    }

    private String printFail(String s) {
        int index = Integer.parseInt(s);
        if (index >= errCode.length) {
            return "失败 " + s;
        } else {
            return "失败 " + s + errCode[Integer.parseInt(s)];
        }

    }

    /* 处理百度语音指令*/
    private void handlerBaiduYuyin(String content) {
        if (TextUtils.isEmpty(content)) {
            return;
        }
        boolean canParse = false;
        int devIndex = -1;
        int sceneIndex = -1;
        int loginIndex = -1;
        int gwListIndex = -1;
        int devListIndex = -1;
        int sceneListIndex = -1;
        int switchGWIndex = -1;
        int contentSize = content.length();
        devIndex = content.indexOf("打开");
        if (devIndex < 0) {
            devIndex = content.indexOf("关闭");
        }
        sceneIndex = content.indexOf("场景");
        loginIndex = content.indexOf("登录");
        gwListIndex = content.indexOf("获取网关");
        devListIndex = content.indexOf("获取设备");
        sceneListIndex = content.indexOf("获取场景");
        switchGWIndex = content.indexOf("切换网关");
        Log.i(TAG, "devIndex " + switchGWIndex + " contentSize " + contentSize);
        if (devIndex >= 0) {
            if (devIndex + 2 < contentSize) {
                String devCmd = content.substring(devIndex);
                runDevice(devCmd);
                canParse = true;
            }
        } else if (sceneListIndex >= 0) {
            getScene();
            canParse = true;
        } else if (sceneIndex >= 0) {
            if (sceneIndex + 2 < contentSize) {
                String devCmd = content.substring(sceneIndex + 2);
                runScene(devCmd);
                canParse = true;
            }
        } else if (switchGWIndex >= 0) {
            if (switchGWIndex + 4 < contentSize) {
                String GwCmd = content.substring(switchGWIndex + 4);
                setGateway(GwCmd);
                canParse = true;
            }
        } else if (gwListIndex >= 0) {
            getGateway();
            canParse = true;
        } else if (devListIndex >= 0) {
            getDevice();
            canParse = true;
        } else if(loginIndex >= 0){
            String input = et_input.getText().toString();
            login(input);
            canParse = true;
        }

        if (!canParse) {
            log.setText(log.getText() + "不支持\n\n\n");
        }

    }




    // 点击“开始识别”按钮
    private void startWakeup() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put(SpeechConstant.WP_WORDS_FILE, "assets:///WakeUp.bin");
        // "assets:///WakeUp.bin" 表示WakeUp.bin文件定义在assets目录下

        // params.put(SpeechConstant.ACCEPT_AUDIO_DATA,true);
        // params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME,true);
        // params.put(SpeechConstant.IN_FILE,"res:///com/baidu/android/voicedemo/wakeup.pcm");
        // params里 "assets:///WakeUp.bin" 表示WakeUp.bin文件定义在assets目录下
        myWakeup.start(params);
    }


    protected void stopWakeup() {
        myWakeup.stop();
    }

}
