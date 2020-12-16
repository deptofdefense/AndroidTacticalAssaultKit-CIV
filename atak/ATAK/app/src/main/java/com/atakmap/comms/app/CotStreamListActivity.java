
package com.atakmap.comms.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;

import com.atakmap.comms.CotServiceRemote;

public class CotStreamListActivity extends CotPortListActivity {

    public static final String TAG = "CotStreamListActivity";

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // connect to the service
        _remote = new CotServiceRemote();
        //get connection state callbacks
        _remote.setOutputsChangedListener(_outputsChangedListener);
        _remote.connect(_connectionListener);

        Intent intent = getIntent();
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                boolean add = extras.getBoolean("add");
                if (add) {
                    //Only consume the add flag once, observed due to orientation change
                    intent.removeExtra("add");
                    initiateAdd();
                }
            }
        }
    }

    /**
     * gets an array of descriptions for each CotPort in the list
     */
    private ArrayList<String> getDescriptionArray() {
        ArrayList<String> ret = new ArrayList<>(_portList.size());
        for (CotPort p : _portList)
            ret.add(p.getDescription());
        return ret;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result = true;
        if (super.onOptionsItemSelected(item))
            return true;

        if (item.getItemId() == R.id.add_menu_add) {
            initiateAdd();
        } else if (item.getItemId() == R.id.add_menu_remove_all) {
            showDialog(_REMOVE_ALL_DIALOG);
        }
        return result;
    }

    private void initiateAdd() {
        Log.d(TAG, "initiateAdd");
        Intent addNetInputIntent = new Intent(this,
                AddNetInfoActivity.class);
        addNetInputIntent.putExtra("type", "streaming");
        addNetInputIntent.putStringArrayListExtra("descriptions",
                getDescriptionArray());
        startActivityForResult(addNetInputIntent, _REQUEST_ADD_PORT);
    }

    @Override
    protected void addToRemote(String connectString, Bundle inputData) {
        _remote.addStream(connectString, inputData);
    }

    @Override
    protected void removeFromRemote(String connectString) {
        _remote.removeStream(connectString);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        switch (requestCode) {
            case _REQUEST_ADD_PORT:
                if (resultCode == Activity.RESULT_OK) {
                    final Bundle inputData = data.getBundleExtra("data");
                    final String connectString = data
                            .getStringExtra(CotPort.CONNECT_STRING_KEY);
                    if (connectString != null) {
                        try {
                            _remote.addStream(connectString, inputData);
                            inputData.putString(CotPort.CONNECT_STRING_KEY,
                                    connectString);
                            _putItem(new CotPort(inputData));

                            enrollForCertificate(inputData, null);

                        } catch (IllegalArgumentException iae) {
                            Log.d(TAG, "error occurred entering the server",
                                    iae);
                            toast("error occurred entering the server");
                        }
                    }
                }
                break;
            default:
                try {
                    super.onActivityResult(requestCode, resultCode, data);
                } catch (IllegalArgumentException iae) {
                    Log.d(TAG, "error occurred entering the server",
                            iae);
                    toast("error occurred entering the server");
                }
                break;
        }
    }

    private void toast(final String toast) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(CotStreamListActivity.this, toast,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected Dialog onCreateDialog(final int id/* , final Bundle bundle */) {
        Dialog dialog = null;
        final Bundle bundle = _showDialogData;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch (id) {
            case _REMOVE_ITEM_DIALOG:
                builder.setMessage(bundle.getString("message"));

                builder.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                String connectString = bundle
                                        .getString(CotPort.CONNECT_STRING_KEY);
                                _remote.removeStream(connectString);
                                _removeItem(new CotPort(bundle));
                                removeDialog(id);
                            }
                        });
                builder.setNegativeButton(R.string.no, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                removeDialog(id);
                            }
                        });
                dialog = builder.create();
                break;
            case _REMOVE_ALL_DIALOG:
                builder.setMessage(R.string.preferences_text450);
                builder.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface arg0,
                                    int arg1) {
                                removeAllCotPorts();
                            }
                        });

                builder.setNegativeButton(R.string.no, // implemented
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                removeDialog(id);
                            }
                        });
                dialog = builder.create();
                break;
        }
        return dialog;
    }

    /**
     * Add an item to the list of outputs, or updates its enabled state if it is already present
     * 
     * @param port Output to add or update
     * @return true if the output was added, false if not added (because already present)
     */
    @Override
    protected boolean _putItem(CotPort port) {
        return super._putItem(port);
    }

    @Override
    protected void enabledFlagSet(CotPort port) {
        //invoked when user select/unselects the "enabled" checkbox
        _remote.addStream(port.getConnectString(), port.getData());
    }

    /**
     * Remove an output from this Activity's list of displayed outputs and tell the remote service
     * to stop using it.
     * 
     * @param port output to remove
     * @return true if the output was removed, false if no match was found
     */
    @Override
    protected boolean _removeItem(CotPort port) {
        Log.d(TAG, "Removing " + port.getConnectString());
        boolean removed = super._removeItem(port);
        _remote.removeStream(port.getConnectString());
        return removed;
    }

    @Override
    String getPortType() {
        return "streams";
    }

    @Override
    protected boolean displayConnectionStatus() {
        return true;
    }
}
