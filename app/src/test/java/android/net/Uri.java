package android.net;

import java.io.File;

public class Uri {
    private final String uriString;

    protected Uri(String uriString) {
        this.uriString = uriString != null ? uriString : "";
    }

    public static Uri parse(String uriString) {
        return new Uri(uriString);
    }

    public static Uri fromFile(File file) {
        return new Uri(file != null ? "file://" + file.getAbsolutePath().replace('\\', '/') : "");
    }

    @Override
    public String toString() {
        return uriString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Uri)) return false;
        return uriString.equals(((Uri) o).uriString);
    }

    @Override
    public int hashCode() {
        return uriString.hashCode();
    }
}
