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
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
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

            String stackTrace;
            stackTrace = (e.getStackTrace().toString());

            String content = Base64.encodeToString(stackTrace.getBytes("UTF-8"), Base64.NO_WRAP);

            params.put("app", TracelyInfo.API_KEY);
            params.put("app_version", TracelyInfo.APP_VERSION);
            report.put("reason", e.getMessage());
            report.put("content", content);
            report.put("user_log", TracelyManager.GetUserLog());
            report.put("system_log", TracelyManager.getLogcat());
            report.put("flag", 2);
            report.put("app_start", TracelyManager.getTimestamp());

            params.put("report_device", TracelyManager.GetReportedDevice());
            params.put("exception_log", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");

            Log.d(TAG, "StringParams: "+stringParams);

            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);

            TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_REPORT));
            a.execute("a", "b", "c");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        Log.d(TAG, result.toString());

        //call original handler
        defaultExceptionHandler.uncaughtException(t, e);
    }
}
