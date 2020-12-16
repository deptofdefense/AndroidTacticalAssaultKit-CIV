
package com.atakmap.map.gdal;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class MockVSIJFileFileSystemHandler {
    public static VSIJFileFilesystemHandler createSuccessfulHandler(
            final String prefix) {
        return new VSIJFileFilesystemHandler(prefix) {
            @Override
            public FileChannel open(String filename, String access)
                    throws IOException {
                if (!filename.startsWith(prefix)) {
                    return null;
                }
                String actualFilename = filename.substring(prefix.length());
                String sanitizedAccess = MockVSIJFileFileSystemHandler
                        .convertFileAccessMode(access);
                RandomAccessFile file = new RandomAccessFile(actualFilename,
                        sanitizedAccess);
                return file.getChannel();
            }

            @Override
            public int stat(String filename, VSIStatBuf statBuffer, int flags) {
                return 0;
            }
        };
    }

    private static String convertFileAccessMode(String access) {
        String sanitizedAccess = access.replace("b", "");
        if (sanitizedAccess.equals("r")) {
            return sanitizedAccess;
        }

        return "rw";
    }

    public static VSIJFileFilesystemHandler createExceptionalHandler(
            final String prefix,
            final ExceptionalHandlerController controller) {
        return new VSIJFileFilesystemHandler(prefix) {
            @Override
            public FileChannel open(String filename, String access)
                    throws IOException {
                if (controller.checkOpenMask()) {
                    throw new IOException("can't open file");
                }
                if (!filename.startsWith(prefix)) {
                    return null;
                }
                String actualFilename = filename.substring(prefix.length());
                String sanitizedAccess = MockVSIJFileFileSystemHandler
                        .convertFileAccessMode(access);
                RandomAccessFile file = new RandomAccessFile(actualFilename,
                        sanitizedAccess);

                final FileChannel chan = file.getChannel();
                return new FileChannel() {
                    @Override
                    public int read(ByteBuffer dst) throws IOException {
                        if (controller.checkRead1Mask()) {
                            throw new IOException(
                                    "can't read from file with 1 argument");
                        }
                        return chan.read(dst);
                    }

                    @Override
                    public long read(ByteBuffer[] dsts, int offset, int length)
                            throws IOException {
                        if (controller.checkRead3Mask()) {
                            throw new IOException(
                                    "can't read from file with 3 arguments");
                        }
                        return chan.read(dsts, offset, length);
                    }

                    @Override
                    public int write(ByteBuffer src) throws IOException {
                        if (controller.checkWrite1Mask()) {
                            throw new IOException(
                                    "can't write to file with 1 argument");
                        }
                        return chan.write(src);
                    }

                    @Override
                    public long write(ByteBuffer[] srcs, int offset, int length)
                            throws IOException {
                        if (controller.checkWrite3Mask()) {
                            throw new IOException(
                                    "can't write to file with 3 arguments");
                        }
                        return chan.write(srcs, offset, length);
                    }

                    @Override
                    public long position() throws IOException {
                        if (controller.checkPosition0Mask()) {
                            throw new IOException("can't get file position");
                        }
                        return chan.position();
                    }

                    @Override
                    public FileChannel position(long newPosition)
                            throws IOException {
                        if (controller.checkPosition1Mask()) {
                            throw new IOException("can't change file position");
                        }
                        return chan.position(newPosition);
                    }

                    @Override
                    public long size() throws IOException {
                        if (controller.checkSizeMask()) {
                            throw new IOException("can't get file size");
                        }
                        return chan.size();
                    }

                    @Override
                    public FileChannel truncate(long size) throws IOException {
                        if (controller.checkTruncateMask()) {
                            throw new IOException("can't truncate file");
                        }
                        return chan.truncate(size);
                    }

                    @Override
                    public void force(boolean metaData) throws IOException {
                        if (controller.checkForceMask()) {
                            throw new IOException("can't force file");
                        }
                        chan.force(metaData);
                    }

                    @Override
                    public long transferTo(long position, long count,
                            WritableByteChannel target) throws IOException {
                        if (controller.checkTransfertoMask()) {
                            throw new IOException("can't transfer to file");
                        }
                        return chan.transferTo(position, count, target);
                    }

                    @Override
                    public long transferFrom(ReadableByteChannel src,
                            long position, long count) throws IOException {
                        if (controller.checkTransferfromMask()) {
                            throw new IOException("can't transfer from file");
                        }
                        return chan.transferFrom(src, position, count);
                    }

                    @Override
                    public int read(ByteBuffer dst, long position)
                            throws IOException {
                        if (controller.checkRead2Mask()) {
                            throw new IOException(
                                    "can't read from file with 2 arguments");
                        }
                        return chan.read(dst, position);
                    }

                    @Override
                    public int write(ByteBuffer src, long position)
                            throws IOException {
                        if (controller.checkWrite2Mask()) {
                            throw new IOException(
                                    "can't write to file with 2 arguments");
                        }
                        return chan.write(src, position);
                    }

                    @Override
                    public MappedByteBuffer map(MapMode mode, long position,
                            long size) throws IOException {
                        if (controller.checkMapMask()) {
                            throw new IOException("can't map file");
                        }
                        return chan.map(mode, position, size);
                    }

                    @Override
                    public FileLock lock(long position, long size,
                            boolean shared) throws IOException {
                        if (controller.checkLockMask()) {
                            throw new IOException("can't lock file");
                        }
                        return chan.lock(position, size, shared);
                    }

                    @Override
                    public FileLock tryLock(long position, long size,
                            boolean shared) throws IOException {
                        if (controller.checkTryLockMask()) {
                            throw new IOException("can't try lock file");
                        }
                        return chan.tryLock(position, size, shared);
                    }

                    @Override
                    protected void implCloseChannel() throws IOException {
                        if (controller.checkImplclosechannelMask()) {
                            throw new IOException("can't close channel");
                        }
                        chan.close();
                    }
                };
            }

            @Override
            public int stat(String filename, VSIStatBuf statBuffer, int flags)
                    throws IOException {
                if (controller.checkStatMask()) {
                    throw new IOException("cannot stat");
                }
                return 0;
            }
        };
    }

    public static class ExceptionalHandlerController {
        private int methodOptions = 0;

        public void resetAll() {
            methodOptions = 0;
        }

        public static int OPEN_MASK = 1;

        public void setOpenMask() {
            methodOptions |= OPEN_MASK;
        }

        public void resetOpenMask() {
            methodOptions &= ~OPEN_MASK;
        }

        public boolean checkOpenMask() {
            return (methodOptions & OPEN_MASK) != 0;
        }

        public static int STAT_MASK = 1 << 1;

        public void setStatMask() {
            methodOptions |= STAT_MASK;
        }

        public void resetStatMask() {
            methodOptions &= ~STAT_MASK;
        }

        public boolean checkStatMask() {
            return (methodOptions & STAT_MASK) != 0;
        }

        public static int READ1_MASK = 1 << 2;

        public void setRead1Mask() {
            methodOptions |= READ1_MASK;
        }

        public void resetRead1Mask() {
            methodOptions &= ~READ1_MASK;
        }

        public boolean checkRead1Mask() {
            return (methodOptions & READ1_MASK) != 0;
        }

        public static int READ2_MASK = 1 << 3;

        public void setRead2Mask() {
            methodOptions |= READ2_MASK;
        }

        public void resetRead2Mask() {
            methodOptions &= ~READ2_MASK;
        }

        public boolean checkRead2Mask() {
            return (methodOptions & READ2_MASK) != 0;
        }

        public static int READ3_MASK = 1 << 4;

        public void setRead3Mask() {
            methodOptions |= READ3_MASK;
        }

        public void resetRead3Mask() {
            methodOptions &= ~READ3_MASK;
        }

        public boolean checkRead3Mask() {
            return (methodOptions & READ3_MASK) != 0;
        }

        public static int WRITE1_MASK = 1 << 5;

        public void setWrite1Mask() {
            methodOptions |= WRITE1_MASK;
        }

        public void resetWrite1Mask() {
            methodOptions &= ~WRITE1_MASK;
        }

        public boolean checkWrite1Mask() {
            return (methodOptions & WRITE1_MASK) != 0;
        }

        public static int WRITE2_MASK = 1 << 6;

        public void setWrite2Mask() {
            methodOptions |= WRITE2_MASK;
        }

        public void resetWrite2Mask() {
            methodOptions &= ~WRITE2_MASK;
        }

        public boolean checkWrite2Mask() {
            return (methodOptions & WRITE2_MASK) != 0;
        }

        public static int WRITE3_MASK = 1 << 7;

        public void setWrite3Mask() {
            methodOptions |= WRITE3_MASK;
        }

        public void resetWrite3Mask() {
            methodOptions &= ~WRITE3_MASK;
        }

        public boolean checkWrite3Mask() {
            return (methodOptions & WRITE3_MASK) != 0;
        }

        public static int POSITION0_MASK = 1 << 8;

        public void setPosition0Mask() {
            methodOptions |= POSITION0_MASK;
        }

        public void resetPosition0Mask() {
            methodOptions &= ~POSITION0_MASK;
        }

        public boolean checkPosition0Mask() {
            return (methodOptions & POSITION0_MASK) != 0;
        }

        public static int POSITION1_MASK = 1 << 9;

        public void setPosition1Mask() {
            methodOptions |= POSITION1_MASK;
        }

        public void resetPosition1Mask() {
            methodOptions &= ~POSITION1_MASK;
        }

        public boolean checkPosition1Mask() {
            return (methodOptions & POSITION1_MASK) != 0;
        }

        public static int SIZE_MASK = 1 << 10;

        public void setSizeMask() {
            methodOptions |= SIZE_MASK;
        }

        public void resetSizeMask() {
            methodOptions &= ~SIZE_MASK;
        }

        public boolean checkSizeMask() {
            return (methodOptions & SIZE_MASK) != 0;
        }

        public static int TRUNCATE_MASK = 1 << 11;

        public void setTruncateMask() {
            methodOptions |= TRUNCATE_MASK;
        }

        public void resetTruncateMask() {
            methodOptions &= ~TRUNCATE_MASK;
        }

        public boolean checkTruncateMask() {
            return (methodOptions & TRUNCATE_MASK) != 0;
        }

        public static int FORCE_MASK = 1 << 12;

        public void setForceMask() {
            methodOptions |= FORCE_MASK;
        }

        public void resetForceMask() {
            methodOptions &= ~FORCE_MASK;
        }

        public boolean checkForceMask() {
            return (methodOptions & FORCE_MASK) != 0;
        }

        public static int TRANSFERTO_MASK = 1 << 13;

        public void setTransfertoMask() {
            methodOptions |= TRANSFERTO_MASK;
        }

        public void resetTransfertoMask() {
            methodOptions &= ~TRANSFERTO_MASK;
        }

        public boolean checkTransfertoMask() {
            return (methodOptions & TRANSFERTO_MASK) != 0;
        }

        public static int TRANSFERFROM_MASK = 1 << 14;

        public void setTransferfromMask() {
            methodOptions |= TRANSFERFROM_MASK;
        }

        public void resetTransferfromMask() {
            methodOptions &= ~TRANSFERFROM_MASK;
        }

        public boolean checkTransferfromMask() {
            return (methodOptions & TRANSFERFROM_MASK) != 0;
        }

        public static int MAP_MASK = 1 << 15;

        public void setMapMask() {
            methodOptions |= MAP_MASK;
        }

        public void resetMapMask() {
            methodOptions &= ~MAP_MASK;
        }

        public boolean checkMapMask() {
            return (methodOptions & MAP_MASK) != 0;
        }

        public static int LOCK_MASK = 1 << 16;

        public void setLockMask() {
            methodOptions |= LOCK_MASK;
        }

        public void resetLockMask() {
            methodOptions &= ~LOCK_MASK;
        }

        public boolean checkLockMask() {
            return (methodOptions & LOCK_MASK) != 0;
        }

        public static int TRYlOCK_MASK = 1 << 17;

        public void setTryLockMask() {
            methodOptions |= TRYlOCK_MASK;
        }

        public void resetTryLockMask() {
            methodOptions &= ~TRYlOCK_MASK;
        }

        public boolean checkTryLockMask() {
            return (methodOptions & TRYlOCK_MASK) != 0;
        }

        public static int IMPLCLOSECHANNEL_MASK = 1 << 18;

        public void setImplclosechannelMask() {
            methodOptions |= IMPLCLOSECHANNEL_MASK;
        }

        public void resetImplclosechannelMask() {
            methodOptions &= ~IMPLCLOSECHANNEL_MASK;
        }

        public boolean checkImplclosechannelMask() {
            return (methodOptions & IMPLCLOSECHANNEL_MASK) != 0;
        }
    }
}
