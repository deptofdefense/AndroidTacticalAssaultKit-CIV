package com.atakmap.commoncommotest;

import com.atakmap.commoncommo.*;

import java.text.SimpleDateFormat;
import java.io.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;

public class CommoTest extends JPanel implements CoTMessageListener,
                                  ContactPresenceListener,
                                  InterfaceStatusListener,
                                  MissionPackageIO,
                                  CommoLogger, ActionListener
{
    static class NetInfo {
        boolean isAdded;
        boolean isUp;
        boolean isError;
        byte[] physAddr;
        String displayName;
        
        public NetInfo(String name, byte[] physAddr) {
            isAdded = false;
            isUp = false;
            this.physAddr = physAddr;
            this.displayName = name;
        }
    }
    
    static class NetInfoRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list,
                                          Object value, int index,
                                          boolean isSelected,
                                          boolean cellHasFocus)
        {
            NetInfo info = (NetInfo)value;
            String text = info.displayName;
            if (info.isAdded) text = "[+] " + text;
            super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            
            if (info.isUp)
                setForeground(Color.GREEN);
            else if (info.isError)
                setForeground(Color.RED);
            else if (info.isAdded)
                setForeground(Color.ORANGE);
            return this;
        }
    }
    
    private Commo commo;
    
    private JFileChooser fileChooser;


    private HashSet<Contact> contacts;
    private IdentityHashMap<NetInterface, NetInfo> inboundNetMap;
    private IdentityHashMap<NetInterface, NetInfo> bcastNetMap;
    
    private PrintWriter cotWriter;

    private int mprxCount;
    
    private DefaultListModel<NetInfo> netIfaceListModel;    
    private DefaultListModel<String> contactListModel;
    
    private JList<String> contactList;
    private JList<NetInfo> netIfaceList;
    
    private JButton addNetBtn;
    private JButton deleteNetBtn;
    private JButton takServerBtn;

    private JButton sendFileBtn;

    private JLabel countLabel;
    private int txCount;
    
    private String uid;
    private String callsign;


    public CommoTest(String uid, String callsign) throws IOException, CommoException {
        this.uid = uid;
        this.callsign = callsign;
        
        commo = new Commo(this, uid, callsign);
        commo.enableMissionPackageIO(this, 9871);
        commo.addContactPresenceListener(this);
        commo.addInterfaceStatusListener(this);
        commo.addCoTMessageListener(this);
        contacts = new HashSet<Contact>();
        inboundNetMap = new IdentityHashMap<NetInterface, NetInfo>();
        bcastNetMap = new IdentityHashMap<NetInterface, NetInfo>();
        mprxCount = 0;

        txCount = 0;

        cotWriter = new PrintWriter(new File("commo-cot.log"));
        
        
        netIfaceListModel = new DefaultListModel<NetInfo>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface iface = nets.nextElement();
                    byte[] b = iface.getHardwareAddress();
                    netIfaceListModel.addElement(new NetInfo(iface.getDisplayName(), b));
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        
        contactListModel = new DefaultListModel<String>();
        
        
        fileChooser = new JFileChooser();
    
        
        netIfaceList = new JList<NetInfo>(netIfaceListModel);
        netIfaceList.setPreferredSize(new Dimension(140, 200));
        netIfaceList.setMinimumSize(new Dimension(140, 200));
        netIfaceList.setCellRenderer(new NetInfoRenderer());
        contactList = new JList<String>(contactListModel);
        contactList.setPreferredSize(new Dimension(140, 200));
        contactList.setMinimumSize(new Dimension(140, 200));
        
        addNetBtn = new JButton("Add");
        deleteNetBtn = new JButton("Remove");
        takServerBtn = new JButton("TAK Server");
        sendFileBtn = new JButton("Send file");
        addNetBtn.addActionListener(this);
        deleteNetBtn.addActionListener(this);
        takServerBtn.addActionListener(this);
        sendFileBtn.addActionListener(this);
        
        countLabel = new JLabel("SA Transmit Count: 0");
        
        JPanel listPanel = new JPanel();
        listPanel.add(contactList);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(listPanel);
        left.add(Box.createVerticalGlue());
        left.add(sendFileBtn);
        left.setBorder(BorderFactory.createTitledBorder("Contacts"));
        
        JPanel p = new JPanel();
        p.add(addNetBtn);
        p.add(deleteNetBtn);
        p.add(takServerBtn);
        
        listPanel = new JPanel();
        listPanel.add(netIfaceList);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(listPanel);
        right.add(Box.createVerticalGlue());
        right.add(p);
        right.setBorder(BorderFactory.createTitledBorder("Network"));

        JPanel topPanel = new JPanel();        
        topPanel.add(left);
        topPanel.add(right);
        
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.CENTER);
        add(countLabel, BorderLayout.SOUTH);
        
        
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            sendCoT();
                            txCount++;
                            countLabel.setText("SA Transmit Count: " + txCount);
                        }
                    });
                    
                    try { 
                        Thread.sleep(3000);
                    } catch (Exception ex) {
                    }
                }
            }
        });
        
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
        
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
        try {
            commo.broadcastCoT(sb.toString());
        } catch (CommoException e) {
            e.printStackTrace();
        }
    }
    
    
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand().charAt(0)) {
        case 'A':
            {
                try {
                CoTMessageType[] types = new CoTMessageType[] {
                    CoTMessageType.SITUATIONAL_AWARENESS
                };
                NetInfo ni = netIfaceList.getSelectedValue();
                if (ni == null)
                    return;
                NetInterface iface = 
                    commo.addBroadcastInterface(ni.physAddr, types, "239.2.3.1",
                                            6969);
                NetInterface iface2 = 
                    commo.addInboundInterface(ni.physAddr, 6969, new String[] { "239.2.3.1" });
                synchronized(this) {
                    bcastNetMap.put(iface, ni);
                    inboundNetMap.put(iface2, ni);
                }
                ni.isAdded = true;
                netIfaceList.repaint();
                } catch (CommoException ex) {
                    ex.printStackTrace();
                }
            }
            break;
        case 'R':
            {
                PhysicalNetInterface inbound = null;
                PhysicalNetInterface bcast = null;
                NetInfo ni = netIfaceList.getSelectedValue();
                if (ni == null)
                    return;
                
                synchronized (this) {
                
                    for (Map.Entry<NetInterface, NetInfo> ent : inboundNetMap.entrySet()) {
                        if (ent.getValue() == ni) {
                            inbound = (PhysicalNetInterface)ent.getKey();
                            inboundNetMap.remove(ent.getKey());
                            break;
                        }
                    }
                    for (Map.Entry<NetInterface, NetInfo> ent : bcastNetMap.entrySet()) {
                        if (ent.getValue() == ni) {
                            bcast = (PhysicalNetInterface)ent.getKey();
                            bcastNetMap.remove(ent.getKey());
                            break;
                        }
                    }
                }
                commo.removeInboundInterface(inbound);
                commo.removeBroadcastInterface(bcast);
                
                ni.isAdded = false;
                ni.isUp = false;
                netIfaceList.repaint();
            }
            break;

        case 'T':
            String host = JOptionPane.showInputDialog(this, "Enter hostname");
            String port = JOptionPane.showInputDialog(this, "Enter port");

            fileChooser.setDialogTitle("Select Cert");
            File cert = null;
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                cert = fileChooser.getSelectedFile();

            fileChooser.setDialogTitle("Select Trust Cert");
            if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                return;
            File cacert = fileChooser.getSelectedFile();
            
            try {
                byte[] certBytes = null;
                byte[] cacertBytes = null;
                if (cert != null) {
                    certBytes = new byte[(int)cert.length()];
                    cacertBytes = new byte[(int)cacert.length()];
                
                    FileInputStream fis = new FileInputStream(cert);
                    fis.read(certBytes);
                    fis.close();
                    fis = new FileInputStream(cacert);
                    fis.read(cacertBytes);
                    fis.close();
                }

                CoTMessageType[] types = new CoTMessageType[] {
                    CoTMessageType.SITUATIONAL_AWARENESS,
                    CoTMessageType.CHAT
                };
                NetInterface iface = 
                    commo.addStreamingInterface(host, Integer.parseInt(port),
                             types, certBytes, cacertBytes, "atakatak", "atakatak",
                             null, null);
                NetInfo ni = new NetInfo("TAK: " + host + ":" + port + (certBytes == null ? " tcp" : " ssl"),null);
                netIfaceListModel.addElement(ni);
                synchronized(this) {
                    inboundNetMap.put(iface, ni);
                }
                ni.isAdded = true;
                netIfaceList.repaint();
                
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Problem adding streaming server " + ex, "Error", JOptionPane.ERROR_MESSAGE);
            }
            
            break;

        case 'S':
            java.util.List<String> uids = contactList.getSelectedValuesList();
            if (uids == null || uids.size() == 0)
                return;
            Vector<Contact> dests = new Vector<Contact>();
            
            for (int i =0; i < uids.size(); ++i) {
                boolean found = false;
                for (Contact c : contacts) {
                    if (c.contactUID.equals(uids.get(i))) {
                         dests.addElement(c);
                         found = true;
                         break;
                    }
                }
                if (!found) {
                    System.err.println("Contact matching error");
                    System.exit(1);
                }
            }
            
            if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                return;
            
            Vector<Contact> destsCopy = new Vector<Contact>(dests);
            File file = fileChooser.getSelectedFile();
            try {
                int id = commo.sendMissionPackage(dests, file, file.getName(), "javacommo-" + file.getName());
                System.out.println("[MPIO] TxSend started id=" + id + " to " + destsCopy.size()  + " receivers; " + dests.size() + " initially gone");
            } catch (CommoException ex) {
                System.out.println("[MPIO] TxSend start failed: " + ex);
            }
            
            break;
        }
    }
    
    
    private void refresh() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                refreshEventThread();
            }
        });
    }
    
    private void refreshEventThread() {
        contactListModel.clear();
        synchronized (this) {
            for (Contact c : contacts)
                contactListModel.addElement(c.contactUID);
        }
        contactList.repaint();
    }


    // CommoLogger
    public synchronized void log(Level level, String msg) {
        System.out.println("[" + level.toString() + "] " + msg);
    }
    
    
    // ContactPresenceListener
    public synchronized void contactAdded(Contact c) {
        contacts.add(c);
        refresh();
    }
    
    public synchronized void contactRemoved(Contact c) {
        contacts.remove(c);
        refresh();
    }
    
    
    // InterfaceStatusListener
    public synchronized void interfaceError(final NetInterface iface,
                                        final NetInterfaceErrorCode errCode) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                NetInfo inf = inboundNetMap.get(iface);
                if (inf != null) {
                    inf.isError = true;
                    netIfaceList.repaint();
                }
            }
        });
    }

    public synchronized void interfaceUp(final NetInterface iface) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                NetInfo inf = inboundNetMap.get(iface);
                if (inf != null) {
                    inf.isUp = true;
                    netIfaceList.repaint();
                }
            }
        });
    }

    public synchronized void interfaceDown(final NetInterface iface) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                NetInfo inf = inboundNetMap.get(iface);
                if (inf != null) {
                    inf.isUp = false;
                    netIfaceList.repaint();
                }
            }
        });
    }
    
    
    
    // MissionPackageIO
    public String createUUID() {
        return UUID.randomUUID().toString();
    }
    
    public CoTPointData getCurrentPoint() {
        return new CoTPointData(36.1, -77.38);
    }
    
    
    public File missionPackageReceiveInit(String fileName, String transferName,
                                          String shaHash, String sender) {
        mprxCount++;
        File f = new File("mprx-" + mprxCount + "-" + fileName);
        System.out.println("[MPIO] RxInit " + fileName + 
                           " with name " + transferName + " from " + sender + " -- returning " + f);
        return f;
    }
    
    public void missionPackageReceiveComplete(File file, MissionPackageTransferStatus status, String error) {
        System.out.println("[MPIO] RxComplete " + file + " status = " +  status + " error = " + error);
    }
    
    public void missionPackageSendStatusUpdate(MissionPackageTransferStatusUpdate update) {
        System.out.println("[MPIO] TxUpdate " + update.transferId + " from " + update.recipient.contactUID  + " status = " +  update.status + " reason = " + update.reason);
    }
    
    
    public void cotMessageReceived(String msg, String a) {
        cotWriter.println(msg);
        cotWriter.flush();
    }
    
    
    public static void main(String[] args) throws Exception {
        Commo.initNativeLibraries();
    
    
        JFrame jf = new JFrame();
        CommoTest me = new CommoTest("CommoTestJava", "CommoJava");
        jf.getContentPane().add(me);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.pack();
        jf.show();
    }
    
}

