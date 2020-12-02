package android.os;

public interface Parcelable {
    interface Creator<T> {
        T createFromParcel(Parcel source);
        T[] newArray(int size);
    }

    int describeContents();
    void writeToParcel(Parcel dest, int flags);
}
