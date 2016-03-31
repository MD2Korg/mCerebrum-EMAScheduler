package org.md2k.ema_scheduler.condition.privacy;

import android.content.Context;

import com.google.gson.Gson;

import org.md2k.datakitapi.DataKitAPI;
import org.md2k.datakitapi.datatype.DataType;
import org.md2k.datakitapi.datatype.DataTypeString;
import org.md2k.datakitapi.source.datasource.DataSource;
import org.md2k.datakitapi.source.datasource.DataSourceBuilder;
import org.md2k.datakitapi.source.datasource.DataSourceClient;
import org.md2k.datakitapi.time.DateTime;
import org.md2k.ema_scheduler.condition.Condition;
import org.md2k.ema_scheduler.configuration.ConfigCondition;
import org.md2k.utilities.Report.Log;

import java.util.ArrayList;

/**
 * Created by monowar on 3/26/16.
 */
public class PrivacyManager extends Condition{
    private static final String TAG = PrivacyManager.class.getSimpleName();

    public PrivacyManager(Context context){
        super(context);
    }
    public boolean isValid(ConfigCondition configCondition){
//        if(true) return true;
        Log.d(TAG,"isValid()...");
        DataKitAPI dataKitAPI=DataKitAPI.getInstance(context);
        DataSource dataSource = configCondition.getData_source();
        DataSourceBuilder dataSourceBuilder = new DataSourceBuilder(dataSource);
        ArrayList<DataSourceClient> dataSourceClientArrayList = dataKitAPI.find(dataSourceBuilder);
        Log.d(TAG,"isValid()...find()...size="+dataSourceClientArrayList.size());
        if (dataSourceClientArrayList.size() != 0) {
            ArrayList<DataType> dataTypes = dataKitAPI.query(dataSourceClientArrayList.get(0), 1);
            Log.d(TAG,"isValid()...dataTypes="+dataTypes.size());
            if (dataTypes.size() == 0) {
                log(configCondition, "true: datapoint not found");
                return true;
            }
            String sample = ((DataTypeString)dataTypes.get(0)).getSample();
            Gson gson=new Gson();
            PrivacyData privacyData=gson.fromJson(sample,PrivacyData.class);
            if (privacyData.isStatus() == false) {
                Log.d(TAG,"status=false");
                return true;
            }
            if(privacyData.getDuration().getValue()+privacyData.getStartTimeStamp()<= DateTime.getDateTime()) {
                Log.d(TAG,"privacytime < currenttime");
                return true;
            }
            for(int i=0;i<=privacyData.getPrivacyTypes().size();i++){
                if(privacyData.getPrivacyTypes().get(i).getId().equals("ema_intervention")) {
                    Log.d(TAG,"ema privacy enabled.");
                    return false;
                }
            }
            Log.d(TAG,"passed");
            return true;
        } else {
            Log.d(TAG,"datasource not found");
            log(configCondition, "true: datasource not found");
            return true;
        }
    }
}
