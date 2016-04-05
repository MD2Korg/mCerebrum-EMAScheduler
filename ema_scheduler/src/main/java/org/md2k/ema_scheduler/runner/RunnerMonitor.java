package org.md2k.ema_scheduler.runner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import com.google.gson.Gson;

import org.md2k.datakitapi.DataKitAPI;
import org.md2k.datakitapi.datatype.DataTypeString;
import org.md2k.datakitapi.source.METADATA;
import org.md2k.datakitapi.source.datasource.DataSourceBuilder;
import org.md2k.datakitapi.source.datasource.DataSourceClient;
import org.md2k.datakitapi.source.datasource.DataSourceType;
import org.md2k.datakitapi.source.platform.Platform;
import org.md2k.datakitapi.source.platform.PlatformBuilder;
import org.md2k.datakitapi.source.platform.PlatformType;
import org.md2k.datakitapi.time.DateTime;
import org.md2k.ema_scheduler.configuration.Application;
import org.md2k.ema_scheduler.configuration.EMAType;
import org.md2k.ema_scheduler.delivery.Callback;
import org.md2k.ema_scheduler.incentive.IncentiveManager;
import org.md2k.ema_scheduler.logger.LogInfo;
import org.md2k.ema_scheduler.logger.LoggerManager;
import org.md2k.utilities.Report.Log;
import org.md2k.utilities.data_format.NotificationAcknowledge;

/**
 * Created by monowar on 3/14/16.
 */
public class RunnerMonitor {
    public static final long NO_RESPONSE_TIME = 35000;

    private static final String TAG = RunnerMonitor.class.getSimpleName();
    IntentFilter intentFilter;
    Handler handler;
    Context context;
    long lastResponseTime;
    String message;
    String type;
    Application application;
    Survey survey;
    DataSourceClient dataSourceClient;
    EMAType emaType;
    Callback callback;

    Runnable runnableTimeOut = new Runnable() {
        @Override
        public void run() {
            if (DateTime.getDateTime() - lastResponseTime < NO_RESPONSE_TIME)
                handler.postDelayed(this, DateTime.getDateTime() - lastResponseTime);
            else {
                sendData();
                handler.postDelayed(runnableWaitThenSave,5000);
                //clear();
            }
        }
    };
    Runnable runnableWaitThenSave=new Runnable() {
        @Override
        public void run() {
            saveData(null, NotificationAcknowledge.TIMEOUT);
        }
    };
    private MyBroadcastReceiver myReceiver;
    boolean isStart=false;

    public RunnerMonitor(Context context, Callback callback) {
        this.context = context;
        this.callback=callback;

        myReceiver = new MyBroadcastReceiver();
        intentFilter = new IntentFilter("org.md2k.ema_scheduler.response");
        handler = new Handler();
        DataSourceBuilder dataSourceBuilder = createDataSourceBuilder();
        dataSourceClient = DataKitAPI.getInstance(context).register(dataSourceBuilder);
    }

