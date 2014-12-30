package io.rwilinski.tracely;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by Rafal on 27.12.14.
 */
public class TracelyPingAsyncTask extends AsyncTask<JSONArray, Void, String> {

    public String LOGTAG = "TracelyPingAsyncTask";
    public JSONArray pingsArray;

    private int methodType = 0;

    protected String doInBackground(JSONArray... params) {
        pingsArray = new JSONArray();
        Log.d(LOGTAG, "Processing "+params[0].length()+" ping requests...");
        for(int j = 0; j < params[0].length(); j++) {
            try {
                JSONObject json = params[0].getJSONObject(j);
                String url = json.getString("value");
                String str = "";
                long elapsedTime = -1;
                if(methodType == 0) {
                    try {
                        Process process = Runtime.getRuntime().exec(
                                "/system/bin/ping -c 5 " + url);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                process.getInputStream()));
                        int i;
                        char[] buffer = new char[4096];
                        StringBuffer output = new StringBuffer();
                        while ((i = reader.read(buffer)) > 0)
                            output.append(buffer, 0, i);
                        reader.close();

                        // body.append(output.toString()+"\n");
                        str = output.toString();
                        str.substring(str.lastIndexOf("\n"));
                        String[] vars = str.split("/");
                        str = vars[4];
                        Log.i(LOGTAG, "#" + j + ": " + vars[4]);
                    } catch (IOException e) {
                        // body.append("Error\n");
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        long startTime = System.currentTimeMillis();
                        HttpURLConnection urlc = (HttpURLConnection) new URL("http://"+url).openConnection();
                        urlc.setRequestProperty("User-Agent", "Android Application:"+TracelyInfo.APP_VERSION);
                        urlc.setRequestProperty("Connection", "close");
                        urlc.setConnectTimeout(1000 * 10); // mTimeout is in seconds
                        urlc.connect();

                        if (urlc.getResponseCode() == 200) {
                            elapsedTime = (System.currentTimeMillis() - startTime);
                        }
                    }
                    catch (MalformedURLException e1) {
                        e1.printStackTrace();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                JSONObject cell = new JSONObject();
                cell.put("service", url);
                cell.put("ping", str);
                pingsArray.put(cell);
            }
            catch(Exception e) {
                Log.d(LOGTAG, "Failed to ping! "+e.getMessage());
                e.printStackTrace();
            }

        }
        TracelyManager.SendPings(pingsArray);
        return null;
    }
}
