package com.atakmap.commoncommotest;

import com.atakmap.commoncommo.*;

import java.text.SimpleDateFormat;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.awt.*;
import java.awt.event.*;
import java.net.*;

public class CommoTest extends JPanel implements CoTMessageListener,
                                  ContactPresenceListener,
                                  InterfaceStatusListener,
                                  MissionPackageIO,
                                  CommoLogger
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
    private IdentityHashMap<TcpInboundNetInterface, NetInfo> tcpInboundNetMap;
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

    private JButton sendAllChatBtn;
    private JButton broadcastCotBtn;

    private JScrollPane cotMessageScroll;
    private JTextArea cotMessageLabel;

    private Transformer transformer;

    private JLabel missionPackageStatusLabel;
    private JLabel countLabel;
    private int txCount;
    
    private String uid;
    private String callsign;


    public CommoTest(String uid, String callsign) throws IOException, CommoException {
        this.uid = uid;
        this.callsign = callsign;
        
        commo = new Commo(this, uid, callsign);
        commo.setupMissionPackageIO(this);
        commo.setMissionPackageLocalPort(8080);
        byte[] httpsCert = commo.generateSelfSignedCert("atakatak");
        commo.setMissionPackageLocalHttpsParams(8443, httpsCert, "atakatak");
        commo.setMissionPackageNumTries(10);
        commo.setMissionPackageConnTimeout(10);
        commo.setMissionPackageTransferTimeout(120);
        commo.setMissionPackageHttpsPort(8443);
        commo.setTcpConnTimeout(20);
        commo.setTTL(64);
        commo.setEnableAddressReuse(false);
        commo.addContactPresenceListener(this);
        commo.addInterfaceStatusListener(this);
        commo.addCoTMessageListener(this);
        contacts = new HashSet<Contact>();
        inboundNetMap = new IdentityHashMap<NetInterface, NetInfo>();
        tcpInboundNetMap = new IdentityHashMap<TcpInboundNetInterface, NetInfo>();
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
                    if (b == null) continue;
                    netIfaceListModel.addElement(new NetInfo(iface.getDisplayName(), b));
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        
        contactListModel = new DefaultListModel<String>();
        
        
        fileChooser = new JFileChooser();
    
        
        netIfaceList = new JList<NetInfo>(netIfaceListModel);
        netIfaceList.setPreferredSize(new Dimension(300, 200));
        netIfaceList.setMinimumSize(new Dimension(300, 200));
        netIfaceList.setCellRenderer(new NetInfoRenderer());
        contactList = new JList<String>(contactListModel);
        contactList.setPreferredSize(new Dimension(150, 200));
        contactList.setMinimumSize(new Dimension(150, 200));
        
        addNetBtn = new JButton("Add");
        deleteNetBtn = new JButton("Remove");
        takServerBtn = new JButton("TAK Server");
        sendFileBtn = new JButton("Send file");
        sendAllChatBtn = new JButton("Send All Chat");
        broadcastCotBtn = new JButton("Broadcast CoT");
        addNetBtn.addActionListener(actionEvent -> addNetworkInterface());
        deleteNetBtn.addActionListener(actionEvent -> removeNetworkInterface());
        takServerBtn.addActionListener(actionEvent -> addServerNetworkInterface());
        sendFileBtn.addActionListener(actionEvent -> sendFile());
        sendAllChatBtn.addActionListener(actionEvent -> broadcastChat());
        broadcastCotBtn.addActionListener(actionEvent -> broadcastCot());

        countLabel = new JLabel("SA Transmit Count: 0");
        missionPackageStatusLabel = new JLabel("Mission Package Status: --");
        cotMessageLabel = new JTextArea();
        cotMessageLabel.setPreferredSize(new Dimension(400, 800));
        cotMessageLabel.setMinimumSize(new Dimension(400, 800));
        cotMessageLabel.setLineWrap(true);
        cotMessageLabel.setEditable(false);
        cotMessageScroll = new JScrollPane(cotMessageLabel);
        cotMessageScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        cotMessageScroll.setPreferredSize(new Dimension(400, 200));
        cotMessageScroll.setMinimumSize(new Dimension(400, 200));

        try {
            transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        }
        catch(TransformerConfigurationException e)
        {
            e.printStackTrace();
        }

        JPanel listPanel = new JPanel();
        listPanel.add(contactList);

        JPanel pLeftBtns = new JPanel();
        pLeftBtns.add(sendFileBtn);
        pLeftBtns.add(sendAllChatBtn);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(listPanel);
        left.add(Box.createVerticalGlue());
        left.add(pLeftBtns);
        left.setBorder(BorderFactory.createTitledBorder("Contacts"));
        
        JPanel pMiddleBtns = new JPanel();
        pMiddleBtns.add(addNetBtn);
        pMiddleBtns.add(deleteNetBtn);
        pMiddleBtns.add(takServerBtn);
        
        listPanel = new JPanel();
        listPanel.add(netIfaceList);

        JPanel middle = new JPanel();
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
        middle.add(listPanel);
        middle.add(Box.createVerticalGlue());
        middle.add(pMiddleBtns);
        middle.setBorder(BorderFactory.createTitledBorder("Network"));

        JPanel pRightBtns = new JPanel();
        pRightBtns.add(broadcastCotBtn);

        JPanel cotMessagePanel = new JPanel();
        cotMessagePanel.add(cotMessageScroll);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(cotMessagePanel);
        right.add(Box.createVerticalGlue());
        right.add(pRightBtns);
        right.setBorder(BorderFactory.createTitledBorder("CoT Messages"));

        JPanel topPanel = new JPanel();        
        topPanel.add(left);
        topPanel.add(middle);
        topPanel.add(right);

        JPanel bottomPanel = new JPanel();
        //bottomPanel.setPreferredSize(new Dimension(850, 50));
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(countLabel, BorderLayout.LINE_START);
        bottomPanel.add(missionPackageStatusLabel, BorderLayout.LINE_END);
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        missionPackageStatusLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);
        
        
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            sendSA();
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
    
    
    private void sendSA() {
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

    private void addNetworkInterface(){
        try {
            CoTMessageType[] saTypes = new CoTMessageType[] {
                    CoTMessageType.SITUATIONAL_AWARENESS
            };
            CoTMessageType[] chatTypes = new CoTMessageType[] {
                    CoTMessageType.CHAT
            };
            NetInfo ni = netIfaceList.getSelectedValue();
            if (ni == null)
                return;
            // SA Multicast
            NetInterface broadcastIface = commo.addBroadcastInterface(ni.physAddr, saTypes, "239.2.3.1", 6969);
            // Geochat Messages
            NetInterface broadcastIface2 = commo.addBroadcastInterface(ni.physAddr, chatTypes, "224.10.10.1", 17012);
            // SA Multicast
            NetInterface inboundIface = commo.addInboundInterface(ni.physAddr, 6969, new String[] { "239.2.3.1" }, false);
            // Default
            NetInterface inboundIface2 = commo.addInboundInterface(ni.physAddr, 4242, new String[] {}, false);
            // Wave Relay Multicast
            NetInterface inboundIface3 = commo.addInboundInterface(ni.physAddr, 18999, new String[] { "239.23.212.230" }, false);
            // Wave Relay Multicast 2
            NetInterface inboundIface4 = commo.addInboundInterface(ni.physAddr, 18200, new String[] { "227.8.135.15" }, false);
            // Services Multicast
            NetInterface inboundIface5 = commo.addInboundInterface(ni.physAddr, 18000, new String[] { "224.67.111.84"}, false);
            // PRC-152
            NetInterface inboundIface6 = commo.addInboundInterface(ni.physAddr, 10011, new String[] {}, false);
            // Jumpmaster DIPs
            NetInterface inboundIface7 = commo.addInboundInterface(ni.physAddr, 8087, new String[] {}, false);
            // GeoChat Messages
            NetInterface inboundIface8 = commo.addInboundInterface(ni.physAddr, 17012, new String[] { "224.10.10.1" }, false);
            // GeoChat Announce
            NetInterface inboundIface9 = commo.addInboundInterface(ni.physAddr, 18740, new String[] { "224.10.10.1" }, false);
            // Default TCP
            TcpInboundNetInterface tcpInboundInterface = commo.addTcpInboundInterface(4242);
            // Request Notify
            TcpInboundNetInterface tcpInboundInterface2 = commo.addTcpInboundInterface(8087);
            synchronized(this) {
                bcastNetMap.put(broadcastIface, ni);
                bcastNetMap.put(broadcastIface2, ni);
                inboundNetMap.put(inboundIface, ni);
                inboundNetMap.put(inboundIface2, ni);
                inboundNetMap.put(inboundIface3, ni);
                inboundNetMap.put(inboundIface4, ni);
                inboundNetMap.put(inboundIface5, ni);
                inboundNetMap.put(inboundIface6, ni);
                inboundNetMap.put(inboundIface7, ni);
                inboundNetMap.put(inboundIface8, ni);
                inboundNetMap.put(inboundIface9, ni);
                tcpInboundNetMap.put(tcpInboundInterface, ni);
                tcpInboundNetMap.put(tcpInboundInterface2, ni);
            }
            ni.isAdded = true;
            netIfaceList.repaint();
        } catch (CommoException ex) {
            ex.printStackTrace();
        }
    }

    private void removeNetworkInterface() {
        PhysicalNetInterface inbound = null;
        TcpInboundNetInterface tcpInbound = null;
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
            for (Map.Entry<TcpInboundNetInterface, NetInfo> ent : tcpInboundNetMap.entrySet()) {
                if (ent.getValue() == ni) {
                    tcpInbound = (TcpInboundNetInterface)ent.getKey();
                    tcpInboundNetMap.remove(ent.getKey());
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
        commo.removeTcpInboundInterface(tcpInbound);
        commo.removeInboundInterface(inbound);
        commo.removeBroadcastInterface(bcast);

        ni.isAdded = false;
        ni.isUp = false;
        netIfaceList.repaint();
    }

    private void addServerNetworkInterface() {
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
    }

    private Vector<Contact> getSelectedContacts() {
        java.util.List<String> uids = contactList.getSelectedValuesList();
        Vector<Contact> dests = new Vector<Contact>();
        if (uids == null || uids.size() == 0)
            return dests;

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

        return dests;
    }

    private void sendFile() {
        Vector<Contact> dests = getSelectedContacts();

        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        Vector<Contact> destsCopy = new Vector<Contact>(dests);
        File file = fileChooser.getSelectedFile();
        try {
            int id = commo.sendMissionPackageInit(dests, file, file.getName(), "javacommo-" + file.getName());
            commo.startMissionPackageSend(id);
            System.out.println("[MPIO] TxSend started id=" + id + " to " + destsCopy.size()  + " receivers; " + dests.size() + " initially gone");
        } catch (CommoException ex) {
            System.out.println("[MPIO] TxSend start failed: " + ex);
        }
    }

    private void broadcastChat() {
        String message = JOptionPane.showInputDialog(this, "Enter Chat Message");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.'000Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        Date d = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.MINUTE, 2);
        Date stale = c.getTime();

        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"");
        sb.append("GeoChat." + uid +".All Chat Rooms." + UUID.randomUUID().toString());
        sb.append("\" type=\"b-t-f\" time=\"");
        sb.append(sdf.format(d));
        sb.append("\" start=\"")
                .append(sdf.format(d))
                .append("\" stale=\"")
                .append(sdf.format(stale))
                .append("\" how=\"h-g-i-g-o\">")
                .append("<point lat=\"0\" lon=\"0\" hae=\"9999999.0\" ")
                .append("ce=\"9999999\" le=\"9999999\"/>")
                .append("<detail>")
                .append("<__chat groupOwner=\"false\" ")
                .append(" senderCallsign=\"")
                .append(callsign)
                .append("\" chatroom=\"All Chat Rooms\" id=\"All Chat Rooms\"><chatgrp id=\"All Chat Rooms\" uid1=\"All Chat Rooms\" uid0=\"")
                .append(uid)
                .append("\"/>")
                .append("</__chat>")
                .append("<link type=\"a-f-G-U-C-I\" uid=\"")
                .append(uid)
                .append("\" relation=\"p-p\"/>")
                .append("<remarks time=\"")
                .append(sdf.format(d))
                .append("\" to=\"All Chat Rooms\" sourceID=\"")
                .append(uid)
                .append("\" source=\"BAO.CommoTest.")
                .append(uid)
                .append("\">")
                .append(message)
                .append("</remarks>")
                .append("</detail>")
                .append("</event>");

        try {
            commo.broadcastCoT(sb.toString(), CoTSendMethod.ANY);
        } catch (CommoException commoException) {
            commoException.printStackTrace();
        }
    }

    private void broadcastCot() {
        String message = JOptionPane.showInputDialog(this, "Enter CoT Message to Broadcast");

        try {
            commo.broadcastCoT(message, CoTSendMethod.ANY);
        } catch (CommoException commoException) {
            commoException.printStackTrace();
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
    public synchronized void log(Level level, Type type, String msg, LoggingDetail detail) {
        if (type == Type.GENERAL) {
            System.out.println("[" + level.toString() + "] " + msg);
        }
        else if (type == Type.PARSING) {
            ParsingDetail parsingDetail = (ParsingDetail)detail;
            System.out.println("[" + level.toString() + "] " + msg + ", detail: " + parsingDetail);
        }
        else if (type == Type.NETWORK) {
            NetworkDetail networkDetail = (NetworkDetail)detail;
            System.out.println("[" + level.toString() + "] " + msg + ", detail: " + networkDetail);
        }
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
                                          String shaHash, long transferId, String sender) {
        mprxCount++;
        File f = new File("mprx-" + mprxCount + "-" + fileName);
        System.out.println("[MPIO] RxInit " + fileName + 
                           " with name " + transferName + " from " + sender + " -- returning " + f);
        missionPackageStatusLabel.setText(String.format("Mission Package Status: Receiving Package %s", f.getName()));
        return f;
    }
    
    public void missionPackageSendStatusUpdate(MissionPackageSendStatusUpdate update) {
        System.out.println("[MPIO] TxUpdate " + update.transferId + " from " + update.recipient.contactUID  + " status = " +  update.status);
        if (update.status == MissionPackageTransferStatus.FINISHED_SUCCESS) {
            missionPackageStatusLabel.setText("Mission Package Status: Package Sent!");
        }
        else {
            missionPackageStatusLabel.setText(String.format("Mission Package Status: Sending Package %d", update.totalBytesTransferred));
        }
    }

    public void missionPackageReceiveStatusUpdate(MissionPackageReceiveStatusUpdate update) {
        System.out.println("[MPIO] TxUpdate " + update.localFile.getName() + " attempt: " + update.attempt + ", status = " +  update.status);

        if (update.status == MissionPackageTransferStatus.FINISHED_SUCCESS) {
            missionPackageStatusLabel.setText("Mission Package Status: Package Receieved!");
            try {
                Desktop.getDesktop().browseFileDirectory(update.localFile); // Doesn't work on Windows 10?
            } catch(UnsupportedOperationException e) {
                e.printStackTrace();
                try {
                    Desktop.getDesktop().open(update.localFile);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }
        else {
            missionPackageStatusLabel.setText(String.format("Mission Package Status: Receiving Package %s %d/%d", update.localFile.getName(), update.totalBytesReceived, update.totalBytesExpected));
        }
    }
    
    public void cotMessageReceived(String msg, String a) {
        StreamSource source = new StreamSource(new StringReader(msg));
        StreamResult result = new StreamResult(new StringWriter());
        try {
            transformer.transform(source, result);
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        cotMessageLabel.setText(result.getWriter().toString());
        cotWriter.println(msg);
        cotWriter.flush();
    }
    
    
    public static void main(String[] args) throws Exception {
        System.loadLibrary("commoncommojni");
        Commo.initThirdpartyNativeLibraries();
    
        JFrame jf = new JFrame("Commo Test");
        CommoTest me = new CommoTest("CommoTestJava", "CommoJava");
        jf.getContentPane().add(me);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.pack();
        jf.show();
    }
    
}

