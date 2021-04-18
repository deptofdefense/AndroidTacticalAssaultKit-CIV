
package com.atakmap.comms.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.atakmap.comms.CotServiceRemote;

import com.atakmap.app.R;

public class CotInputsListActivity extends CotPortListActivity {

    public static final String TAG = "CotInputsListActivity";

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // connect to the service
        _remote = new CotServiceRemote();
        _remote.setInputsChangedListener(_inputsChangedListener);
        _remote.connect(_connectionListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // boolean result = true;
        if (super.onOptionsItemSelected(item))
            return true;

        if (item.getItemId() == R.id.add_menu_add) {
            Intent addNetInputIntent = new Intent(this,
                    AddNetInfoActivity.class);
            addNetInputIntent.putExtra("type", "input");
            startActivityForResult(addNetInputIntent, _REQUEST_ADD_PORT);
        } else if (item.getItemId() == R.id.add_menu_remove_all) {
            showDialog(_REMOVE_ALL_DIALOG);
        }
        return true;
    }

    @Override
    protected void addToRemote(String connectString, Bundle inputData) {
        _remote.addInput(connectString, inputData);
    }

    @Override
    protected void removeFromRemote(String connectString) {
        _remote.removeInput(connectString);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        switch (requestCode) {
            case _REQUEST_ADD_PORT:
                if (resultCode == Activity.RESULT_OK) {
                    Bundle inputData = data.getBundleExtra("data");
                    String connectString = data
                            .getStringExtra(CotPort.CONNECT_STRING_KEY);
                    try {
                        _remote.addInput(connectString, inputData);
                    } catch (IllegalArgumentException iae) {
                        Toast.makeText(
                                this,
                                R.string.invalid_cot_address, Toast.LENGTH_LONG)
                                .show();
                    }
                }
                break;
            default:
                try {
                    super.onActivityResult(requestCode, resultCode, data);
                } catch (IllegalArgumentException iae) {
                    Toast.makeText(
                            this,
                            R.string.invalid_cot_address, Toast.LENGTH_LONG)
                            .show();
                }
                break;
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id/* , final Bundle bundle */) {
        Dialog dialog = null;
        final Bundle bundle = _showDialogData;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch (id) {
            case _REMOVE_ITEM_DIALOG:
                builder.setMessage(bundle.getString("message"));

                builder.setPositiveButton(R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String connectString = bundle
                                .getString(CotPort.CONNECT_STRING_KEY);
                        _remote.removeInput(connectString);
                        _removeItem(new CotPort(bundle));
                        removeDialog(id);
                    }
                });
                builder.setNegativeButton(R.string.no, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(id);
                    }
                });
                dialog = builder.create();
                break;
            case _REMOVE_ALL_DIALOG:
                builder.setMessage(R.string.are_you_sure_remove_inputs);
                builder.setPositiveButton(R.string.yes, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        removeAllCotPorts();
                    }
                });

                builder.setNegativeButton(R.string.no, null);
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
        _remote.addInput(port.getConnectString(), port.getData());
    }

    /**
     * Remove an output from this Activity's list of displayed outputs and tell the remote service
     * to stop using it.
     * 
     * @param port output to remove
     * @return true if the output was removed, false if no match was found
     */
    @Override
    protected boolean _removeItem(final CotPort port) {
        // Log.v(TAG, "Removing " + output.getDescription());
        boolean removed = super._removeItem(port);
        _remote.removeInput(port.getConnectString());
        return removed;
    }

    /**
     * Returns the type of the Activity - in this case "inputs".
     * @return the String "inputs".
     */
    @Override
    String getPortType() {
        return "inputs";
    }
}
