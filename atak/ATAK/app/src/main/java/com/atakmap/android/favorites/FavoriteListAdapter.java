
package com.atakmap.android.favorites;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Adapter for the favorite list supplied as part of the Maps and Favorites drop down.   Contains
 * the adapter as well as the parsing code responsible for ingesting the favs.txt file.
 */
public class FavoriteListAdapter extends BaseAdapter {

    public static final String FAVS = "::ATAK FAVORITES";
    public static final String TAG = "FavoriteListAdapter";

    private final static int VERSION = 7;

    protected final List<Favorite> mData;
    public static final String DIRNAME = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "favorites";
    private static final String FILENAME = "favs.txt";
    private static final String PATH_TO_FILE = FileSystemUtils.getItem(
            DIRNAME + File.separator + FILENAME).toString();

    protected final LayoutInflater mInflater;
    private static Drawable DELETE_ICON;
    private static Drawable EDIT_ICON;
    private static Drawable SEND_ICON;
    private static final String DELIMITER = "\t";
    protected final Context _context;

    protected final SharedPreferences _prefs;

    public FavoriteListAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
        mData = loadList();
        notifyDataSetChanged();
        _context = context;

        _prefs = PreferenceManager.getDefaultSharedPreferences(context);

        DELETE_ICON = getDrawable("icons/delete.png", "delete.png");
        EDIT_ICON = getDrawable("icons/edit2.png", "edit2.png");
        SEND_ICON = getDrawable("icons/send.png", "send.png");
    }

    private Drawable getDrawable(String fileName, String srcName) {
        InputStream is1 = null;
        try {
            return Drawable.createFromStream(
                    is1 = _context.getAssets().open(fileName),
                    srcName);
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        } finally {
            if (is1 != null) {
                try {
                    is1.close();
                } catch (IOException ignore) {
                }
            }
        }
        return null;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView,
            ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.favorite_row, null);
            holder = new ViewHolder();
            holder.titleText = convertView
                    .findViewById(R.id.favTitle);
            holder.locationText = convertView
                    .findViewById(R.id.favLocation);
            holder.mapTitle = convertView.findViewById(R.id.favMap);
            holder.editButton = convertView
                    .findViewById(R.id.favEdit);
            holder.editButton.setFocusable(false); // ImageButtons steal focus from the parent view
            holder.editButton.setImageDrawable(EDIT_ICON);
            holder.editButton.setScaleType(ScaleType.FIT_XY);

            holder.sendButton = convertView
                    .findViewById(R.id.favSend);
            holder.sendButton.setFocusable(false); // ImageButtons steal focus from the parent view
            holder.sendButton.setImageDrawable(SEND_ICON);
            holder.sendButton.setScaleType(ScaleType.FIT_XY);

            holder.deleteButton = convertView
                    .findViewById(R.id.favDelete);
            holder.deleteButton.setFocusable(false); // ImageButtons steal focus from the parent
                                                     // view
            holder.deleteButton.setImageDrawable(DELETE_ICON);
            holder.deleteButton.setScaleType(ScaleType.FIT_XY);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Favorite fav = mData.get(position);
        holder.editButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final EditText input = new EditText(v.getContext());
                input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                input.setText(fav.title);

                AlertDialog.Builder build = new AlertDialog.Builder(v
                        .getContext())
                                .setTitle(
                                        _context.getResources().getString(
                                                R.string.fav_dialogue))
                                .setView(input)
                                .setPositiveButton(R.string.ok,
                                        new DialogInterface.OnClickListener() {

                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                String result = input.getText()
                                                        .toString();
                                                if (result.length() > 0)
                                                    fav.title = result;
                                                notifyDataSetChanged();
                                                writeList();
                                            }
                                        })
                                .setNegativeButton(R.string.cancel, null);
                build.show();
            }
        });

        holder.deleteButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Builder builder = new Builder(v.getContext());
                builder.setTitle(_context
                        .getString(R.string.confirmation_dialogue));
                builder.setMessage(String.format(
                        _context.getString(
                                R.string.confirmation_remove_details),
                        fav.title));
                builder.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                removeFavorite(fav);

                            }
                        });
                builder.setNegativeButton(R.string.cancel, null);

                builder.show();
            }
        });

        holder.sendButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Favorite fav = mData.get(position);

                //save file to disk
                File file = FileSystemUtils
                        .getItem(FileSystemUtils.EXPORT_DIRECTORY
                                + File.separatorChar + "fav_"
                                + UUID.randomUUID().toString().substring(0, 5)
                                + ".txt");
                write(file, fav);

                //send as mission package
                if (FileSystemUtils.isFile(file))
                    MissionPackageApi.Send(file, fav.title);
                else
                    Log.w(TAG, "Failed to send: " + fav.toString());
            }
        });

        setText(holder, position);

        return convertView;
    }

    private void removeFavorite(final Favorite fav) {
        boolean res = mData.remove(fav);
        if (res) {
            Log.d(TAG, "removeFavorite: " + fav.toString());
        }

        notifyDataSetChanged();
        writeList();
    }

    protected String getLocationText(final int position,
            final CoordinateFormat cf) {
        final Favorite fav = mData.get(position);
        if (fav != null) {
            final GeoPoint gp = new GeoPoint(fav.latitude, fav.longitude);
            return CoordinateFormatUtilities.formatToString(gp, cf);
        }
        return "";
    }

    protected void setText(ViewHolder holder, int position) {
        String name = mData.get(position).title;
        if (name.length() > 24)
            holder.titleText.setTextSize(13);
        else if (name.length() > 18)
            holder.titleText.setTextSize(15);
        else
            holder.titleText.setTextSize(18);

        holder.titleText.setText(mData.get(position).title);
        CoordinateFormat coordForm = CoordinateFormat.find(_prefs.getString(
                "coord_display_pref",
                _context.getString(R.string.coord_display_pref_default)));
        String locText = getLocationText(position, coordForm);
        holder.locationText.setText(locText);
        if (mData.get(position).selection == null) {
            holder.mapTitle.setVisibility(View.GONE);
        } else if (mData.get(position).selection.contentEquals("null")) {
            holder.mapTitle.setVisibility(View.GONE);
        } else {
            holder.mapTitle.setVisibility(View.VISIBLE);
            holder.mapTitle.setText(mData.get(position).selection);
        }
    }

    private static List<Favorite> loadList() {
        return loadList(PATH_TO_FILE);
    }

    /**
     * Ability to load a set of favorites locations into the FavoriteLIst adapter
     * @param file the file to load the favories from in the favorite file format versions 2+
     * @return the list of favorites
     */
    public static List<Favorite> loadList(final String file) {
        ArrayList<Favorite> result = new ArrayList<>();

        try {
            File f = new File(
                    FileSystemUtils.sanitizeWithSpacesAndSlashes(file));
            if (IOProviderFactory.exists(f)) {
                try (InputStream is = IOProviderFactory.getInputStream(f);
                        InputStreamReader isr = new InputStreamReader(is,
                                FileSystemUtils.UTF8_CHARSET);
                        BufferedReader reader = new BufferedReader(isr)) {

                    String line;
                    int version = 0;
                    Favorite fav;
                    while ((line = reader.readLine()) != null) {
                        // drop all newlines from the input
                        line = line.replace("\n", "");
                        // process any header
                        if (line.startsWith("::")) {
                            if (version == 0 && line.startsWith("::VERSION")) {
                                // obtain the version
                                String versionStr = line.substring(9).trim();
                                if (versionStr.matches("\\d+"))
                                    version = Integer.parseInt(versionStr);
                            }

                            continue;
                        }

                        // parse the line per the version
                        switch (version) {
                            case 7:
                                fav = parseVersion7(line);
                                break;
                            case 6:
                                fav = parseVersion6(line);
                                break;
                            case 5:
                                fav = parseVersion5(line);
                                break;
                            case 4:
                                fav = parseVersion4(line);
                                break;
                            case 3:
                                fav = parseVersion3(line);
                                break;
                            case 2:
                                fav = parseVersion2(line);
                                break;
                            default:
                                fav = parseLegacy(line);
                                break;
                        }

                        if (fav != null)
                            result.add(fav);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private static Favorite parseLegacy(final String line) {
        final String[] parts = line.split(DELIMITER);
        try {
            final GeoPoint gp = CoordinateFormatUtilities.convert(parts[1],
                    CoordinateFormat.MGRS);
            if (gp == null)
                return null;

            if (parts.length == 3) {
                return new Favorite(parts[0], gp.getLatitude(),
                        gp.getLongitude(),
                        Double.parseDouble(parts[2]), 0d, 0d,
                        null, null, false,
                        false, CoordinatedTime.currentTimeMillis());
            } else if (parts.length == 4) {
                return new Favorite(parts[0], gp.getLatitude(),
                        gp.getLongitude(),
                        Double.parseDouble(parts[2]), 0d, 0d,
                        null, parts[3], true,
                        false, CoordinatedTime.currentTimeMillis());
            } else {
                return null;
            }
        } catch (Exception e) {
            Log.d(TAG, "error reading legacy line, skipping: " + line, e);
            return null;
        }
    }

    private static Favorite parseVersion2(final String line) {
        final String[] parts = line.split(DELIMITER);
        if (parts.length != 6)
            return null;
        try {
            final GeoPoint gp = CoordinateFormatUtilities.convert(parts[1],
                    CoordinateFormat.MGRS);
            if (gp == null)
                return null;

            return new Favorite(parts[0], gp.getLatitude(), gp.getLongitude(),
                    Double.parseDouble(parts[2]), 0d, 0d,
                    parts[3], parts[4],
                    Integer.parseInt(parts[5]) != 0,
                    false, CoordinatedTime.currentTimeMillis());
        } catch (Exception e) {
            Log.d(TAG, "error reading v2 line, skipping: " + line, e);
            return null;
        }
    }

    private static Favorite parseVersion3(final String line) {
        final String[] parts = line.split(DELIMITER);
        if (parts.length != 7)
            return null;

        try {
            return new Favorite(parts[0], Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    0d, 0d,
                    parts[4], parts[5], Integer.parseInt(parts[6]) != 0,
                    false, CoordinatedTime.currentTimeMillis());
        } catch (Exception e) {
            Log.d(TAG, "error reading v3 line, skipping: " + line, e);
            return null;
        }
    }

    private static Favorite parseVersion4(final String line) {
        final String[] parts = line.split(DELIMITER);
        if (parts.length != 8)
            return null;

        try {
            return new Favorite(parts[0], Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Double.parseDouble(parts[4]), 0d,
                    parts[5], parts[6], Integer.parseInt(parts[7]) != 0,
                    false, CoordinatedTime.currentTimeMillis());
        } catch (Exception e) {
            Log.d(TAG, "error reading v4 line, skipping: " + line, e);
            return null;
        }
    }

    private static Favorite parseVersion5(final String line) {
        final String[] parts = line.split(DELIMITER);
        if (parts.length != 9)
            return null;

        try {
            return new Favorite(parts[0], Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Double.parseDouble(parts[4]), Double.parseDouble(parts[5]),
                    parts[6], parts[7], Integer.parseInt(parts[8]) != 0,
                    false, CoordinatedTime.currentTimeMillis());
        } catch (Exception e) {
            Log.d(TAG, "error reading v5 line, skipping: " + line, e);
            return null;
        }
    }

    private static Favorite parseVersion6(final String line) {
        final String[] parts = line.split(DELIMITER);
        if (parts.length != 10)
            return null;

        try {
            return new Favorite(parts[0], Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Double.parseDouble(parts[4]), Double.parseDouble(parts[5]),
                    Double.parseDouble(parts[6]),
                    parts[7], parts[8], Integer.parseInt(parts[9]) != 0,
                    false, CoordinatedTime.currentTimeMillis());
        } catch (Exception e) {
            Log.d(TAG, "error reading v6 line, skipping: " + line, e);
            return null;
        }
    }

    private static Favorite parseVersion7(final String line) {
        final String[] parts = line.split(DELIMITER);
        if (parts.length != 12)
            return null;

        try {
            return new Favorite(parts[0], Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Double.parseDouble(parts[4]), Double.parseDouble(parts[5]),
                    Double.parseDouble(parts[6]),
                    parts[7], parts[8], Integer.parseInt(parts[9]) != 0,
                    Integer.parseInt(parts[10]) != 0,
                    Long.parseLong(parts[11]));
        } catch (Exception e) {
            Log.d(TAG, "error reading v7 line, skipping: " + line, e);
            return null;
        }
    }

    public void write(File file, FavoriteListAdapter.Favorite fav) {
        writeList(file, Collections.singletonList(fav));
    }

    public void writeList() {
        writeList(new File(PATH_TO_FILE), mData);
    }

    private void writeList(File file, List<Favorite> favs) {
        if (file == null) {
            Log.w(TAG, "writeList path invalid");
            return;
        }

        try (Writer fos = IOProviderFactory.getFileWriter(file);
                BufferedWriter bufferedWriter = new BufferedWriter(fos)) {
            File parent = file.getParentFile();
            if (parent != null && !IOProviderFactory.exists(parent)
                    && !IOProviderFactory.mkdirs(parent)) {
                Log.w(TAG, "Failed to create dirs: " + file.getAbsolutePath());
            }

            bufferedWriter.write(FAVS + "\n");
            bufferedWriter.write("::VERSION " + VERSION);
            Log.d(TAG, "write: " + file.getAbsolutePath());

            if (favs.isEmpty())
                return;

            Iterator<Favorite> iter = favs.iterator();
            StringBuilder sBuilder = new StringBuilder();
            Favorite i;
            do {
                i = iter.next();
                sBuilder.append("\n")
                        .append(i.title).append(DELIMITER)
                        .append(i.latitude).append(DELIMITER)
                        .append(i.longitude).append(DELIMITER)
                        .append(i.altitude).append(DELIMITER)
                        .append(i.zoomLevel).append(DELIMITER)
                        .append(i.tilt).append(DELIMITER)
                        .append(i.rotation).append(DELIMITER)
                        .append(i.layer).append(DELIMITER)
                        .append(i.selection).append(DELIMITER)
                        .append(i.locked ? "1" : "0").append(DELIMITER)
                        .append(i.illuminationEnabled ? "1" : "0")
                        .append(DELIMITER)
                        .append(i.illuminationDateTime).append(DELIMITER);
                bufferedWriter.write(sBuilder.toString());
                sBuilder.delete(0, sBuilder.length());
            } while (iter.hasNext());
        } catch (IOException io) {
            Log.e(TAG, "error: ", io);
        }
    }

    public static class Favorite implements Parcelable {

        public String title;
        public double latitude;
        public double longitude;
        public double altitude;
        public double zoomLevel;
        public double tilt;
        public double rotation;
        public String layer;
        public String selection;
        public boolean locked;
        public boolean illuminationEnabled;
        public long illuminationDateTime;

        Favorite(final String title,
                final double latitude,
                final double longitude,
                final double zoomLevel,
                final double tilt,
                final double rotation,
                final String layer,
                final String selection,
                final boolean locked,
                final boolean illuminationEnabled,
                final long illuminationDateTime) {
            this(title,
                    latitude,
                    longitude,
                    0d,
                    zoomLevel,
                    tilt,
                    rotation,
                    layer,
                    selection,
                    locked,
                    illuminationEnabled,
                    illuminationDateTime);
        }

        Favorite(final String title,
                final double latitude,
                final double longitude,
                final double altitude,
                final double zoomLevel,
                final double tilt,
                final double rotation,
                final String layer,
                final String selection,
                final boolean locked,
                final boolean illuminationEnabled,
                final long illuminationDateTime) {
            this.title = title;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.tilt = tilt;
            this.rotation = rotation;
            this.zoomLevel = zoomLevel;
            this.layer = layer;
            this.selection = selection;
            this.locked = locked;
            this.illuminationEnabled = illuminationEnabled;
            this.illuminationDateTime = illuminationDateTime;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(title);
            dest.writeDouble(latitude);
            dest.writeDouble(longitude);
            dest.writeDouble(altitude);
            dest.writeDouble(zoomLevel);
            dest.writeDouble(tilt);
            dest.writeDouble(rotation);
            dest.writeString(layer);
            dest.writeString(selection);
            dest.writeByte(locked ? (byte) 1 : (byte) 0);
            dest.writeByte(illuminationEnabled ? (byte) 1 : (byte) 0);
            dest.writeLong(illuminationDateTime);
        }

        public static final Creator<Favorite> CREATOR = new Creator<Favorite>() {
            @Override
            public Favorite createFromParcel(Parcel in) {
                return new Favorite(in);
            }

            @Override
            public Favorite[] newArray(int size) {
                return new Favorite[size];
            }
        };

        protected Favorite(Parcel in) {
            title = in.readString();
            latitude = in.readDouble();
            longitude = in.readDouble();
            altitude = in.readDouble();
            zoomLevel = in.readDouble();
            tilt = in.readDouble();
            rotation = in.readDouble();
            layer = in.readString();
            selection = in.readString();
            locked = (in.readByte() != 0);
            illuminationEnabled = (in.readByte() != 0);
            illuminationDateTime = in.readLong();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof Favorite))
                return false;
            if (obj == this)
                return true;
            return this.equals((Favorite) obj);
        }

        public boolean equals(Favorite c) {
            if (!FileSystemUtils.isEquals(layer, c.layer))
                return false;
            if (!FileSystemUtils.isEquals(selection, c.selection))
                return false;

            if (Double.compare(latitude, c.latitude) != 0)
                return false;
            if (Double.compare(longitude, c.longitude) != 0)
                return false;
            if (Double.compare(altitude, c.altitude) != 0)
                return false;
            if (Double.compare(zoomLevel, c.zoomLevel) != 0)
                return false;
            if (Double.compare(tilt, c.tilt) != 0)
                return false;
            if (Double.compare(rotation, c.rotation) != 0)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            return 31 * (int) ((layer == null ? 1 : layer.hashCode())
                    + (selection == null ? 1 : selection.hashCode())
                    + latitude + longitude + tilt + rotation + zoomLevel);
        }

        @Override
        public String toString() {
            return title + ", " + layer + ", "
                    + selection + ", " + latitude + ", "
                    + longitude + ", " + altitude + ", "
                    + zoomLevel + ", " + tilt + ", "
                    + rotation + ", " + locked + ", "
                    + illuminationEnabled + ", " + illuminationDateTime;
        }
    }

    protected static class ViewHolder {
        ImageButton editButton;
        ImageButton deleteButton;
        ImageButton sendButton;
        TextView titleText;
        TextView locationText;
        TextView mapTitle;
    }

    public void add(Favorite favorite) {
        if (favorite == null) {
            Log.w(TAG, "cannot add invalid favorite");
            return;
        }

        if (FileSystemUtils.isEmpty(favorite.title))
            favorite.title = "Location" + mData.size();

        if (mData.contains(favorite)) {
            Log.d(TAG, "skipping duplicate favorite");
            return;
        }

        Log.d(TAG, "add: " + favorite);
        try {
            mData.add(favorite);
            notifyDataSetChanged();
        } catch (Exception e) {
            Log.d(TAG, "error parsing favorite", e);
        }
    }

    public void add(final String title, final double latitude,
            final double longitude,
            final double zoom, final double tilt, double rotation,
            final String layer, final String selection,
            final boolean locked) {
        add(title, latitude, longitude, 0d, zoom, tilt, rotation, layer,
                selection, locked);
    }

    public void add(final String title, final double latitude,
            final double longitude, final double altitude,
            final double zoom, final double tilt, double rotation,
            final String layer, final String selection,
            final boolean locked) {
        add(title, latitude, longitude, altitude, zoom, tilt, rotation, layer,
                selection, locked, false, System.currentTimeMillis());
    }

    public void add(final String title, final double latitude,
            final double longitude, final double altitude,
            final double zoom, final double tilt, double rotation,
            final String layer, final String selection,
            final boolean locked, final boolean illuminationEnabled,
            final long illuminationDateTime) {
        final String favTitle;

        if (title == null || title.length() < 1)
            favTitle = "Location" + mData.size();
        else
            favTitle = title;

        add(new Favorite(favTitle, latitude, longitude, altitude, zoom, tilt,
                rotation, layer,
                selection, locked, illuminationEnabled, illuminationDateTime));
    }

}
