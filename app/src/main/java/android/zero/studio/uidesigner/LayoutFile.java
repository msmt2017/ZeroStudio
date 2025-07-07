package android.zero.studio.uidesigner;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import android.zero.studio.uidesigner.utils.FileUtil;

import org.jetbrains.annotations.Contract;

import java.io.File;

public class LayoutFile implements Parcelable {
    public static final Parcelable.Creator<LayoutFile> CREATOR =
        new Parcelable.Creator<>() {
            @NonNull
            @Contract("_ -> new")
            public LayoutFile createFromParcel(Parcel in) {
                return new LayoutFile(in);
            }

            @NonNull
            @Contract(value = "_ -> new", pure = true)
            public LayoutFile[] newArray(int size) {
                return new LayoutFile[size];
            }
        };
    public String name;
    private String path;

    public LayoutFile(String path) {
        this.path = path;
        this.name = FileUtil.getLastSegmentFromPath(path);
    }

    private LayoutFile(@NonNull Parcel parcel) {
        path = parcel.readString();
        name = parcel.readString();
    }

    public void rename(String newPath) {
        File newFile = new File(newPath);
        File oldFile = new File(getPath());
        oldFile.renameTo(newFile);

        path = newPath;
        name = FileUtil.getLastSegmentFromPath(path);
    }

    public void saveLayout(String content) {
        FileUtil.writeFile(path, content);
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String read() {
        return FileUtil.readFile(path);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags) {
        parcel.writeString(path);
        parcel.writeString(name);
    }
}
