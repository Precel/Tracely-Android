package io.rwilinski.tracely;

import android.content.Context;
import android.os.Build;
import android.os.Trace;
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
            // Random number to avoid duplicate files
            Random generator = new Random();
            int random = generator.nextInt(99999);

            // Embed version in stacktrace filename
            String filename = "Crash_"+ TracelyInfo.APP_VERSION+"-"+Integer.toString(random);
            Log.d(TAG, "Writing unhandled exception to: " + TracelyInfo.FILES_PATH + "/" + filename + ".stacktrace");

            String stringParams = "";

            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) TracelyManager.context.getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(metrics);

            // Write the stacktrace to disk
            BufferedWriter bos = new BufferedWriter(new FileWriter(TracelyInfo.FILES_PATH+"/"+filename+".stacktrace"));

            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(TracelyInfo.URL);
            JSONObject params = new JSONObject();
            JSONObject report = new JSONObject();
            JSONObject report_device = new JSONObject();
            JSONObject device = new JSONObject();

            device.put("manufacturer", Build.MANUFACTURER);
            device.put("model", android.os.Build.MODEL);
            device.put("os",0);
            device.put("os_version",Build.VERSION.RELEASE);
            device.put("processor_name", Runtime.getRuntime().availableProcessors());
            device.put("processor_cores", TracelyManager.getNumCores());
            device.put("ram", Runtime.getRuntime().maxMemory());
            device.put("screen_width", metrics.widthPixels);
            device.put("screen_height", metrics.heightPixels);
            device.put("dpi", metrics.densityDpi);

            String deviceString = device.toString();
            deviceString = deviceString.replaceAll("\\\\", "");
            String stackTrace;
            stackTrace = (e.getStackTrace().toString()).replace("\n","###HWDP###");
            stackTrace = stackTrace.toString().replace("\\n","###HWDP###");
            Log.d(TAG, "StackTrace: "+stackTrace);

            report.put("application", TracelyInfo.API_KEY);
            report.put("app_version", TracelyInfo.APP_VERSION);
            report.put("reason", e.getMessage());
            report.put("content", stackTrace);
            report.put("app_start", TracelyManager.getTimestamp());
            report.put("device", device);

            report_device.put("device_key", TracelyManager.GetDeviceId());
            report_device.put("disk_space_free", TracelyManager.spaceAvailable());
            report_device.put("disk_space_total", TracelyManager.spaceTotal());
            report_device.put("sd_space_free", TracelyManager.sdSpaceAvailable());
            report_device.put("sd_space_total", TracelyManager.sdSpaceTotal());
            report_device.put("battery_level", TracelyManager.getBatteryLevel());
            report_device.put("memory_usage", Runtime.getRuntime().totalMemory());
            report_device.put("carrier_name", TracelyManager.GetCarrier());
            report_device.put("rom_name", Build.DISPLAY);
            report_device.put("build_board", Build.BOARD);
            report_device.put("is_rooted", TracelyManager.isRooted());
            report_device.put("connection_type", TracelyManager.GetNetworkClass());

            params.put("report_device", report_device);
            params.put("report", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");

            bos.write(stringParams.toString());
            bos.flush();
            bos.close();

            Log.d(TAG, "StirngParams: "+stringParams);

            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);

            TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(httpPost);
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
