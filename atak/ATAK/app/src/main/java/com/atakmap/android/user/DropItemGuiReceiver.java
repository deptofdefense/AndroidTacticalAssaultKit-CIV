
package com.atakmap.android.user;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.CameraController;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DropItemGuiReceiver extends BroadcastReceiver {
    private final MapView _mapView;

    public DropItemGuiReceiver(MapView mapView) {
        _mapView = mapView;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        displayPopUp(context);
    }

    private void displayPopUp(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setMessage(R.string.point_dropper_text25);

        final View v = setUpView(context);

        builder.setView(v);

        builder.setPositiveButton(R.string.ok, null);

        builder.setNegativeButton(R.string.cancel, new OnClickListener() {

            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                /* Do nothing and close the dialog */
            }
        });

        final AlertDialog dialog = builder.create();

        // Set positive button handler in onShow so we don't have to close the dialog if something
        // is wrong.
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                Button ok = ((AlertDialog) d)
                        .getButton(AlertDialog.BUTTON_POSITIVE);
                ok.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View button) {
                        GeoPoint point = checkEnteredData(v);

                        if (point != null) {
                            broadcastPoint(point, v);
                            dialog.dismiss();
                        }
                    }
                });
            }
        });

        try {
            dialog.show();
        } catch (Exception ignore) {
        }

    }

    private View setUpView(Context context) {
        LayoutInflater inf = LayoutInflater.from(context);
        final View newView = inf.inflate(R.layout.dropitem_text_edit, null);

        final RadioGroup rg = newView.findViewById(R.id.favCon);
        final RadioGroup rgNS = newView
                .findViewById(R.id.favNorthSouth);
        final RadioGroup rgEW = newView
                .findViewById(R.id.favEastWest);
        final EditText eTop1 = newView.findViewById(R.id.editTop1);
        final EditText eTop2 = newView.findViewById(R.id.editTop2);
        final EditText eTop3 = newView.findViewById(R.id.editTop3);
        final EditText eBot1 = newView.findViewById(R.id.editBot1);
        final EditText eBot2 = newView.findViewById(R.id.editBot2);
        final EditText eBot3 = newView.findViewById(R.id.editBot3);
        final EditText eDecT = newView
                .findViewById(R.id.favEditDecTop);
        final EditText eDecB = newView
                .findViewById(R.id.favEditDecBot);
        final EditText eMGRS1 = newView.findViewById(R.id.favMGRS1);
        final EditText eMGRS2 = newView.findViewById(R.id.favMGRS2);
        final EditText eMGRS3 = newView.findViewById(R.id.favMGRS3);
        final EditText eMGRS4 = newView.findViewById(R.id.favMGRS4);

        rg.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup arg0, int arg1) {

                /*
                 * The reason a switch is not being used is because of changes made in ADT 14 where
                 * the IDs are not final see: http://tools.android.com/tips/non-constant-fields
                 */

                if (arg1 == R.id.favMGRSBoss) {
                    // MGRS edit
                    eMGRS1.setVisibility(View.VISIBLE);
                    eMGRS2.setVisibility(View.VISIBLE);
                    eMGRS3.setVisibility(View.VISIBLE);
                    eMGRS4.setVisibility(View.VISIBLE);
                    eTop1.setVisibility(View.GONE);
                    eTop2.setVisibility(View.GONE);
                    eTop3.setVisibility(View.GONE);
                    eBot1.setVisibility(View.GONE);
                    eBot2.setVisibility(View.GONE);
                    eBot3.setVisibility(View.GONE);
                    rgEW.setVisibility(View.GONE);
                    rgNS.setVisibility(View.GONE);
                    eDecT.setVisibility(View.GONE);
                    eDecB.setVisibility(View.GONE);
                    newView.findViewById(R.id.degSymbT)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.minSymbT)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.secSymbT)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.degSymbB)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.minSymbB)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.secSymbB)
                            .setVisibility(View.GONE);
                } else if (arg1 == R.id.favDDBoss) {
                    // Decimal Degrees edit
                    eMGRS1.setVisibility(View.GONE);
                    eMGRS2.setVisibility(View.GONE);
                    eMGRS3.setVisibility(View.GONE);
                    eMGRS4.setVisibility(View.GONE);
                    eTop1.setVisibility(View.GONE);
                    eTop2.setVisibility(View.GONE);
                    eTop3.setVisibility(View.GONE);
                    eBot1.setVisibility(View.GONE);
                    eBot2.setVisibility(View.GONE);
                    eBot3.setVisibility(View.GONE);
                    rgEW.setVisibility(View.GONE);
                    rgNS.setVisibility(View.GONE);
                    eDecT.setVisibility(View.VISIBLE);
                    eDecB.setVisibility(View.VISIBLE);
                    newView.findViewById(R.id.degSymbT)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.minSymbT)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.secSymbT)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.degSymbB)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.minSymbB)
                            .setVisibility(View.GONE);
                    newView.findViewById(R.id.secSymbB)
                            .setVisibility(View.GONE);
                } else if (arg1 == R.id.favDMSBoss) {
                    // D-M-S edit
                    eMGRS1.setVisibility(View.GONE);
                    eMGRS2.setVisibility(View.GONE);
                    eMGRS3.setVisibility(View.GONE);
                    eMGRS4.setVisibility(View.GONE);
                    eTop1.setVisibility(View.VISIBLE);
                    eTop2.setVisibility(View.VISIBLE);
                    eTop3.setVisibility(View.VISIBLE);
                    eBot1.setVisibility(View.VISIBLE);
                    eBot2.setVisibility(View.VISIBLE);
                    eBot3.setVisibility(View.VISIBLE);
                    rgEW.setVisibility(View.VISIBLE);
                    rgNS.setVisibility(View.VISIBLE);
                    eDecT.setVisibility(View.GONE);
                    eDecB.setVisibility(View.GONE);
                    newView.findViewById(R.id.degSymbT).setVisibility(
                            View.VISIBLE);
                    newView.findViewById(R.id.minSymbT).setVisibility(
                            View.VISIBLE);
                    newView.findViewById(R.id.secSymbT).setVisibility(
                            View.VISIBLE);
                    newView.findViewById(R.id.degSymbB).setVisibility(
                            View.VISIBLE);
                    newView.findViewById(R.id.minSymbB).setVisibility(
                            View.VISIBLE);
                    newView.findViewById(R.id.secSymbB).setVisibility(
                            View.VISIBLE);
                }
            }
        });

        return newView;
    }

    private GeoPoint checkEnteredData(View v) {
        GeoPoint geoPoint = null;

        final RadioGroup rg = v.findViewById(R.id.favCon);
        final RadioGroup rgNS = v.findViewById(R.id.favNorthSouth);
        final RadioGroup rgEW = v.findViewById(R.id.favEastWest);
        final EditText eTop1 = v.findViewById(R.id.editTop1);
        final EditText eTop2 = v.findViewById(R.id.editTop2);
        final EditText eTop3 = v.findViewById(R.id.editTop3);
        final EditText eBot1 = v.findViewById(R.id.editBot1);
        final EditText eBot2 = v.findViewById(R.id.editBot2);
        final EditText eBot3 = v.findViewById(R.id.editBot3);
        final EditText eDecT = v.findViewById(R.id.favEditDecTop);
        final EditText eDecB = v.findViewById(R.id.favEditDecBot);
        final EditText eMGRS1 = v.findViewById(R.id.favMGRS1);
        final EditText eMGRS2 = v.findViewById(R.id.favMGRS2);
        final EditText eMGRS3 = v.findViewById(R.id.favMGRS3);
        final EditText eMGRS4 = v.findViewById(R.id.favMGRS4);

        /*
         * The reason a switch is not being used is because of changes made in ADT 14 where the IDs
         * are not final see: http://tools.android.com/tips/non-constant-fields
         */

        if (rg.getCheckedRadioButtonId() == R.id.favDMSBoss) {
            String[] temp = {
                    eTop1.getText().toString(), eTop2.getText().toString(),
                    eTop3.getText().toString()
            };
            String[] temp1 = {
                    eBot1.getText().toString(), eBot2.getText().toString(),
                    eBot3.getText().toString()
            };
            if (!temp[0].isEmpty() || !temp[1].isEmpty()) {
                for (int i = 1; i < temp.length; i++) {
                    if (temp[i].isEmpty()) {
                        temp[i] = "0";
                    }
                }
                for (int j = 1; j < temp1.length; j++) {
                    if (temp1[j].isEmpty()) {
                        temp1[j] = "0";
                    }
                }
                try {
                    double mLat = Double.parseDouble(temp[0])
                            + Double.parseDouble(temp[1]) / 60
                            + Double.parseDouble(temp[2]) / 3600;

                    if (rgNS.getCheckedRadioButtonId() == R.id.radioTop2)
                        mLat *= -1;

                    double mLon = Double.parseDouble(temp1[0])
                            + Double.parseDouble(temp1[1]) / 60
                            + Double.parseDouble(temp1[2]) / 3600;

                    if (rgEW.getCheckedRadioButtonId() == R.id.radioBot2)
                        mLon *= -1;

                    if (validLatitude(mLat) && validLongitude(mLon))
                        geoPoint = new GeoPoint(mLat, mLon);
                    else if (!validLatitude(mLat))
                        printErrorMessage(ErrorEnum.BADLAT);
                    else
                        printErrorMessage(ErrorEnum.BADLON);
                } catch (NumberFormatException nfe) {
                    printErrorMessage(ErrorEnum.MISSINGDATA);
                }

            } else {
                printErrorMessage(ErrorEnum.MISSINGDATA);
            }

        } else if (rg.getCheckedRadioButtonId() == R.id.favDDBoss) {
            if (eDecT.getText().length() > 0 && eDecB.getText().length() > 0) {
                try {
                    double lat = Double.parseDouble(eDecT.getText().toString());
                    double lon = Double.parseDouble(eDecB.getText().toString());

                    if (validLatitude(lat) && validLongitude(lon))
                        geoPoint = new GeoPoint(lat, lon);
                    else if (!validLatitude(lat)) {
                        printErrorMessage(ErrorEnum.BADLAT);
                    } else {
                        printErrorMessage(ErrorEnum.BADLON);
                    }

                } catch (NumberFormatException nfe) {
                    printErrorMessage(ErrorEnum.MISSINGDATA);
                }
            } else {
                printErrorMessage(ErrorEnum.MISSINGDATA);
            }
        } else {
            if (eMGRS1.getText().length() > 0
                    && eMGRS2.getText().length() > 0) {
                try {
                    MGRSPoint mgrsPoint = MGRSPoint.decodeString(
                            eMGRS1.getText().toString()
                                    .toUpperCase(Locale.ENGLISH)
                                    + eMGRS2.getText().toString()
                                            .toUpperCase(Locale.ENGLISH)
                                    +
                                    eMGRS3.getText().toString()
                                    + eMGRS4.getText().toString(),
                            Ellipsoid.WGS_84, null);

                    geoPoint = new GeoPoint(mgrsPoint.toLatLng(null)[0],
                            mgrsPoint.toLatLng(null)[1]);
                } catch (Exception err) {
                    printErrorMessage(ErrorEnum.BADMGRS);
                }
            } else {
                printErrorMessage(ErrorEnum.MISSINGMGRS);
            }
        }

        return geoPoint;
    }

    private static boolean validLatitude(double lat) {
        return (lat >= -90) && (lat <= 90);
    }

    private static boolean validLongitude(double lon) {
        return (lon >= -180) && (lon <= 180);
    }

    private String getCallsign() {
        StringBuilder builder = new StringBuilder();

        builder.append(_mapView.getDeviceCallsign());
        builder.append(".Object.");

        DateFormat dateFormat = new SimpleDateFormat("yDHms", Locale.ENGLISH);
        Date date = CoordinatedTime.currentDate();
        builder.append(dateFormat.format(date));

        return builder.toString();
    }

    private String getType(View v) {
        RadioGroup group = v.findViewById(R.id.favTypes);

        int id = group.getCheckedRadioButtonId();

        /*
         * The reason a switch is not being used is because of changes made in ADT 14 where the IDs
         * are not final see: http://tools.android.com/tips/non-constant-fields
         */

        if (id == R.id.radioFriendly)
            return "a-f-G";
        else if (id == R.id.radioHostile)
            return "a-h-G";
        else if (id == R.id.radioUnknown)
            return "a-u-G";
        else
            return "a-n-G";
    }

    private void printErrorMessage(ErrorEnum error) {
        Toast make;
        switch (error) {
            case BADLAT:
                make = Toast.makeText(_mapView.getContext(),
                        R.string.point_dropper_text33, Toast.LENGTH_SHORT);
                break;
            case BADLON:
                make = Toast.makeText(_mapView.getContext(),
                        R.string.point_dropper_text34, Toast.LENGTH_SHORT);
                break;
            case BADMGRS:
                make = Toast.makeText(_mapView.getContext(),
                        R.string.point_dropper_text35,
                        Toast.LENGTH_SHORT);
                break;
            case MISSINGDATA:
                make = Toast.makeText(_mapView.getContext(),
                        R.string.point_dropper_text36,
                        Toast.LENGTH_SHORT);
                break;
            case MISSINGMGRS:
                make = Toast.makeText(_mapView.getContext(),
                        R.string.point_dropper_text37,
                        Toast.LENGTH_SHORT);
                break;
            default:
                make = Toast.makeText(_mapView.getContext(),
                        R.string.point_dropper_text38, Toast.LENGTH_SHORT);
        }
        make.show();
    }

    private enum ErrorEnum {
        BADLAT,
        BADLON,
        BADMGRS,
        MISSINGDATA,
        MISSINGMGRS
    }

    private void broadcastPoint(GeoPoint point, View v) {
        new PlacePointTool.MarkerCreator(point)
                .setCallsign(getCallsign())
                .setType(getType(v))
                .placePoint();
        CameraController.Programmatic.panTo(
                _mapView.getRenderer3(), point, true);
    }
}
