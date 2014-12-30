package io.rwilinski.tracely;

import org.json.JSONArray;

/**
 * Created by Rafal on 30.12.14.
 */
public class TracelyPingsThread implements Runnable {

    public JSONArray parameters;

    public TracelyPingsThread(JSONArray parameter) {
        this.parameters = parameter;
    }

    public void run() {
        TracelyPingAsyncTask task = new TracelyPingAsyncTask();
        task.execute(parameters);
    }
}