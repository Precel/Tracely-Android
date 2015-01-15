package io.rwilinski.tracely;

/**
 * Created by Rafal on 02.01.15.
 */
public enum TracelyFlag {
    EXCEPTION,
    HANDLED_EXCEPTION,
    ERROR;

    public static TracelyFlag toEnum(int x) {
        switch(x) {
            case 0:
                return EXCEPTION;
            case 1:
                return HANDLED_EXCEPTION;
            case 2:
                return ERROR;
        }
        return null;
    }

    public static int toInt(TracelyFlag m) {
        switch(m) {
            case EXCEPTION:
                return 0;
            case HANDLED_EXCEPTION:
                return 1;
            case ERROR:
                return 2;
        }
        return -1;
    }
}
