package io.rwilinski.tracely;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * Created by Rafal on 15.12.14.
 */
public class TracelyHTTPAsyncTask extends AsyncTask<String, Void, String> {

    private HttpPost httpHandler;
    public TracelyHTTPAsyncTask(HttpPost httpHandler){
        this.httpHandler = httpHandler;
    }

    @Override
    protected String doInBackground(String... params) {
        HttpClient httpClient = new DefaultHttpClient();

        try {
            HttpResponse response = httpClient.execute(httpHandler);
            Log.d("TracelyHttpClient", "Response status code: "+response.getStatusLine().getStatusCode());
            String responseBody = EntityUtils.toString(response.getEntity());
            Log.d("HTTP Response", responseBody);
        } catch (ClientProtocolException e) {
            Log.d("TracelyHttpClient", "Connection failed!");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d("TracelyHttpClient", "Connection failed!");
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected void onPostExecute(String result) {

    }
}
