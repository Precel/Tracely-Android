package io.rwilinski.tracely;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Rafal on 15.12.14.
 */
public class TracelyHTTPAsyncTask extends AsyncTask<String, Void, String> {

    private TracelyConnection connection;
    public TracelyHTTPAsyncTask(TracelyConnection connection){
        this.connection = connection;

    }

    @Override
    protected String doInBackground(String... params) {
        HttpClient httpClient = new DefaultHttpClient();

        try {
            HttpResponse response = httpClient.execute(connection.post);
            connection.response = response;
            int code = connection.response.getStatusLine().getStatusCode();
            if(code == 200) TracelyManager.HandleResponse(connection);
            else {
                Log.w("TracelyHTTPAsyncTask", "ERROR! Request response code: "+code);
                String responseBody = EntityUtils.toString(connection.response.getEntity());
                Log.d("TracelyHTTPAsyncTask", "Response: "+responseBody);
                if(connection.method != TracelyMethod.TRACELY_PING && code == 404 || code == 503) {
                    Log.d("TracelyHTTPAsyncTask", "Saving request for later use... Server probably unavailable");
                    saveRequest(connection);
                }
            }
        } catch (ClientProtocolException e) {
            Log.d("TracelyHttpClient", "Connection failed!");
            e.printStackTrace();
            saveRequest(connection);
        } catch (IOException e) {
            Log.d("TracelyHttpClient", "Connection failed!");
            e.printStackTrace();
            saveRequest(connection);
        }
        return "";
    }

    private void saveRequest(TracelyConnection connection) {
        try {
            HttpEntity he = connection.post.getEntity();
            InputStream is = he.getContent();
            InputStreamReader isr = new InputStreamReader(is);
            StringBuilder sb=new StringBuilder();
            BufferedReader br = new BufferedReader(isr);
            String read = br.readLine();

            while(read != null) {
                sb.append(read);
                read = br.readLine();
            }
            try {
                JSONObject json = new JSONObject(sb.toString());
                json.put("old", 1);
                JSONObject container = new JSONObject();
                container.put("method", TracelyMethod.toInt(connection.method));
                container.put("json",json);
                String stringParams = container.toString();
                stringParams = stringParams.replaceAll("\\\\","");

                TracelyManager.saveRequestToStorage(stringParams);
            }
            catch(Exception e) {
                TracelyManager.saveRequestToStorage(sb.toString());
            }
        }
        catch (Exception e) {
            Log.d("TracelyHTTPAsyncTask", "Failed to save request! "+e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onPostExecute(String result) {

    }
}
