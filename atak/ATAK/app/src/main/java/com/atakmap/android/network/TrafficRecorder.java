
package com.atakmap.android.network;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Toast;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.comms.NetworkUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

public class TrafficRecorder implements Runnable {

    private static final int DEFAULT_BUFFER_LENGTH = 1024 * 64; // 64k ; max udp packet size

    // This is the maximum interval between issuing joins when no traffic is received.
    private static final int _IGMP_JOIN_INTERVAL = 20000; // 8k milliseconds

    private static final String TAG = "TrafficRecorder";
    private final String address;
    private final int port;
    private final NetworkInterface ni;
    private final File file;
    private final File index;
    private final View view;

    private MulticastSocket socket;
    private boolean cancelled = false;
    private AlertDialog ad = null;

    /**
     * Create a new traffic recorder.
     * @param address the network address to record (multicast).
     * @param port  the network port to record from.
     * @param ni the network device to record from.
     * @param file the file to record to.
     */
    public TrafficRecorder(final String address,
            final int port,
            final NetworkInterface ni,
            final File file,
            final View view) {
        this.address = address;
        this.port = port;
        this.ni = ni;
        this.file = file;
        this.index = new File(file.toString() + ".idx");
        this.view = view;

    }

    public void cancel() {
        cancelled = true;
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public void run() {
        create();

        DatagramPacket packet = new DatagramPacket(
                new byte[DEFAULT_BUFFER_LENGTH], DEFAULT_BUFFER_LENGTH);
        InetAddress sourceAddress = null;
        try {
            sourceAddress = InetAddress.getByAddress(NetworkUtils
                    .getByteAddress(address));
        } catch (Exception ignored) {
        }

        if (sourceAddress == null || ni == null) {
            dismiss("source interface missing, error recording", ad);
            return;
        }

        try {
            socket = new MulticastSocket(port);
            socket.setSoTimeout(_IGMP_JOIN_INTERVAL);
        } catch (Exception e) {
            dismiss("unable to listen for data, stopping", ad);
            return;
        }

        if (sourceAddress.isMulticastAddress()) {
            InetSocketAddress sourceInetAddress = new InetSocketAddress(
                    address, port);
            try {
                socket.joinGroup(sourceInetAddress, ni);
            } catch (Exception e) {
                dismiss("error occurred subscribing to traffic", ad);
                return;
            }
        }

        long filelength = 0;

        try (FileOutputStream fos = IOProviderFactory.getOutputStream(file);
                OutputStream os = IOProviderFactory.getOutputStream(index);
                PrintWriter idxos = new PrintWriter(os)) {

            while (!cancelled) {
                socket.receive(packet);
                fos.write(packet.getData(), packet.getOffset(),
                        packet.getLength());
                int size = packet.getLength() - packet.getOffset();
                idxos.println(filelength + ","
                        + android.os.SystemClock.elapsedRealtime()
                        + "," + size);
                filelength += size;
            }
        } catch (FileNotFoundException fnfe) {
            dismiss("unable to create recording file", ad);
            return;
        } catch (IOException ioe) {
            if (!cancelled)
                Log.d(TAG, "exception has occurred: ", ioe);
        }
        dismiss(null, ad);
    }

    private void create() {
        final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                view.getContext());
        alertBuilder
                .setTitle("Recording Traffic")
                .setMessage(
                        "Recording traffic from "
                                + address
                                + ":"
                                + port
                                + " until cancelled or 20 seconds has elapsed without receiving data.")
                .setPositiveButton("CANCEL",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                cancel();
                            }
                        });
        view.post(new Runnable() {
            @Override
            public void run() {
                ad = alertBuilder.create();
                ad.show();
            }
        });

    }

    private void dismiss(final String reason, final AlertDialog ad) {
        view.post(new Runnable() {
            @Override
            public void run() {
                if (reason != null)
                    Toast.makeText(view.getContext(), reason,
                            Toast.LENGTH_SHORT)
                            .show();

                if (ad != null)
                    ad.dismiss();
            }
        });
    }

}
