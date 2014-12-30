package io.rwilinski.tracely;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Dictionary;
import java.util.UUID;
import java.util.regex.Pattern;

public class TracelyManager {

    private static TracelyManager _instance;

    public static String LOGTAG = "TracelyManager";
    public static boolean isDebug = true;
    public static Context context;

    public static JSONArray USER_LOG;

    private static String[] stackTraceFileList = null;

    public TracelyManager() {
        this._instance = this;
    }

    public static TracelyManager getInstance() {
        if(_instance == null) {
            _instance = new TracelyManager();
        }
        return _instance;
    }

    public static void SetApiKey(String key) {
        TracelyInfo.API_KEY = key;
    }

    public static void SimulateHardCrash(Object contextObject) {
        TracelyCrashSimulator t = TracelyCrashSimulator.getInstance();
        t.recreate();
    }

    public static void SetDebug(boolean b) {
        isDebug = b;
    }

    public static void Logger(String msg) {
        if(isDebug) {
            Log.i(LOGTAG, msg);
        }
    }

    public static void Base64Encode(String str) {
        String result = "";
        Logger("-------BASE64 TESTS------");
        result = Base64.encodeToString(str.getBytes(), Base64.DEFAULT);
        Logger( "#1: "+result);

        try {
            result = Base64.encodeToString(str.getBytes("UTF-8"), Base64.DEFAULT);
            Logger("#2: "+result);

            result = Base64.encodeToString(str.getBytes( "ISO-8859-1"), Base64.DEFAULT);
            Logger("#3: "+result);
        }
        catch(Exception e) {
            Logger( "Bash64 Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean RegisterExceptionHandler(Object contextObject) {
        Context context = (Context) contextObject;
        TracelyManager.context = context;

        USER_LOG = new JSONArray();

        AddToUserLog("TRACELY","Tracely initialized from context "+context.getPackageName(), null);

        Logger( "Registering default exceptions handler");
        // Get information about the Package
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi;
            // Version
            pi = pm.getPackageInfo(context.getPackageName(), 0);
            TracelyInfo.APP_VERSION = pi.versionName;
            // Package name
            TracelyInfo.APP_PACKAGE = pi.packageName;
            // Files dir for storing the stack traces
            TracelyInfo.FILES_PATH = context.getFilesDir().getAbsolutePath();
            // Device model
            TracelyInfo.PHONE_MODEL = android.os.Build.MODEL;
            // Android version
            TracelyInfo.ANDROID_VERSION = android.os.Build.VERSION.RELEASE;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Logger( "TRACE_VERSION: " + TracelyInfo.TraceVersion);
        Log.d(LOGTAG, "APP_VERSION: " + TracelyInfo.APP_VERSION);
        Log.d(LOGTAG, "APP_PACKAGE: " + TracelyInfo.APP_PACKAGE);
        Log.d(LOGTAG, "FILES_PATH: " + TracelyInfo.FILES_PATH);
        Log.d(LOGTAG, "URL: " + TracelyInfo.URL);

        boolean stackTracesFound = false;
        // We'll return true if any stack traces were found
        if ( searchForStackTraces().length > 0 ) {
            stackTracesFound = true;
        }

        new Thread() {
            @Override
            public void run() {
                // First of all transmit any stack traces that may be lying around
                submitStackTraces();
                UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
                if (currentHandler != null) {
                    Log.d(LOGTAG, "current handler class="+currentHandler.getClass().getName());
                }
                // don't register again if already registered
                if (!(currentHandler instanceof TracelyExceptionHandler)) {
                    // Register default exceptions handler
                    Thread.setDefaultUncaughtExceptionHandler(
                            new TracelyExceptionHandler(currentHandler));
                }
            }
        }.start();

        Log.d(LOGTAG, "Exception handler registered successfully!");

        Launch();

        return stackTracesFound;
    }

    public static void submitStackTraces() {
        try {
            Log.d(LOGTAG, "Looking for exceptions in: " + TracelyInfo.FILES_PATH);
            String[] list = searchForStackTraces();
            if ( list != null && list.length > 0 ) {
                Log.d(LOGTAG, "Found "+list.length+" stacktrace(s)");
                for (int i=0; i < list.length; i++) {
                    String filePath = TracelyInfo.FILES_PATH+"/"+list[i];
                    Log.d(LOGTAG, "Stacktrace in file '"+filePath);
                    // Read contents of stacktrace
                    StringBuilder contents = new StringBuilder();
                    BufferedReader input =  new BufferedReader(new FileReader(filePath));
                    String line = null;
                    while (( line = input.readLine()) != null){
                        contents.append(line);
                    }
                    input.close();

                    String json = contents.toString();
                    JSONObject jsonObject = new JSONObject(json);
                    int method = jsonObject.getInt("method");
                    json = jsonObject.getString("json");

                    Log.d(LOGTAG, "Transmitting stack trace: " + json);

                    DefaultHttpClient httpClient = new DefaultHttpClient();

                    String URL = TracelyInfo.URL;
                    switch(TracelyMethod.toEnum(method)) {
                        case TRACELY_REPORT:
                            URL += "exception_log/";
                            break;
                        case TRACELY_LAUNCH:
                            URL += "launch/";
                            break;
                    }

                    HttpPost httpPost = new HttpPost(URL);
                    StringEntity se = new StringEntity(json, HTTP.UTF_8);
                    httpPost.setEntity(se);
                    TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.toEnum(method)));
                    a.execute("abcd");
                }
            }
            else {
                Log.d(LOGTAG, "Stack traces not found!");
            }
        } catch( Exception e ) {
            Log.d(LOGTAG,"Failed to submit stacktraces!");
            e.printStackTrace();
        } finally {
            try {
                String[] list = searchForStackTraces();
                for ( int i = 0; i < list.length; i ++ ) {
                    Log.d(LOGTAG, "Deleting "+list[i]);
                    File file = new File(TracelyInfo.FILES_PATH+"/"+list[i]);
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void saveRequestToStorage(String data) {
        Log.d(LOGTAG, "Saving: "+data);
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);

        try {
            String filename = "Crash_" + TracelyInfo.APP_VERSION + "-" + getTimestamp();
            BufferedWriter bos = new BufferedWriter(new FileWriter(TracelyInfo.FILES_PATH + "/" + filename + ".stacktrace"));
            bos.write(data);
            bos.flush();
            bos.close();

            Log.d(LOGTAG, "Writing file " + TracelyInfo.FILES_PATH + "/" + filename + ".stacktrace" + " success!");
        }
        catch(Exception e) {
            Logger( "Failed to save file! "+e.getMessage());
            e.printStackTrace();
        }
    }

    public static void TestConnection_1() {
        Log.d(LOGTAG, "Testing connection");
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(TracelyInfo.URL + "exception_log/");
        String stringParams = "";

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);

        try {
            JSONObject params = new JSONObject();
            JSONObject report = new JSONObject();

            String content = Base64.encodeToString("Stacktrace here... test test test".getBytes("UTF-8"), Base64.NO_WRAP);
            Log.d(LOGTAG, "CONTENT encoded: "+content);

            params.put("app", TracelyInfo.API_KEY);
            params.put("app_version", TracelyInfo.APP_VERSION);
            report.put("reason", "CRASH - test string");
            report.put("content", content);
            report.put("user_log", GetUserLog());
            report.put("system_log",  getLogcat());
            report.put("app_start", getTimestamp());
            report.put("flag", 0); //Exception

            params.put("report_device", GetReportedDevice());
            params.put("exception_log", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");
        }
        catch(Exception e) {
            Logger( "Json creation failed!");
            e.printStackTrace();
        }

        try {
            Log.d(LOGTAG, "StringParams: "+stringParams);
            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);
        }
        catch(Exception e) {
            Log.d(LOGTAG,"failed to set entity");
        }

        TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_REPORT));
        a.execute("a", "b", "c");
    }

