package android.content;

import android.net.Uri;

public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 0x00000001;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;

    private String action;
    private Uri data;
    private String type;
    private int flags = 0;

    public Intent() {}

    public Intent(String action) {
        this.action = action;
    }

    public Intent(String action, Uri uri) {
        this.action = action;
        this.data = uri;
    }

    public String getAction() {
        return action;
    }

    public Uri getData() {
        return data;
    }

    public String getType() {
        return type;
    }

    public int getFlags() {
        return flags;
    }

    public Intent setDataAndType(Uri data, String type) {
        this.data = data;
        this.type = type;
        return this;
    }

    public Intent addFlags(int flags) {
        this.flags |= flags;
        return this;
    }
}