    public void start(EMAType emaType, String status, Application application, String type) {
        isStart=true;
        context.registerReceiver(myReceiver, intentFilter);
        this.type = type;
        this.application = application;
        this.emaType=emaType;
        survey = new Survey();
        survey.start_timestamp = DateTime.getDateTime();
        survey.id = application.getId();
        survey.name = application.getName();
        survey.trigger_type = type;
        switch (status) {
            case NotificationAcknowledge.OK:
            case NotificationAcknowledge.DELAY_CANCEL:
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(application.getPackage_name());
                intent.setAction(application.getPackage_name());
                intent.putExtra("file_name", application.getFile_name());
                intent.putExtra("id", application.getId());
                intent.putExtra("name", application.getName());
                intent.putExtra("timeout", application.getTimeout());
                context.startActivity(intent);
                Log.d(TAG,"timeout="+application.getTimeout());
                handler.postDelayed(runnableTimeOut, application.getTimeout());
                log(LogInfo.STATUS_RUN_START, "EMA Starts");
                break;
            case NotificationAcknowledge.CANCEL:
                survey.status=LogInfo.STATUS_RUN_ABANDONED_BY_USER;
                survey.end_timestamp=DateTime.getDateTime();
                log(LogInfo.STATUS_RUN_ABANDONED_BY_USER, "EMA abandoned by user at prompt");
                saveToDataKit();
                clear();
                break;
            case NotificationAcknowledge.TIMEOUT:
                survey.status=LogInfo.STATUS_RUN_MISSED;
                survey.end_timestamp=DateTime.getDateTime();
                log(LogInfo.STATUS_RUN_MISSED, "EMA is timed out..at prompt..MISSED");
                saveToDataKit();
                clear();
                break;
        }
    }
    protected void log(String status, String message){
        if(type.equals("SYSTEM")) {
            LogInfo logInfo = new LogInfo();
            logInfo.setOperation(LogInfo.OP_RUN);
            logInfo.setId(emaType.getId());
            logInfo.setType(emaType.getType());
            logInfo.setTimestamp(DateTime.getDateTime());
            logInfo.setStatus(status);
            logInfo.setMessage(message);
            LoggerManager.getInstance(context).insert(logInfo);
        }
    }

    void sendData() {
        Intent intent = new Intent();
        intent.setAction("org.md2k.ema.operation");
        intent.putExtra("TYPE", "TIMEOUT");
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
    }

    void clear() {
        Log.d(TAG, "clear()...");
        if(isStart) {
            handler.removeCallbacks(runnableTimeOut);
            if (myReceiver != null)
                context.unregisterReceiver(myReceiver);
        }
        Log.d(TAG, "...clear()");
        isStart=false;

    }

    DataSourceBuilder createDataSourceBuilder() {
        Platform platform = new PlatformBuilder().setType(PlatformType.PHONE).setMetadata(METADATA.NAME, "Phone").build();
        DataSourceBuilder dataSourceBuilder = new DataSourceBuilder().setType(DataSourceType.SURVEY).setPlatform(platform);
        dataSourceBuilder = dataSourceBuilder.setMetadata(METADATA.NAME, "Survey");
        dataSourceBuilder = dataSourceBuilder.setMetadata(METADATA.DESCRIPTION, "EMA & EMI Question and answers");
        dataSourceBuilder = dataSourceBuilder.setMetadata(METADATA.DATA_TYPE, DataTypeString.class.getName());
        return dataSourceBuilder;
    }
    void showIncentive(){
        if(!survey.status.equals((LogInfo.STATUS_RUN_COMPLETED))) return;
        if(emaType.getIncentive_rules()==null) return;
        IncentiveManager incentiveManager=new IncentiveManager(context, emaType);
        incentiveManager.start();
    }

    void saveToDataKit() {
        showIncentive();
        Gson gson = new Gson();
        String json = gson.toJson(survey);
        Log.d(TAG, "survey=" + json);
        DataTypeString dataTypeString = new DataTypeString(DateTime.getDateTime(), json);
        DataKitAPI.getInstance(context).insert(dataSourceClient, dataTypeString);
        callback.onResponse(survey.status);
//        Toast.makeText(this, "Information is Saved", Toast.LENGTH_SHORT).show();
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra("TYPE");
            if (type.equals("RESULT")) {
                String answer = intent.getStringExtra("ANSWER");
                String status = intent.getStringExtra("STATUS");
                handler.removeCallbacks(runnableWaitThenSave);
                saveData(answer, status);
            } else if (type.equals("STATUS_MESSAGE")) {
                lastResponseTime = intent.getLongExtra("TIMESTAMP", -1);
                message = intent.getStringExtra("MESSAGE");
                Log.d(TAG, "data received... lastResponseTime=" + lastResponseTime + " message=" + message);
            }
        }
    }
    public void saveData(String answer, String status){
        survey.end_timestamp = DateTime.getDateTime();
        survey.question_answers = answer;
        survey.status = status;
        log(survey.status, survey.status);
        saveToDataKit();
        clear();

    }

}
