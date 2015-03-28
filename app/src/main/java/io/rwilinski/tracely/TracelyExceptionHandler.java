package io.rwilinski.tracely;

import android.content.Context;
import android.os.Build;
import android.os.Trace;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by Rafal on 12.12.14.
 */
public class TracelyExceptionHandler implements Thread.UncaughtExceptionHandler {
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    private static final String TAG = "TracelyExceptionHandler";

    // constructor
    public TracelyExceptionHandler(Thread.UncaughtExceptionHandler pDefaultExceptionHandler)
    {
        Log.d(TAG, "Constructor called");
        defaultExceptionHandler = pDefaultExceptionHandler;
    }

    // Default exception handler
    public void uncaughtException(Thread t, Throwable e) {
        Log.d(TAG,"Crash caught by tracely.io");
        // Here you should have a more robust, permanent record of problems
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        try {
            String stringParams = "";

            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(TracelyInfo.URL + "exception_log/");
            JSONObject params = new JSONObject();
            JSONObject report = new JSONObject();
            JSONObject exception_data = new JSONObject();

            String stackTrace = "";
            try {
                Log.e(TAG, "MSG: " + e.getMessage());
                Log.e(TAG, "Cause: " + e.getCause() + ", causeStr: " + e.getCause().toString() + ", causeMsg: " + e.getCause().getMessage());
            }
            catch(Exception exc) {
                Log.e(TAG, "Couldn't get causes/messages");
                e.printStackTrace();
            }
            Log.e(TAG, "class: "+ e.getClass().getCanonicalName()+", "+e.getClass().getName()+", "+e.getClass().getSimpleName());

            JSONArray threads = new JSONArray();
            String stringEntry = "";

            Map<Thread, StackTraceElement[]> threadMap = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> entry : threadMap.entrySet()) {
                stringEntry = "";
                //Log.i(TAG, "Thread ID: "+entry.getKey().getId()+", stacktrace: "+entry.getKey().getStackTrace().length);
                for(int i = 0; i<entry.getKey().getStackTrace().length; i++) {
                    stringEntry += entry.getKey().getStackTrace()[i].toString()+"\n";
                }
                JSONObject thr = new JSONObject();
                thr.put("thread",entry.getKey().getName());
                thr.put("pid",entry.getKey().getId());
                thr.put("state",entry.getKey().getState().toString());
                thr.put("stacktrace", stringEntry);
                threads.put(thr);
            }
            Log.e(TAG, "Thread name: "+t.getName()+", State:"+t.getState().toString());
            Log.e(TAG, "getLocalizedMessage stacktrace: " + e.getLocalizedMessage());
            Log.e(TAG, "Classname stacktrace: "+e.getStackTrace()[0].getClassName());
            Log.e(TAG, "Filename stacktrace: "+e.getStackTrace()[0].getFileName());
            Log.e(TAG, "MethodName stacktrace: "+e.getStackTrace()[0].getMethodName());

            for(int i = 0; i<e.getStackTrace().length; i++){
                //Log.e(TAG, e.getStackTrace()[i].toString());
                stackTrace += e.getStackTrace()[i].toString()+"\n";
            }

            String content = Base64.encodeToString(stackTrace.getBytes("UTF-8"), Base64.NO_WRAP);

            int flag = 0;
            if(e instanceof Error) flag = 2;

            params.put("app", TracelyInfo.API_KEY);
            params.put("app_version", TracelyInfo.APP_VERSION);

            String cause = e.getLocalizedMessage() != null ? e.getClass().getName() +":"+ e.getLocalizedMessage() : e.getClass().getName() + " at " +e.getStackTrace()[0].getClassName() + "." + e.getStackTrace()[0].getMethodName()+ ":"+ e.getStackTrace()[0].getLineNumber();

            exception_data.put("name", e.getClass().getSimpleName());
            exception_data.put("reason", cause);
            exception_data.put("content", content);
            exception_data.put("threads", Base64.encodeToString(threads.toString().getBytes("UTF-8"), Base64.NO_WRAP));
            exception_data.put("flag", flag);

            report.put("user_log", TracelyManager.GetUserLog());
            report.put("system_log", TracelyManager.getLogcat());
            report.put("app_start", TracelyManager.getTimestamp());

            params.put("report_device", TracelyManager.GetReportedDevice());
            report.put("exception_data", exception_data);
            params.put("exception_log", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");

            Log.d(TAG, "StringParams: "+stringParams);

            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);

            TracelyManager.InterruptPingsTask();

            TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_REPORT));
            Runnable r = new TracelyAsyncThread(a);
            Thread th = new Thread(r);
            th.setPriority(Thread.MAX_PRIORITY);
            th.run();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        Log.d(TAG, result.toString());

        //call original handler
        defaultExceptionHandler.uncaughtException(t, e);
    }
}
