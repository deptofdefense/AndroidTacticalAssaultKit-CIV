package com.atakmap.commoncommotest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.atakmap.commoncommo.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class ContactListActivity extends Activity implements CommoLogger, ContactPresenceListener, InterfaceStatusListener, CoTMessageListener, MissionPackageIO {

    private Timer timer;
    private ListView contactList;
    private TextView statusLabel;
    private ToggleButton serverEnabledButton;
    private ArrayAdapter<String> contactsAdapter;

    private static final String uid = "android-commo-test";
    private static final String callsign = "androidcommo";
    
    private Commo commo;
    private StreamingNetInterface streamNetIface = null;

    public synchronized void log(Level level, String s) {
        int priority = Log.INFO;
        switch (level) {
            case DEBUG:
                priority = Log.DEBUG;
                break;
            case ERROR:
                priority = Log.ERROR;
                break;
            case INFO:
                priority = Log.INFO;
                break;
            case VERBOSE:
                priority = Log.VERBOSE;
                break;
            case WARNING:
                priority = Log.WARN;
                break;
        }
        Log.println(priority, "COMMO", s);
    }

    public void cotMessageReceived(String s) {
        Log.d("CoTRecv", s);
    }

    public void contactAdded(Contact contact) {
        final String s = contact.contactUID;
        runOnUiThread(new Runnable() {
            public void run() {
                contactsAdapter.add(s);

            }
        });
    }

    public void contactRemoved(Contact contact) {
        final String s = contact.contactUID;
        runOnUiThread(new Runnable() {
            public void run() {
                contactsAdapter.remove(s);

            }
        });
    }

    public void interfaceUp(NetInterface netInterface) {
        Log.i("COMMOAPP", "Interface up " + netInterface);
        if (netInterface == streamNetIface) {
            statusLabel.post(new Runnable() {
                public void run() {
                    statusLabel.setText("Server connected!");
                }
            });
        }
    }

    public void interfaceDown(NetInterface netInterface) {
        Log.i("COMMOAPP", "Interface down " + netInterface);
        if (netInterface == streamNetIface) {
            statusLabel.post(new Runnable() {
                public void run() {
                    statusLabel.setText("Server not connected!");
                }
            });
        }
    }

    private static final String MPIO_LOC = "/sdcard/commomptest";
    private int mprxCount = 0;
    public File missionPackageReceiveInit(String fileName, String transferName, String shaHash, String sender) throws MissionPackageTransferException {
        mprxCount++;
        File dir = new File(MPIO_LOC);
        dir.mkdirs();
        File f = new File(dir, "mprx-" + mprxCount + "-" + fileName);
        Log.i("COMMOAPP", "MPRX Init -> " + f + " from " + sender);
        return f;
    }

    public void missionPackageReceiveComplete(File file, MissionPackageTransferStatus missionPackageTransferStatus, String error) {
        Log.i("COMMOAPP", "MPRX Complete -> " + file + " status " + missionPackageTransferStatus + " err = " + error);
    }

    public void missionPackageSendStatusUpdate(MissionPackageTransferStatusUpdate up) {
        Log.i("COMMOAPP", "MPTX Update -> " + up.transferId + " status " + up.status + " reason = " + up.reason + " receiver = " + up.recipient.contactUID);

    }

    public CoTPointData getCurrentPoint() {
        return new CoTPointData(36.1, -77.38);
    }

    public String createUUID() {
        return UUID.randomUUID().toString();
    }

    private byte[] readFile(String file) {
        FileInputStream fis = null;
        try {
            File f = new File(file);
            fis = new FileInputStream(f);
            byte[] ret = new byte[(int)f.length()];
            fis.read(ret);
            fis.close();
            return ret;
        } catch (IOException e) {
            Log.e("COMMOAPP", "Unable to read cert file " + file + "   " + e.getMessage(), e);
            return new byte[0];
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);
        
        contactList = (ListView)findViewById(R.id.contactListView);
        statusLabel = (TextView)findViewById(R.id.serverStatusLabel);
        serverEnabledButton = (ToggleButton)findViewById(R.id.connectButton);
        Button btn = (Button)findViewById(R.id.sendFileButton);
        
        btn.setOnClickListener(new View.OnClickListener() {
            
            @Override
            public void onClick(View v) {
                File dir = new File(MPIO_LOC);
                dir.mkdirs();
                File f = new File(dir, "mptx.zip");
                
                try {
                    commo.sendMissionPackage(commo.getContacts()[0], f, f.getName(), f.getName());
                } catch (CommoException e) {
                    Log.e("COMMOAPP", "Failed to send MP", e);
                }
            }
        });
        
        streamNetIface = null;

        serverEnabledButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    CoTMessageType[] allTypes = new CoTMessageType[] {
                            CoTMessageType.CHAT,
                            CoTMessageType.SITUATIONAL_AWARENESS
                    };
                    try {
                        streamNetIface = commo.addStreamingInterface("192.168.135.180",  8089,  allTypes,  readFile("/sdcard/parcerts/atak_2.p12"),  readFile("/sdcard/parcerts/truststore.p12"),  "atakatak",  null,  null);
                    } catch (CommoException e) {
                        Log.e("COMMOAPP", "Unable to add streaming iface", e);
                    }
                    statusLabel.setText("Server disconnected");
                } else if (streamNetIface != null) {
                    commo.removeStreamingInterface(streamNetIface);
                    statusLabel.setText("Server disabled");
                    
                }
                
            }
        });

        try {
            Commo.initNativeLibraries();
        } catch (CommoException e) {
            statusLabel.setText("Lib Init Failed!");
            return;
        }

        try {
            commo = new Commo(this, uid, callsign);
            commo.addInterfaceStatusListener(this);
            commo.addContactPresenceListener(this);
            commo.addCoTMessageListener(this);


            List<NetworkInterface> allnics = Collections.list(NetworkInterface.getNetworkInterfaces());
            byte[] b = null;
            for (NetworkInterface nic : allnics) {
                if (!nic.getName().toLowerCase().startsWith("wlan"))
                    continue;

                b = nic.getHardwareAddress();
                if (b != null)
                    break;
            }

            if (b == null) {
                statusLabel.setText("No wlan found!");
                Log.i("COMMOAPP", "No wlan found!");
            } else {
                commo.addInboundInterface(b, 6969, new String[]{"239.2.3.1"});
                commo.addBroadcastInterface(b, new CoTMessageType[]{CoTMessageType.SITUATIONAL_AWARENESS}, "239.2.3.1", 6969);
            }

            commo.enableMissionPackageIO(this, 8082);
        } catch (SocketException e) {
            statusLabel.setText("Commo create error!");
            Log.e("COMMOAPP", "Error during commo init", e);

        } catch (CommoException e) {
            statusLabel.setText("Commo create error!");
            Log.e("COMMOAPP", "Error during commo init", e);
        }
        Log.i("COMMOAPP", "Initialized and running!");

        contactsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        contactList.setAdapter(contactsAdapter);

        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                sendCoT();
            }
        }, 1000, 5000);

    }
    
    
    private void sendCoT() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.'000Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        
        Date d = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.MINUTE, 2);
        Date stale = c.getTime(); 
        
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"");
        sb.append(uid);
        sb.append("\" type=\"a-f-G-U-C\" time=\"");
        sb.append(sdf.format(d));
        sb.append("\" start=\"") 
          .append(sdf.format(d)) 
          .append("\" stale=\"") 
          .append(sdf.format(stale))
          .append("\" how=\"h-e\">")
          .append("<point lat=\"36.5261810013514\" lon=\"-77.3862509255614\" hae=\"9999999.0\" ")
          .append("ce=\"9999999\" le=\"9999999\"/>")
          .append("<detail>        <contact phone=\"3152545187\" endpoint=\"10.233.154.blah:221\"")
          .append(" callsign=\"")
          .append(callsign)
          .append("\"/>")  
          .append("<uid Droid=\"JDOG\"/>")
          .append("<__group name=\"Cyan\" role=\"Team Member\"/>")
          .append("<status battery=\"100\"/>")
          .append("<track speed=\"0")
          .append("\" course=\"56.23885995781046\"/>")
          .append("<precisionlocation geopointsrc=\"User\" altsrc=\"???\"/>")
          .append("</detail>")
          .append("</event>");
        commo.broadcastCoT(sb.toString());
    }
    
}
