package io.rwilinski.tracely;

/**
 * Created by Rafal on 27.12.14.
 */

public enum TracelyMethod {
    TRACELY_LAUNCH,
    TRACELY_PING,
    TRACELY_REPORT;

    public static TracelyMethod toEnum(int x) {
        switch(x) {
            case 0:
                return TRACELY_LAUNCH;
            case 1:
                return TRACELY_PING;
            case 2:
                return TRACELY_REPORT;
        }
        return null;
    }

    public static int toInt(TracelyMethod m) {
        switch(m) {
            case TRACELY_LAUNCH:
                return 0;
            case TRACELY_PING:
                return 1;
            case TRACELY_REPORT:
                return 2;
        }
        return -1;
    }
}
