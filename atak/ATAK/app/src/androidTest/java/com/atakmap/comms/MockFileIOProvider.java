
package com.atakmap.comms;

import java.nio.channels.FileChannel;

public class MockFileIOProvider
        implements com.atakmap.commoncommo.FileIOProvider {
    public boolean open_invoked = false;
    public String open_path = null;
    public String open_mode = null;
    public boolean getSize_invoked = false;
    public String getSize_path = null;

    public void reset() {
        open_invoked = false;
        open_path = null;
        open_mode = null;
        getSize_invoked = false;
        getSize_path = null;
    }

    @Override
    public FileChannel open(String path, String mode) {
        open_invoked = true;
        open_path = path;
        open_mode = mode;
        return null;
    }

    @Override
    public long getSize(String path) {
        getSize_invoked = true;
        getSize_path = path;
        return 0;
    }
}
