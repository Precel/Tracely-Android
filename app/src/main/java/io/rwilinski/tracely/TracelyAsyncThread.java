package io.rwilinski.tracely;

import org.json.JSONArray;

/**
 * Created by Rafal on 30.12.14.
 */
public class TracelyAsyncThread implements Runnable {

    public JSONArray parameters;
    public TracelyHTTPAsyncTask task;

    public TracelyAsyncThread(JSONArray parameter) {
        this.parameters = parameter;
    }
    public TracelyAsyncThread(TracelyHTTPAsyncTask parameter) {
        this.task = parameter;
    }

    public void run() {
        if(parameters != null) {
            TracelyPingAsyncTask task = new TracelyPingAsyncTask();
            task.execute(parameters);
            TracelyManager.SetPingsTask(task);
        }
        else {
            task.execute("abc");
        }
    }
}