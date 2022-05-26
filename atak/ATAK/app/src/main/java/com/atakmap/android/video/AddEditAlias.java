
package com.atakmap.android.video;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.annotations.FortifyFinding;
import com.atakmap.comms.NetworkManagerLite;
import com.atakmap.comms.NetworkManagerLite.NetworkDevice;
import com.atakmap.android.video.ConnectionEntry.Protocol;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.apache.commons.lang.StringUtils;

import java.net.NetworkInterface;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddEditAlias {

    public static final String TAG = "AddEditAlias";

    //private final static String NON_MCAST_IP_PATTERN =
    //        "^([01]?\\d\\d?|2[0-1]\\d|22[0-3])\\." +
    //                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
    //                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
    //                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])[:/]";

    public final static String MCAST_IP_PATTERN = "^(22[4-9]|2[3-4]\\d|25[4-5])\\."
            +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])[:/]";

    public interface AliasModifiedListener {
        void aliasModified(ConnectionEntry ce);
    }

    private AliasModifiedListener aliasModifiedListener = null;

    private boolean _isUpdate = false;
    private String prevAlias = null;

    private TextView alias = null;
    private TextView url = null;
    private TextView port = null;
    private TextView portSep = null;
    private TextView file = null;
    private TextView fileSep = null;
    private TextView passphraseLabel = null;
    private TextView passphrase = null;

    private CheckBox rtspReliable = null;

    private CheckBox ignoreKLVCB = null;

    private Spinner proto = null;
    private List<String> connectionProtos = null;
    private TextView bufferTime = null;
    private TextView networkTimeout = null;
    private CheckBox bufferEnabled = null;
    private TextView bufferLabel = null;

    private LinearLayout preferredNetwork = null;
    private Spinner preferredNetworkSpinner = null;
    private List<NetworkDeviceStringWrapper> networkList = null;

    private LinearLayout videoUsernameLayout;
    private TextView videoUsername;
    private TextView videoPassword;
    private CheckBox videoPasswordShowCheckbox;

    // initial values if editing an alias for easy comparison to see if anything has changed
    private int initialProto = 0;
    private String initialAddress = "";
    private String initialPort = "";
    private boolean initialIgnoreKLV = false;
    private String initialPath = "";
    private String initialTimeout = "";

    private String initialUsername = "";
    @FortifyFinding(finding = "Password Management: Hardcoded Password", rational = "This is a empty assignment just for the purposes of making the code simpler instead of extra null pointer checks.    This is not hardcoded.")
    private String initialPassword = "";

    @FortifyFinding(finding = "Password Management: Hardcoded Password", rational = "This is a empty assignment just for the purposes of making the code simpler instead of extra null pointer checks.    This is not hardcoded.")
    private String initialPassphrase = "";
    private boolean initialBuffered = false;
    private String initialBufferTime = "";
    private String preferredAddress = "";

    private final Context context;

    public static final int MAX_PORT_VAL = 65535;
    private static final int MAX_NETWORK_TIMEOUT = 60000; // in ms
    private static final int MAX_BUFFER = 300000;
    private static final int DEFAULT_TIMEOUT = 12000; // in ms

    public AddEditAlias(final Context c) {
        this.context = c;
    }

    public void addEditConnection(final ConnectionEntry currentCE,
            AliasModifiedListener aml) {
        aliasModifiedListener = aml;
        addEditConnection(currentCE);
    }

    public void addEditConnection(final ConnectionEntry currentCE) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.alias, null);

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setView(view);
        b.setCancelable(false);
        b.setPositiveButton(currentCE != null ? R.string.update
                : R.string.add, null);
        b.setNegativeButton(R.string.cancel, null);

        final AlertDialog d = b.show();

        Button btn = d.getButton(AlertDialog.BUTTON_POSITIVE);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Action for 'OK' Button
                if (!validateInput())
                    return;

                Protocol p = Protocol.fromString(proto.getSelectedItem()
                        .toString());

                String aliasKey = alias.getText().toString();
                String aliasVal = url.getText().toString();
                if (p == Protocol.RTSP ||
                        p == Protocol.RTMP ||
                        p == Protocol.RTMPS ||
                        p == Protocol.HTTP ||
                        p == Protocol.HTTPS) {
                    String userName = videoUsername.getText().toString();
                    String password = videoPassword.getText().toString();
                    if (!StringUtils.isBlank(userName)
                            && !StringUtils.isBlank(password)) {
                        aliasVal = userName + ":" + password + "@" + aliasVal;
                    }
                }
                String host = aliasVal.contains("/") ? aliasVal
                        .substring(0,
                                aliasVal.indexOf("/"))
                        : aliasVal;
                String path = "";
                if (aliasVal.contains("/"))
                    path = aliasVal.replace(host, "");
                else if (file.isShown()) {
                    path = "/" + file.getText().toString();
                }

                // special case for RAW, ignore the work done above
                if (p == Protocol.RAW) {
                    host = aliasVal;
                    path = "";
                }

                Log.v(TAG, "Path: " + path);

                String portVal = "";

                Log.d(TAG, "processing alias: " + aliasVal + " " + p);
                if (p == Protocol.RAW) {
                    Log.d(TAG,
                            "ignoring format of the raw video alias: "
                                    + aliasVal);
                } else if (p == Protocol.UDP ||
                        p == Protocol.RTSP ||
                        p == Protocol.TCP ||
                        p == Protocol.RTP ||
                        p == Protocol.SRT)
                    portVal = port.getText().toString();
                else if (p == Protocol.HTTP ||
                        p == Protocol.RTMP ||
                        p == Protocol.RTMPS ||
                        p == Protocol.HTTPS) {

                    try {
                        String temp = "http://" + host;

                        URI u = URI.create(temp);
                        if (u.getPort() > 0)
                            portVal = "" + u.getPort();
                        host = u.getHost();
                        if (host == null)
                            throw new Exception("badhost");

                        if (!FileSystemUtils.isEmpty(u.getUserInfo()))
                            host = u.getUserInfo() + "@" + host;

                    } catch (Exception e) {
                        url.requestFocus();
                        Toast.makeText(context,
                                R.string.video_text38_host,
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                } else {
                    Log.d(TAG,
                            "ignoring format of the raw video alias, but it was not flagged as RAW: "
                                    + aliasVal);
                    Toast.makeText(
                            context,
                            "Problem has been detected with the video alias, treating the entry as: "
                                    + host,
                            Toast.LENGTH_SHORT).show();
                    p = Protocol.RAW;
                }

                ConnectionEntry ce = new ConnectionEntry();
                if (currentCE != null)
                    ce.copy(currentCE);
                ce.setAlias(aliasKey);
                ce.setAddress(host);
                ce.setPath(path);
                ce.setProtocol(p);
                ce.setRtspReliable(rtspReliable.isChecked() ? 1 : 0);

                if (p == Protocol.SRT)
                    ce.setPassphrase(passphrase.getText().toString());
                ce.setIgnoreEmbeddedKLV(ignoreKLVCB.isChecked());
                if (!FileSystemUtils.isEmpty(preferredAddress))
                    ce.setPreferredInterfaceAddress(preferredAddress);
                ce.setBufferTime(-1);

                if (bufferEnabled.isChecked()) {
                    try {
                        ce.setBufferTime(1000 * Integer.parseInt(bufferTime
                                .getText().toString()));
                    } catch (Exception ignore) {
                    }
                }

                try {
                    ce.setNetworkTimeout(1000 * Integer.parseInt(
                            networkTimeout.getText().toString().trim()));
                } catch (Exception ignore) {
                }

                if (portVal != null && !portVal.trim().equals("")) {
                    try {
                        ce.setPort(Integer.parseInt(portVal.trim()));
                    } catch (NumberFormatException nfe) {
                        showPortErrorDialog();
                        return;
                    }
                } else if (p.getDefaultPort() != -1) {
                    // default ports otherwise
                    ce.setPort(p.getDefaultPort());
                }

                VideoManager.getInstance().addEntry(ce);

                d.dismiss();

                if (aliasModifiedListener != null)
                    aliasModifiedListener.aliasModified(ce);
            }
        });

        btn = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Action for 'NO' Button
                AlertDialog.Builder b = new AlertDialog.Builder(context);

                if (_isUpdate) {
                    // test if anything changed. Only show dialog if info was changed.
                    if (!entryChanged(currentCE)) {
                        d.dismiss();
                        return;
                    }
                    b.setTitle(R.string.video_text26);
                    b.setMessage(context
                            .getString(R.string.video_text27)
                            + alias.getText()
                            + context.getString(R.string.video_text28));
                } else {
                    boolean modified = false;
                    if (!alias.getText().toString().contentEquals(""))
                        modified = true;

                    if (proto.getSelectedItemPosition() != initialProto)
                        modified = true;

                    if (!(networkTimeout
                            .getText()
                            .toString() + "000")
                                    .contentEquals(
                                            String.valueOf(
                                                    DEFAULT_TIMEOUT)))
                        modified = true;

                    if (bufferEnabled.isChecked())
                        modified = true;

                    if (!url.getText().toString().contentEquals(""))
                        modified = true;

                    if (modified) {
                        b.setTitle(R.string.video_text29);
                        b.setMessage(R.string.video_text30);
                    } else {
                        d.dismiss();
                        return;
                    }
                }
                b.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                d.dismiss();
                            }
                        });
                b.setNegativeButton(R.string.no, null);
                b.show();
            }
        });

        alias = view.findViewById(R.id.alias_text);
        alias.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        url = view.findViewById(R.id.alias_url_text);
        url.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        proto = view.findViewById(R.id.alias_protocol);
        connectionProtos = Arrays.asList(context.getResources()
                .getStringArray(R.array.connection_protos));
        proto.setOnItemSelectedListener(new SimpleItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int pos, long id) {
                String selected = parent.getItemAtPosition(pos).toString();
                setInputUI(selected);
                if (view instanceof TextView)
                    ((TextView) view).setTextColor(Color.WHITE);
            }

        });

        ignoreKLVCB = view.findViewById(R.id.disableklv);

        networkTimeout = view.findViewById(R.id.network_timeout);
        networkTimeout.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        networkTimeout.setText(String.valueOf((DEFAULT_TIMEOUT / 1000)));

        bufferEnabled = view.findViewById(R.id.buffering_enabled);

        bufferLabel = view.findViewById(R.id.buffer_label);
        bufferTime = view.findViewById(R.id.buffer_time);
        bufferTime.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        bufferEnabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                bufferLabel.setEnabled(isChecked);
                bufferTime.setEnabled(isChecked);
                bufferTime.setFocusable(isChecked);
                bufferTime.setFocusableInTouchMode(isChecked);
                buttonView.invalidate();
            }
        });

        passphraseLabel = view.findViewById(R.id.video_passphrase_label);
        passphrase = view.findViewById(R.id.video_passphrase);
        rtspReliable = view.findViewById(R.id.rtsp_reliable);

        preferredNetwork = view
                .findViewById(R.id.preferred_network);
        preferredNetworkSpinner = view
                .findViewById(R.id.preferred_network_spinner);
        preferredNetworkSpinner
                .setOnItemSelectedListener(new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view,
                            int pos, long id) {
                        NetworkDevice nd = ((NetworkDeviceStringWrapper) parent
                                .getItemAtPosition(pos)).nd;
                        if (nd == null)
                            preferredAddress = "";
                        else
                            preferredAddress = NetworkManagerLite
                                    .getAddress(nd.getInterface());

                        if (view instanceof TextView)
                            ((TextView) view).setTextColor(Color.WHITE);
                        Log.d(TAG, "address set to: " + preferredAddress);
                    }

                });

        port = view.findViewById(R.id.alias_url_port);
        port.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        portSep = view.findViewById(R.id.alias_port_separator);
        port.setNextFocusDownId(R.id.alias_url_file);

        file = view.findViewById(R.id.alias_url_file);
        fileSep = view.findViewById(R.id.alias_file_separator);
        file.setNextFocusDownId(R.id.alias_text);

        videoUsernameLayout = view.findViewById(R.id.video_username_layout);
        videoUsername = view.findViewById(R.id.video_username);
        videoPassword = view.findViewById(R.id.video_password);
        videoPasswordShowCheckbox = view
                .findViewById(R.id.video_password_checkbox);

        videoPasswordShowCheckbox.setSelected(false);
        videoPassword.setTransformationMethod(
                PasswordTransformationMethod.getInstance());
        videoPasswordShowCheckbox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton,
                            boolean isChecked) {
                        if (videoPassword
                                .getTransformationMethod() == PasswordTransformationMethod
                                        .getInstance()) {
                            videoPassword.setTransformationMethod(
                                    HideReturnsTransformationMethod
                                            .getInstance());
                        } else {
                            videoPassword.setTransformationMethod(
                                    PasswordTransformationMethod.getInstance());
                        }
                    }
                });

        List<NetworkDevice> list = NetworkManagerLite.getNetworkDevices();

        // need to prepare to populate it with some defaults wlan0, eth0, tun0, tun1
        if (list.size() == 0) {
            list = new ArrayList<>();
            final String[] ifaces = new String[] {
                    "wlan0", "eth0", "usb0", "rmnet0", "rndis0", "nwradio0",
                    "tun0", "tun1", "csctun0", "ppp0"
            };
            for (String iface : ifaces) {
                try {
                    NetworkInterface ni = NetworkInterface.getByName(iface);
                    if (ni != null && ni.isUp()) {
                        NetworkDevice nd = new NetworkDevice(iface);
                        Log.d(TAG, "discovered: " + nd);
                        list.add(nd);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "warning - interface not handled correctly: "
                            + iface + ": " + e);
                }
            }

        }

        networkList = new ArrayList<>();

        // default == null
        networkList.add(new NetworkDeviceStringWrapper(null));

        for (int i = 0; i < list.size(); ++i) {
            networkList.add(new NetworkDeviceStringWrapper(list.get(i)));
        }

        // Set up preferred network spinner
        ArrayAdapter<NetworkDeviceStringWrapper> adapter = new ArrayAdapter<>(
                context,
                R.layout.spinner_text_view,
                networkList);

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        try {
            preferredNetworkSpinner.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
            // TODO: handle exception
        }
        if (networkList != null && currentCE != null) {
            for (int i = 0; i < networkList.size(); ++i) {
                NetworkDeviceStringWrapper ndsw = networkList.get(i);
                if (ndsw.nd != null) {
                    String address = NetworkManagerLite
                            .getAddress(ndsw.nd.getInterface());
                    if (address != null && address
                            .equals(currentCE.getPreferredInterfaceAddress())) {
                        preferredNetworkSpinner.setSelection(i);
                    }
                }
            }
        }

        if (currentCE != null) {
            prevAlias = currentCE.getAlias();
            alias.setText(prevAlias);
            String urlString = currentCE.getAddress();
            if (currentCE.getProtocol().equals(Protocol.RAW)) {
                Log.d(TAG, "do not modify a raw URL");
            } else if (!currentCE.getProtocol().equals(Protocol.RTSP) &&
                    !currentCE.getProtocol().equals(Protocol.UDP) &&
                    !currentCE.getProtocol().equals(Protocol.TCP) &&
                    !currentCE.getProtocol().equals(Protocol.RTP) &&
                    !currentCE.getProtocol().equals(Protocol.SRT)) {
                if (currentCE.getPort() != -1) {
                    urlString = urlString + ":" + currentCE.getPort();
                }
                if (currentCE.getPath() != null
                        && !currentCE.getPath().trim().equals(""))
                    urlString += currentCE.getPath();
            } else {
                String path = currentCE.getPath();
                if (path.length() > 1) {
                    int startIndex = 1;
                    if (!path.startsWith("/"))
                        startIndex = 0;

                    file.setText(path.substring(startIndex));
                    initialPath = path.substring(startIndex);
                }
            }
            url.setText(urlString);

            if ((currentCE.getProtocol().equals(Protocol.RTSP) ||
                    currentCE.getProtocol().equals(Protocol.HTTP) ||
                    currentCE.getProtocol().equals(Protocol.HTTPS) ||
                    currentCE.getProtocol().equals(Protocol.RTMP) ||
                    currentCE.getProtocol().equals(Protocol.RTMPS))
                    && urlString != null && urlString.contains("@")) {
                String[] userPassIp = ConnectionEntry.getUserPassIp(urlString);
                if (!StringUtils.isBlank(userPassIp[0])
                        && !StringUtils.isBlank(userPassIp[1])) {
                    initialUsername = userPassIp[0];
                    initialPassword = userPassIp[1];
                    videoUsername.setText(initialUsername);
                    videoPassword.setText(initialPassword);
                    if (!FileSystemUtils.isEmpty(initialPassword)) {
                        videoPasswordShowCheckbox.setEnabled(false);
                        videoPassword.addTextChangedListener(
                                new AfterTextChangedWatcher() {
                                    @Override
                                    public void afterTextChanged(Editable s) {
                                        if (s != null && s.length() == 0) {
                                            videoPasswordShowCheckbox
                                                    .setEnabled(true);
                                            videoPassword
                                                    .removeTextChangedListener(
                                                            this);
                                        }
                                    }
                                });
                    } else {
                        videoPasswordShowCheckbox.setEnabled(true);
                    }
                }
                initialAddress = userPassIp[2];
                url.setText(userPassIp[2]);
            } else {
                initialAddress = urlString;
            }

            String selectedProtocol = currentCE.getProtocol().toString();

            proto.setSelection(connectionProtos.indexOf(selectedProtocol));
            initialProto = connectionProtos.indexOf(selectedProtocol);
            // http and rtsp could have a default port (e.g., 80?)
            if (currentCE.getProtocol().equals(Protocol.UDP)
                    || currentCE.getPort() != -1) {
                port.setText(String.valueOf(currentCE.getPort()));
                initialPort = String.valueOf(currentCE.getPort());
            }

            initialIgnoreKLV = currentCE.getIgnoreEmbeddedKLV();
            if (currentCE.getIgnoreEmbeddedKLV())
                ignoreKLVCB.setChecked(true);

            boolean isChecked = currentCE.getBufferTime() != -1
                    && currentCE.getBufferTime() != 0;

            bufferEnabled.setChecked(isChecked);
            initialBuffered = isChecked;

            bufferTime.setEnabled(isChecked);
            bufferTime.setFocusable(isChecked);
            bufferTime.setFocusableInTouchMode(isChecked);
            bufferLabel.setEnabled(isChecked);

            if (bufferEnabled.isChecked()) {
                bufferTime.setText(
                        String.valueOf(currentCE.getBufferTime() / 1000));
                initialBufferTime = String
                        .valueOf(currentCE.getBufferTime() / 1000);
            }

            passphrase.setText(currentCE.getPassphrase());
            rtspReliable.setChecked((currentCE.getRtspReliable() == 1));

            networkTimeout
                    .setText(String
                            .valueOf(currentCE.getNetworkTimeout() / 1000));
            initialTimeout = String
                    .valueOf(currentCE.getNetworkTimeout() / 1000);
            initialPassphrase = currentCE.getPassphrase();

            _isUpdate = true;
        }

    }

    private void setLoginVisible(int visible) {
        videoUsernameLayout.setVisibility(visible);
        if (visible == View.GONE || visible == View.INVISIBLE) {
            videoUsername.setText("");
            videoPassword.setText("");
        }
    }

    /**
     * Sets the UI to match the selected spinner protocol.
     */
    private void setInputUI(String s) {

        final Protocol p = Protocol.fromString(s);
        if (p == Protocol.UDP) {
            preferredNetwork.setVisibility(View.VISIBLE);
        } else {
            preferredNetwork.setVisibility(View.GONE);
        }
        rtspReliable.setVisibility(View.GONE);

        if (p == Protocol.RTSP ||
                p == Protocol.RTMP ||
                p == Protocol.RTMPS ||
                p == Protocol.HTTP ||
                p == Protocol.HTTPS) {
            setLoginVisible(View.VISIBLE);
        } else {
            setLoginVisible(View.GONE);
        }

        if (p == Protocol.RAW ||
                p == Protocol.HTTP ||
                p == Protocol.RTMP ||
                p == Protocol.RTMPS ||
                p == Protocol.HTTPS) {

            // hide the port field
            url.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, url.getLayoutParams().height));
            portSep.setVisibility(View.GONE);
            port.setVisibility(View.GONE);
            port.setFocusable(false);
            fileSep.setVisibility(View.GONE);
            file.setVisibility(View.GONE);
            file.setFocusable(false);

        } else if (p == Protocol.UDP ||
                p == Protocol.TCP ||
                p == Protocol.RTP) {
            // show the port field
            url.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, url.getLayoutParams().height));
            portSep.setVisibility(View.VISIBLE);
            port.setVisibility(View.VISIBLE);
            port.setFocusable(true);
            port.setFocusableInTouchMode(true);
            fileSep.setVisibility(View.GONE);
            file.setVisibility(View.GONE);
            file.setFocusable(false);

        } else if (p == Protocol.RTSP) {
            // show the port field
            url.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, url.getLayoutParams().height));
            portSep.setVisibility(View.VISIBLE);
            port.setVisibility(View.VISIBLE);
            port.setFocusable(true);
            port.setFocusableInTouchMode(true);
            fileSep.setVisibility(View.VISIBLE);
            file.setVisibility(View.VISIBLE);
            file.setFocusable(true);
            file.setFocusableInTouchMode(true);
            rtspReliable.setVisibility(View.VISIBLE);

        } else if (p == Protocol.SRT) {
            // show the port field
            url.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, url.getLayoutParams().height));
            portSep.setVisibility(View.VISIBLE);
            port.setVisibility(View.VISIBLE);
            port.setFocusable(true);
            port.setFocusableInTouchMode(true);
            fileSep.setVisibility(View.VISIBLE);
            fileSep.setVisibility(View.GONE);
            file.setVisibility(View.GONE);
            file.setFocusable(false);
        }
        setPassphraseVisibility(p == Protocol.SRT);
    }

    private void setPassphraseVisibility(boolean visible) {
        passphraseLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
        passphrase.setVisibility(visible ? View.VISIBLE : View.GONE);
        passphrase.setFocusable(visible);
        passphrase.setEnabled(visible);
        passphrase.setFocusableInTouchMode(visible);
    }

    /**
     * test if the connection entry has been modified
     *
     * @return - true if the entry was changed
     */
    private boolean entryChanged(ConnectionEntry ce) {
        String name = alias.getText().toString();
        if (!name.equals(prevAlias))
            return true;
        Log.v(TAG, "name is the same");

        int protocol = proto.getSelectedItemPosition();
        if (protocol != initialProto)
            return true;
        Log.v(TAG, "protocol is the same");

        if (!(networkTimeout.getText().toString())
                .equals(initialTimeout)) {
            return true;
        }

        Log.v(TAG, "networkTimeout is the same");

        if (initialBuffered)
            if (!bufferEnabled.isChecked())
                return true;

        if (!initialBuffered)
            if (bufferEnabled.isChecked())
                return true;

        Log.v(TAG, "hasBuffer is the same");

        if (bufferEnabled.isChecked()) {
            if (!(bufferTime.getText().toString())
                    .equals(initialBufferTime))
                return true;

            Log.v(TAG, "Buffer Time is the same");
        }

        if (initialAddress == null)
            initialAddress = "";

        if (!url.getText().toString().equals(initialAddress))
            return true;

        if (initialIgnoreKLV != ignoreKLVCB.isChecked())
            return true;

        if (ce.getProtocol() == Protocol.RTSP) {
            if (!port.getText().toString().equals(initialPort))
                return true;
            if (!file.getText().toString().equals(initialPath))
                return true;
        }

        if (ce.getProtocol() == Protocol.RTSP ||
                ce.getProtocol() == Protocol.RTMP ||
                ce.getProtocol() == Protocol.RTMPS ||
                ce.getProtocol() == Protocol.HTTP ||
                ce.getProtocol() == Protocol.HTTPS) {
            if (!videoUsername.getText().toString().equals(initialUsername))
                return true;
            if (!videoPassword.getText().toString().equals(initialPassword))
                return true;
        }

        if (ce.getProtocol() == Protocol.SRT) {
            if (!passphrase.getText().toString()
                    .equals(initialPassphrase))
                return true;
        }

        if (ce.getProtocol() == Protocol.UDP ||
                ce.getProtocol() == Protocol.TCP ||
                ce.getProtocol() == Protocol.RTP ||
                ce.getProtocol() == Protocol.SRT)

            if (!port.getText().toString().equals(initialPort))
                return true;

        Log.v(TAG, "url is the same");

        return false;
    }

    private boolean ensurePort(final String fieldName,
            final TextView ui) {

        if (ui.getText() != null && ui.getText().length() > 0) {
            try {
                int port = Integer.parseInt(ui.getText().toString());
                if (port > 0 && (port <= MAX_PORT_VAL))
                    return true;
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "not a valid integer: " + ui.getText(), nfe);
            }
        }
        //Do not display an error if RTSP, Just use Default Port 554
        if ("RTSP".equals(fieldName)
                && FileSystemUtils.isEmpty((String) ui.getText())) {
            return false;
        }
        ui.requestFocus();
        Toast.makeText(context, R.string.video_text33, Toast.LENGTH_LONG)
                .show();
        return false;
    }

    /**
     * test the fields for valid input.
     *
     * @return - true if all fields are valid
     */

    private boolean validateInput() {

        final Protocol protocol = Protocol.fromString(proto.getSelectedItem()
                .toString());

        // For now on the network, assume no ability to look up
        // addresses, so everything
        // entered will need to be dot notation.

        // IP can only have numbers and will need to be

        String ip = url.getText().toString().trim();

        if (protocol == Protocol.RAW) {
            return checkAlias();
        } else if (protocol == Protocol.UDP || protocol == Protocol.RTP) {
            if (ip == null || ip.length() == 0 || ip.equals("127.0.0.1")) {
                // valid unicast cases
            } else {
                Pattern pattern = Pattern.compile(MCAST_IP_PATTERN);
                // tack on an additional : just in case it is just
                // the IP and nothing else.
                Matcher matcher = pattern.matcher(ip + ":");
                if (!matcher.find()) {
                    url.requestFocus();
                    Toast.makeText(context, R.string.video_text36,
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }

        } else if (protocol == Protocol.SRT) {
            if (ip == null || ip.length() == 0) {
                url.requestFocus();
                Toast.makeText(context, R.string.video_text38_host,
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }

        if (protocol == Protocol.UDP ||
                protocol == Protocol.RTSP ||
                protocol == Protocol.TCP ||
                protocol == Protocol.RTP ||
                protocol == Protocol.SRT) {

            if (!ensurePort(protocol.toString(), port)) {
                if (port.getText().toString().isEmpty()
                        && protocol == ConnectionEntry.Protocol.RTSP) {
                    port.setText("554");
                    return true;
                }
                return false;
            }
        }

        try {
            int pv = Integer.MAX_VALUE;

            if (!port.getText().toString().equals(""))
                pv = Integer.parseInt(port.getText().toString());

            if (!(protocol == Protocol.HTTP || protocol == Protocol.HTTPS
                    || protocol == Protocol.RTMP || protocol == Protocol.RTMPS)
                    && pv > MAX_PORT_VAL)
                return showPortErrorDialog();

        } catch (NumberFormatException nfe) {
            return showPortErrorDialog();
        }

        try {
            int nt = 1000
                    * Integer.parseInt(networkTimeout.getText().toString());
            if (nt > MAX_NETWORK_TIMEOUT)
                return showTimeoutErrorDialog(true);
        } catch (NumberFormatException ne) {
            return showTimeoutErrorDialog(false);
        }

        if (bufferEnabled.isChecked()) {
            try {
                int buf = 1000
                        * Integer.parseInt(bufferTime.getText().toString());
                if (buf > MAX_BUFFER)
                    return showBufferErrorDialog();
            } catch (NumberFormatException nfe) {
                return showBufferErrorDialog();
            }
        }

        if (protocol == Protocol.RTSP ||
                protocol == Protocol.RTMP ||
                protocol == Protocol.RTMPS ||
                protocol == Protocol.HTTP ||
                protocol == Protocol.HTTPS) {

            boolean check = checkUserPass(
                    context.getString(R.string.username_colon), videoUsername);
            if (!check)
                return false;
            check = checkUserPass(context.getString(R.string.password_colon),
                    videoPassword);
            if (!check)
                return false;
        }
        return checkAlias();
    }

    private boolean checkUserPass(String field_name, TextView textView) {
        String value = textView.getText().toString();
        String invalid_input = context.getString(R.string.invalid_input);
        String invalid_characters = "/@:<>[]%#\"?/{}";
        for (int i = 0; i < invalid_characters.length(); ++i) {
            String c = invalid_characters.charAt(i) + "";
            if (value.contains(c)) {
                textView.requestFocus();
                Toast.makeText(context,
                        field_name + " " + invalid_input + " '" + c + "'",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }

    private boolean checkAlias() {
        if (alias.getText().toString().equals("")) {
            alias.requestFocus();
            Toast.makeText(context, R.string.video_text35,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private boolean showPortErrorDialog() {
        port.requestFocus();
        Toast.makeText(context, R.string.video_text38, Toast.LENGTH_LONG)
                .show();
        return false;
    }

    private boolean showBufferErrorDialog() {
        bufferTime.requestFocus();
        Toast.makeText(context, R.string.video_text39, Toast.LENGTH_LONG)
                .show();
        return false;
    }

    private boolean showTimeoutErrorDialog(boolean valid) {
        networkTimeout.requestFocus();
        Toast.makeText(context, valid ? R.string.video_text40
                : R.string.video_text41, Toast.LENGTH_LONG).show();
        return false;
    }

    static class NetworkDeviceStringWrapper {
        final NetworkDevice nd;

        NetworkDeviceStringWrapper(final NetworkDevice nd) {
            this.nd = nd;
        }

        public String toString() {
            if (nd != null) {
                return nd.getLabel();
            } else {
                return "default";
            }
        }

    }
}
