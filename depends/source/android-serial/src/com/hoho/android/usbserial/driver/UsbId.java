/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.driver;

/**
 * Registry of USB vendor/product ID constants.
 *
 * Culled from various sources; see
 * <a href="http://www.linux-usb.org/usb.ids">usb.ids</a> for one listing.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public final class UsbId {

    public static final int VENDOR_FTDI = 0x0403;
    public static final int FTDI_FT232R = 0x6001;
    public static final int FTDI_FT231X = 0x6015;

    public static final int VENDOR_ATMEL = 0x03EB;
    public static final int ATMEL_LUFA_CDC_DEMO_APP = 0x2044;

    public static final int VENDOR_ARDUINO = 0x2341;
    public static final int ARDUINO_UNO = 0x0001;
    public static final int ARDUINO_MEGA_2560 = 0x0010;
    public static final int ARDUINO_SERIAL_ADAPTER = 0x003b;
    public static final int ARDUINO_MEGA_ADK = 0x003f;
    public static final int ARDUINO_MEGA_2560_R3 = 0x0042;
    public static final int ARDUINO_UNO_R3 = 0x0043;
    public static final int ARDUINO_MEGA_ADK_R3 = 0x0044;
    public static final int ARDUINO_SERIAL_ADAPTER_R3 = 0x0044;
    public static final int ARDUINO_LEONARDO = 0x8036;

    public static final int VENDOR_VAN_OOIJEN_TECH = 0x16c0;
    public static final int VAN_OOIJEN_TECH_TEENSYDUINO_SERIAL = 0x0483;

    public static final int VENDOR_LEAFLABS = 0x1eaf;
    public static final int LEAFLABS_MAPLE = 0x0004;

    public static final int VENDOR_SILABS = 0x10c4;
    public static final int SILABS_CP2102 = 0xea60;
    public static final int SILABS_CP2105 = 0xea70;
    public static final int SILABS_CP2108 = 0xea71;
    public static final int SILABS_CP2110 = 0xea80;

    public static final int VENDOR_ROCKWELL = 0x1c05;
    public static final int ROCKWELL_RSR = 0x4040;

    public static final int VENDOR_BLACKDIAMOND = 0x2A54;
    public static final int BD_SERIAL = 0x04E0;
    public static final int BD_SERIAL_LEGACY = 0x0450;
    public static final int BD_SERIAL_2 = 0x0458;

    public static final int VENDOR_PROLIFIC = 0x067b;
    public static final int PROLIFIC_PL2303 = 0x2303;

    public static final int VENDOR_VARASCITE = 0x0525;
    public static final int VERASCITE_ACM1 = 0xA4A6;
    public static final int VERASCITE_ACM2 = 0xA4A7;

    public static final int VENDOR_SEALEVEL_SYSTEMS = 0x0C52;
    public static final int SEALINK_422 = 0x9020;
   
    public static final int VENDOR_NAL_RESEARCH = 0x2017;
    public static final int NAL_SERIAL = 0x0003;

    public static final int VENDOR_WHOI = 0xac03;
    public static final int WHOI_RADIO = 0x1930;

    public static final int VENDOR_STUDER = 0x16d0;
    public static final int DAISY_RECEIVER = 0x0b03;
    public static final int DAISY_RECEIVER2 = 0x03ea;

    public static final int VENDOR_MAYFLOWER = 0x1f00;
    public static final int MAYFLOWER_RECEIVER = 0x2012;

    private UsbId() {
        throw new IllegalAccessError("Non-instantiable class.");
    }

}