    private static void Launch() {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(TracelyInfo.URL + "launch/");
        String stringParams = "";

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);

        try {
            JSONObject params = new JSONObject();
            JSONObject report = new JSONObject();

            params.put("app", TracelyInfo.API_KEY);
            params.put("app_version", TracelyInfo.APP_VERSION);
            report.put("app_start", getTimestamp());

            params.put("report_device", GetReportedDevice());
            params.put("launch", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");

        }
        catch(Exception e) {
            Logger( "Json creation failed!");
            e.printStackTrace();
        }

        try {
            Log.d(LOGTAG, "URL: "+TracelyInfo.URL + "launch/");
            Log.d(LOGTAG, "StringParams: "+stringParams);
            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);
        }
        catch(Exception e) {
            Log.d(LOGTAG,"failed to set entity");
        }
        TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_LAUNCH));
        a.execute("a", "b", "c");
    }

    public static void RegisterHandledException(String cause, String stackTrace) {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(TracelyInfo.URL + "exception_log/");
        String stringParams = "";

        try {
            JSONObject params = new JSONObject();
            JSONObject report = new JSONObject();

            String content = Base64.encodeToString(stackTrace.getBytes("UTF-8"), Base64.NO_WRAP);

            params.put("app", TracelyInfo.API_KEY);
            params.put("app_version", TracelyInfo.APP_VERSION);
            report.put("reason", cause);
            report.put("content", content);
            report.put("user_log", GetUserLog());
            report.put("system_log", getLogcat());
            report.put("app_start", getTimestamp());
            report.put("flag", 1);

            params.put("report_device", GetReportedDevice());
            params.put("exception_log", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");

        }
        catch(Exception e) {
            Logger( "Json creation failed!");
            e.printStackTrace();
        }

        try {
            Log.d(LOGTAG, "StringParams: "+stringParams);
            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);
        }
        catch(Exception e) {
            Log.d(LOGTAG,"failed to set entity");
        }

        TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_REPORT));
        a.execute("a", "b", "c");
    }

    public static void RegisterUnhandledException(String cause, String stackTrace) {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(TracelyInfo.URL + "exception_log/");
        String stringParams = "";

        try {
            JSONObject params = new JSONObject();
            JSONObject report = new JSONObject();

            String content = Base64.encodeToString(stackTrace.getBytes("UTF-8"), Base64.NO_WRAP);

            params.put("app", TracelyInfo.API_KEY);
            params.put("app_version", TracelyInfo.APP_VERSION);
            report.put("reason", cause);
            report.put("content", content);
            report.put("user_log", GetUserLog());
            report.put("system_log", getLogcat());
            report.put("app_start", getTimestamp());
            report.put("flag", 0);

            params.put("report_device", GetReportedDevice());
            params.put("exception_log", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");

        }
        catch(Exception e) {
            Logger( "Json creation failed!");
            e.printStackTrace();
        }

        try {
            Log.d(LOGTAG, "StringParams: "+stringParams);
            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);
        }
        catch(Exception e) {
            Log.d(LOGTAG,"failed to set entity");
        }

        TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_REPORT));
        a.execute("a", "b", "c");
    }

    public static void RegisterErrorException(String cause, String stackTrace) {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(TracelyInfo.URL + "exception_log/");
        String stringParams = "";

        try {
            JSONObject params = new JSONObject();
            JSONObject report = new JSONObject();

            String content = Base64.encodeToString(stackTrace.getBytes("UTF-8"), Base64.NO_WRAP);

            params.put("app", TracelyInfo.API_KEY);
            params.put("app_version", TracelyInfo.APP_VERSION);
            report.put("reason", cause);
            report.put("content", content);
            report.put("user_log", GetUserLog());
            report.put("system_log",  getLogcat());
            report.put("app_start", getTimestamp());
            report.put("flag", 2);

            params.put("report_device", GetReportedDevice());
            params.put("exception_log", report);

            stringParams = params.toString();
            stringParams = stringParams.replaceAll("\\\\","");

        }
        catch(Exception e) {
            Logger( "Json creation failed!");
            e.printStackTrace();
        }

        try {
            Log.d(LOGTAG, "StringParams: "+stringParams);
            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);
        }
        catch(Exception e) {
            Log.d(LOGTAG,"failed to set entity");
        }

        TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_REPORT));
        a.execute("a", "b", "c");
    }

    public static void SendPings(JSONArray array) {
        HttpPost httpPost = new HttpPost(TracelyInfo.URL + "latency/");
        String stringParams = "";
        try {
            Log.d(LOGTAG, "Sending pings array");

            JSONObject report = new JSONObject();

            report.put("app", TracelyInfo.API_KEY);
            report.put("app_version", TracelyInfo.APP_VERSION);
            report.put("report_device", GetReportedDevice());
            report.put("latency", array);

            stringParams = report.toString();
            stringParams = stringParams.replaceAll("\\\\","");

        }
        catch (Exception e) {
            Log.d(LOGTAG, "Failed to send pings... "+e.getMessage());
            e.printStackTrace();
        }

        try {
            Log.d(LOGTAG, "StringParams: "+stringParams);
            StringEntity se = new StringEntity(stringParams, HTTP.UTF_8);
            httpPost.setEntity(se);
        }
        catch(Exception e) {
            Log.d(LOGTAG,"failed to set entity");
        }

        TracelyHTTPAsyncTask a = new TracelyHTTPAsyncTask(new TracelyConnection(httpPost, TracelyMethod.TRACELY_PING));
        a.execute("a", "b", "c");
    }

    public static void HandleResponse(TracelyConnection connection) {
        try {
            Log.d(LOGTAG, "Request - "+connection.method.toString());
            Log.d(LOGTAG, "Response status code: " + connection.response.getStatusLine().getStatusCode());
            String responseBody = EntityUtils.toString(connection.response.getEntity());
            Log.d(LOGTAG, responseBody);

            if(connection.method == TracelyMethod.TRACELY_LAUNCH) {
                Log.d(LOGTAG, "Getting array of pings...");
                JSONArray pingsArray = new JSONArray(responseBody);

                Runnable r = new TracelyPingsThread(pingsArray);
                new Thread(r).start();
            }
            else {
                Log.d(LOGTAG, "No need to handle response.");
            }
        }
        catch(Exception e) {
            Log.d(LOGTAG, "Couldn't handle reponse! "+e.getMessage());
            e.printStackTrace();
        }
    }

    public static void AddToUserLog(String tag, String message, Dictionary<String, String> dict) {
        JSONObject json = new JSONObject();
        try {
            json.put("tag", tag);
            json.put("message", message);
            if(dict != null) {
                if(!dict.isEmpty()) {
                    JSONArray params = new JSONArray(dict);
                    json.put("extra_data", params);
                    USER_LOG.put(json);
                }
            }
        }
        catch(Exception e) {
            Log.w(LOGTAG,"Couldn't create User_log json. "+e.getMessage());
            e.getMessage();
        }
    }

    public static void AddToUserLog(String tag, String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("tag", tag);
            json.put("message", message);
            USER_LOG.put(json);
        }
        catch(Exception e) {
            Log.w(LOGTAG,"Couldn't create User_log json. "+e.getMessage());
            e.getMessage();
        }
    }

    public static String GetUserLog() {
        String json = USER_LOG.toString();
        json.replaceAll("\\\\","");
        try {
            return Base64.encodeToString(json.getBytes("UTF-8"), Base64.NO_WRAP);
        }
        catch(Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static JSONObject GetReportedDevice() {
        JSONObject report_device = new JSONObject();

        try {
            report_device.put("device_key", TracelyManager.GetDeviceId());
            report_device.put("disk_space_free", spaceAvailable());
            report_device.put("disk_space_total", spaceTotal());
            report_device.put("sd_space_free", sdSpaceAvailable());
            report_device.put("sd_space_total", sdSpaceTotal());
            report_device.put("battery_level", getBatteryLevel());
            report_device.put("memory_usage", Runtime.getRuntime().totalMemory());
            report_device.put("carrier_name", GetCarrier());
            report_device.put("rom_name", Build.DISPLAY);
            report_device.put("build_board", Build.BOARD);
            report_device.put("is_rooted", TracelyManager.isRooted());
            report_device.put("connection_type", TracelyManager.GetNetworkClass());
            report_device.put("screen_orientation",getDeviceOrientation());
            report_device.put("device", GetDevice());
        }
        catch(Exception e) {
            Logger( e.getMessage());
            e.printStackTrace();
        }

        return report_device;
    }

    public static JSONObject GetDevice() {
        JSONObject device = new JSONObject();

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);

        try {
            device.put("manufacturer", Build.MANUFACTURER);
            device.put("model", android.os.Build.MODEL);
            device.put("os", 0);
            device.put("os_version", Build.VERSION.RELEASE);
            device.put("processor_name", getCpuInfo());
            device.put("processor_cores", getNumCores());
            device.put("ram", Runtime.getRuntime().maxMemory());
            device.put("screen_width", metrics.widthPixels);
            device.put("screen_height", metrics.heightPixels);
            device.put("dpi", metrics.densityDpi);
        }
        catch(Exception e) {
            Logger( e.getMessage());
            e.printStackTrace();
        }

        return device;
    }

    public static boolean isRooted() {
        return findBinary("su");
    }

    public static boolean findBinary(String binaryName) {
        boolean found = false;
        if (!found) {
            String[] places = {"/sbin/", "/system/bin/", "/system/xbin/", "/data/local/xbin/",
                    "/data/local/bin/", "/system/sd/xbin/", "/system/bin/failsafe/", "/data/local/"};
            for (String where : places) {
                if ( new File( where + binaryName ).exists() ) {
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    public static String GetNetworkClass() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if(info==null || !info.isConnected())
            return "-"; //not connected
        if(info.getType() == ConnectivityManager.TYPE_WIFI)
            return "WIFI";
        if(info.getType() == ConnectivityManager.TYPE_MOBILE){
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                default:
                    return "?";
            }
        }
        return "?";
    }

    public static String GetDeviceId() {
        final TelephonyManager tm = (TelephonyManager) TracelyManager.context.getSystemService(Context.TELEPHONY_SERVICE);

        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(TracelyManager.context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
        String deviceId = deviceUuid.toString();
        return deviceId;
    }

    public static String getTimestamp() {
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        return ts;
    }

    public static String GetCarrier() {
        TelephonyManager manager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        String carrierName = manager.getNetworkOperatorName();
        return carrierName;
    }

    public static int getNumCores() {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                //Check if filename is "cpu", followed by a single digit number
                if(Pattern.matches("cpu[0-9]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            //Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            //Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            //Return the number of cores (virtual CPU devices)
            return files.length;
        } catch(Exception e) {
            //Default to return 1 core
            return 1;
        }
    }

    public static float getBatteryLevel() {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        // Error checking that probably isn't needed but I added just in case.
        if(level == -1 || scale == -1) {
            return 50.0f;
        }

        return ((float)level / (float)scale) * 100.0f;
    }

    public static float sdSpaceAvailable() {
        File f = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(f.getPath());
        long bytesAvailable = (long)stat.getBlockSizeLong() * (long)stat.getAvailableBlocksLong();
        return bytesAvailable / (1024.f * 1024.f);
    }

    public static float spaceAvailable() {
        File f = Environment.getRootDirectory();
        StatFs stat = new StatFs(f.getPath());
        long bytesAvailable = (long)stat.getBlockSizeLong() * (long)stat.getAvailableBlocksLong();
        return bytesAvailable / (1024.f * 1024.f);
    }

    public static float spaceTotal() {
        File f = Environment.getRootDirectory();
        StatFs stat = new StatFs(f.getPath());
        long bytesAvailable = stat.getTotalBytes();
        return bytesAvailable / (1024.f * 1024.f);
    }

    public static float sdSpaceTotal() {
        File f = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(f.getPath());
        long bytesAvailable = stat.getTotalBytes();
        return bytesAvailable / (1024.f * 1024.f);
    }

    private static String[] searchForStackTraces() {
        Log.d(LOGTAG, "Searching for stacktraces...");
        File dir = new File(TracelyInfo.FILES_PATH + "/");
        // Try to create the files folder if it doesn't exist
        dir.mkdir();
        // Filter for ".stacktrace" files
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".stacktrace");
            }
        };
        return (stackTraceFileList = dir.list(filter));
    }

    public static String getCpuInfo() {
        try {
            Process proc = Runtime.getRuntime().exec("cat /proc/cpuinfo");
            InputStream is = proc.getInputStream();
            return getStringFromInputStream(is);
        }
        catch (IOException e) {
            Log.e(LOGTAG, "------ getCpuInfo " + e.getMessage());
        }
        return "unknown";
    }

    private static String getStringFromInputStream(InputStream is) {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line = null;

        try {
            while((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");

                if(line.contains("Hardware")) {
                    return line.replace("Hardwaret: ", "");
                }
            }
        }
        catch (IOException e) {
            Log.e(LOGTAG, "--- getStringFromInputStream " + e.getMessage());
        }
        finally {
            if(br != null) {
                try {
                    br.close();
                }
                catch (IOException e) {
                    Log.e(LOGTAG, "------ getStringFromInputStream " + e.getMessage());
                }
            }
        }
        return "unknown";
    }

    public static int getDeviceOrientation() {
        final int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 1;
            case Surface.ROTATION_180:
                return 2;
            default:
                return 3;
        }
    }

    public static String getLogcat() {
        String logcat = "UNKNOWN";
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -t 100");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder log=new StringBuilder();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                if(!line.contains("Tracely")) log.append(line + "\n");
            }
            logcat = log.toString();
            Logger("Escaped logcat: "+logcat);
        }
        catch (IOException e) {
            logcat = "Failed to get, probably permission READ_LOGS is missing. True cause: "+e.getMessage();
            e.printStackTrace();
        }
        String output = "UNKNOWN";
        try {
            output = Base64.encodeToString(logcat.getBytes("UTF-8"), Base64.NO_WRAP);
            output.replaceAll("-","+");
        }
        catch(Exception e) {
            Logger( e.getMessage());
            e.printStackTrace();
            return "";
        }

        return output;
    }
}
