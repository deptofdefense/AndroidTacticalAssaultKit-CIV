
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Color;
import android.graphics.Point;

import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

/**
 *
 */
public class Command {

    protected int[] args;
    protected int currentArg;
    protected int currentArgBit;

    private final int elementClass;
    private final int elementID;

    private final int numArgs;
    private Integer[] partitions;
    protected int currentByte;

    private static final String TAG = "CGM Command";
    private final DataInput data;

    public Command(int elClass, int elID, int argCount, DataInput dataIn)
            throws IOException {
        data = dataIn;
        elementClass = elClass;
        elementID = elID;
        if (argCount != 31) {
            this.args = new int[argCount];
            for (int i = 0; i < argCount; i++)
                this.args[i] = data.readUnsignedByte();
            if (Math.abs(argCount) % 2 == 1) {
                try {
                    int skip = data.readUnsignedByte();
                } catch (IOException e) {
                    // we've reached the end of the data input. Since we're only
                    // skipping data here, the exception can be ignored.
                }
            }
            numArgs = argCount;
        } else {
            // this is a long form command
            boolean done = true;
            partitions = new Integer[1];
            Vector<Integer> parts = new Vector<>();
            int a = 0;
            do {
                argCount = read16(data);
                if (argCount == -1)
                    break;
                parts.add(argCount);
                if ((argCount & (1 << 15)) != 0) {
                    // data is partitioned and it's not the last partition
                    done = false;
                    // clear bit 15
                    argCount = argCount & ~(1 << 15);
                } else {
                    done = true;
                }
                if (this.args == null) {
                    this.args = new int[argCount];
                } else {
                    // resize the args array
                    this.args = Arrays.copyOf(this.args, this.args.length
                            + argCount);
                }
                for (int i = 0; i < argCount; i++)
                    this.args[a++] = data.readUnsignedByte();

                // align on a word if necessary
                if (Math.abs(argCount) % 2 == 1) {
                    int skip = data.readUnsignedByte();
                    if (BuildConfig.DEBUG && skip != 0)
                        throw new AssertionError("skipping data");
                }
            } while (!done);
            partitions = parts.toArray(partitions);
            if (args != null)
                numArgs = this.args.length;
            else
                numArgs = 0;
        }
    }

    /**
     * Removes the reference to the arguments, important for commands that have
     * large arguments such as cell arrays, etc.
     */
    public void cleanUpArguments() {
        this.args = null;
    }

    private int read16(DataInput in) throws IOException {
        return (in.readUnsignedByte() << 8) | in.readUnsignedByte();
    }

    @Override
    public String toString() {
        return "Unsupported " + this.elementClass + "," + this.elementID + " ("
                + (this.args != null ? numArgs : "arguments cleared")
                + ")";
    }

    final protected String makeFixedString() {
        int length = getStringCount();

        char[] c = new char[length];
        for (int i = 0; i < length; i++) {
            c[i] = makeChar();
        }

        return new String(c);
    }

    final protected String makeString() {
        int length = getStringCount();
        byte[] b = new byte[length];
        for (int i = 0; i < length; i++)
            b[i] = makeByte();
        return new String(b, FileSystemUtils.UTF8_CHARSET);
    }

    private int getStringCount() {
        int length = makeUInt8();
        if (length == 255) {
            length = makeUInt16();
            if ((length & (1 << 16)) != 0) {
                length = (length << 16) | makeUInt16();
            }
        }
        return length;
    }

    final protected byte makeByte() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg >= numArgs)
            throw new AssertionError();
        return (byte) this.args[this.currentArg++];
    }

    final protected char makeChar() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg >= numArgs)
            throw new AssertionError();
        return (char) this.args[this.currentArg++];
    }

    final protected int makeSignedInt8() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg >= numArgs)
            throw new AssertionError();
        return (byte) this.args[this.currentArg++];
    }

    final protected int makeSignedInt16() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg + 1 >= numArgs)
            throw new AssertionError();
        return ((short) (this.args[this.currentArg++] << 8)
                + this.args[this.currentArg++]);
    }

    final protected void BytesFromSignedInt16(int from, byte[] to) {
        to[currentByte++] = (byte) (from >> 8);
        to[currentByte++] = (byte) (from & 255);
    }

    final protected int makeSignedInt24() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg + 2 >= numArgs)
            throw new AssertionError();
        return (this.args[this.currentArg++] << 16)
                + (this.args[this.currentArg++] << 8)
                + this.args[this.currentArg++];
    }

    final protected int makeSignedInt32() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg + 3 >= numArgs)
            throw new AssertionError();
        return (this.args[this.currentArg++] << 24)
                + (this.args[this.currentArg++] << 16)
                + (this.args[this.currentArg++] << 8)
                + this.args[this.currentArg++];
    }

    final protected int makeInt() {
        int precision = IntegerPrecisionCommand.getPrecision();
        return makeInt(precision);
    }

    final protected int sizeOfInt() {
        int precision = IntegerPrecisionCommand.getPrecision();
        return precision / 8;
    }

    final protected int makeIndex() {
        int precision = IndexPrecisionCommand.getPrecision();
        return makeInt(precision);
    }

    final protected int makeName() {
        int precision = NamePrecisionCommand.getPrecision();
        return makeInt(precision);
    }

    private int makeInt(int precision) {
        skipBits();
        if (precision == 8) {
            return makeSignedInt8();
        }
        if (precision == 16) {
            return makeSignedInt16();
        }
        if (precision == 24) {
            return makeSignedInt24();
        }
        if (precision == 32) {
            return makeSignedInt32();
        }

        unsupported("unsupported integer precision " + precision);
        // return default
        return makeSignedInt16();
    }

    final protected void BytesFromInt(int from, byte[] to) {
        int precision = IntegerPrecisionCommand.getPrecision();
        if (precision == 8) {
            // no op
        }
        if (precision == 16) {
            BytesFromSignedInt16(from, to);
        }
        if (precision == 24) {
            // no op
        }
        if (precision == 32) {
            // no op
        }
    }

    final protected int makeUInt(int precision) {
        if (precision == 1) {
            return makeUInt1();
        }
        if (precision == 2) {
            return makeUInt2();
        }
        if (precision == 4) {
            return makeUInt4();
        }
        if (precision == 8) {
            return makeUInt8();
        }
        if (precision == 16) {
            return makeUInt16();
        }
        if (precision == 24) {
            return makeUInt24();
        }
        if (precision == 32) {
            return makeUInt32();
        }

        unsupported("unsupported uint precision " + precision);
        // return default
        return makeUInt8();
    }

    private int makeUInt32() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg + 3 >= numArgs)
            throw new AssertionError();
        return (char) (this.args[this.currentArg++] << 24)
                + (char) (this.args[this.currentArg++] << 16)
                + (char) (this.args[this.currentArg++] << 8)
                + (char) this.args[this.currentArg++];
    }

    private int makeUInt24() {
        skipBits();
        if (BuildConfig.DEBUG && this.currentArg + 2 >= numArgs)
            throw new AssertionError();
        return (char) (this.args[this.currentArg++] << 16)
                + (char) (this.args[this.currentArg++] << 8)
                + (char) this.args[this.currentArg++];
    }

    private int makeUInt16() {
        skipBits();

        if (this.currentArg + 1 < numArgs) {
            // this is the default, two bytes
            return (char) (this.args[this.currentArg++] << 8)
                    + (char) this.args[this.currentArg++];
        }

        // some CGM files request a 16 bit precision integer when there are only 8 bits left
        if (this.currentArg < numArgs) {
            // TODO: add logging
            return (char) this.args[this.currentArg++];
        }

        if (BuildConfig.DEBUG && !false)
            throw new AssertionError();
        return 0;
    }

    private int makeUInt8() {
        skipBits();

        if (BuildConfig.DEBUG && this.currentArg >= numArgs)
            throw new AssertionError();
        return (char) this.args[this.currentArg++];
    }

    private int makeUInt4() {
        return makeUIntBit(4);
    }

    private int makeUInt2() {
        return makeUIntBit(2);
    }

    private int makeUInt1() {
        return makeUIntBit(1);
    }

    private int makeUIntBit(int numBits) {
        if (BuildConfig.DEBUG && this.currentArg >= numArgs)
            throw new AssertionError();

        int bitsPosition = 8 - numBits - this.currentArgBit;
        int mask = ((1 << numBits) - 1) << bitsPosition;
        int ret = (char) ((this.args[this.currentArg] & mask) >> bitsPosition);
        this.currentArgBit += numBits;
        if (this.currentArgBit % 8 == 0) {
            // advance to next byte
            this.currentArgBit = 0;
            this.currentArg++;
        }
        return ret;
    }

    private void skipBits() {
        if (this.currentArgBit % 8 != 0) {
            // we read some bits from the current arg but aren't done, skip the rest
            this.currentArgBit = 0;
            this.currentArg++;
        }
    }

    final protected int makeVdc() {
        if (VDCTypeCommand.getType().equals(VDCTypeCommand.Type.REAL)) {
            VDCRealPrecisionCommand.Type precision = VDCRealPrecisionCommand
                    .getPrecision();
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FIXED_POINT_32BIT)) {
                return (int) makeFixedPoint32();
            }
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FIXED_POINT_64BIT)) {
                return (int) makeFixedPoint64();
            }
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FLOATING_POINT_32BIT)) {
                return (int) makeFloatingPoint32();
            }
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FLOATING_POINT_64BIT)) {
                return (int) makeFloatingPoint64();
            }

            unsupported("unsupported precision " + precision);
            return (int) makeFixedPoint32();
        }

        // defaults to integer
        int precision = VDCIntegerPrecisionCommand.getPrecision();
        if (precision == 16) {
            return makeSignedInt16();
        }
        if (precision == 24) {
            return makeSignedInt24();
        }
        if (precision == 32) {
            return makeSignedInt32();
        }

        unsupported("unsupported precision " + precision);
        return makeSignedInt16();
    }

    final protected int sizeOfVdc() {
        if (VDCTypeCommand.getType().equals(VDCTypeCommand.Type.INTEGER)) {
            int precision = VDCIntegerPrecisionCommand.getPrecision();
            return (precision / 8);
        }

        if (VDCTypeCommand.getType().equals(VDCTypeCommand.Type.REAL)) {
            VDCRealPrecisionCommand.Type precision = VDCRealPrecisionCommand
                    .getPrecision();
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FIXED_POINT_32BIT)) {
                return sizeOfFixedPoint32();
            }
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FIXED_POINT_64BIT)) {
                return sizeOfFixedPoint64();
            }
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FLOATING_POINT_32BIT)) {
                return sizeOfFloatingPoint32();
            }
            if (precision
                    .equals(VDCRealPrecisionCommand.Type.FLOATING_POINT_64BIT)) {
                return sizeOfFloatingPoint64();
            }
        }
        return 1;
    }

    final protected double makeVc() {
        return makeInt();
    }

    final protected double makeReal() {
        RealPrecisionCommand.Precision precision = RealPrecisionCommand
                .getPrecision();
        if (precision.equals(RealPrecisionCommand.Precision.FIXED_32)) {
            return makeFixedPoint32();
        }
        if (precision.equals(RealPrecisionCommand.Precision.FIXED_64)) {
            return makeFixedPoint64();
        }
        if (precision.equals(RealPrecisionCommand.Precision.FLOATING_32)) {
            return makeFloatingPoint32();
        }
        if (precision.equals(RealPrecisionCommand.Precision.FLOATING_64)) {
            return makeFloatingPoint64();
        }

        unsupported("unsupported real precision " + precision);
        // return default
        return makeFixedPoint32();
    }

    final protected double makeFixedPoint() {
        RealPrecisionCommand.Precision precision = RealPrecisionCommand
                .getPrecision();
        if (precision.equals(RealPrecisionCommand.Precision.FIXED_32)) {
            return makeFixedPoint32();
        }
        if (precision.equals(RealPrecisionCommand.Precision.FIXED_64)) {
            return makeFixedPoint64();
        }
        unsupported("unsupported real precision " + precision);
        // return default
        return makeFixedPoint32();
    }

    final protected double makeFloatingPoint() {
        RealPrecisionCommand.Precision precision = RealPrecisionCommand
                .getPrecision();
        if (precision.equals(RealPrecisionCommand.Precision.FLOATING_32)) {
            return makeFloatingPoint32();
        }
        if (precision.equals(RealPrecisionCommand.Precision.FLOATING_64)) {
            return makeFloatingPoint64();
        }
        return makeFloatingPoint32();
    }

    private double makeFixedPoint32() {
        double wholePart = makeSignedInt16();
        double fractionPart = makeUInt16();

        return wholePart + (fractionPart / (2 << 15));
    }

    private int sizeOfFixedPoint32() {
        return 2 + 2;
    }

    private double makeFixedPoint64() {
        double wholePart = makeSignedInt32();
        double fractionPart = makeUInt32();

        return wholePart + (fractionPart / (2 << 31));
    }

    private int sizeOfFixedPoint64() {
        return 4 + 4;
    }

    protected final double makeFloatingPoint32() {
        skipBits();
        int bits = 0;
        for (int i = 0; i < 4; i++) {
            bits = (bits << 8) | makeChar();
        }
        return Float.intBitsToFloat(bits);
    }

    private int sizeOfFloatingPoint32() {
        return 2 * 2;
    }

    private double makeFloatingPoint64() {
        skipBits();
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | makeChar();
        }
        return Double.longBitsToDouble(bits);
    }

    private int sizeOfFloatingPoint64() {
        return 2 * 4;
    }

    final protected int makeEnum() {
        return makeSignedInt16();
    }

    final protected void BytesFromEnum(int from, byte[] to) {
        BytesFromSignedInt16(from, to);
    }

    final protected int sizeOfEnum() {
        return 2;
    }

    final protected Point makePoint() {
        return new Point(makeVdc(), makeVdc());
    }

    final protected int sizeOfPoint() {
        return 2 * sizeOfVdc();
    }

    final protected int makeColorIndex() {
        int precision = ColourIndexPrecisionCommand.getPrecision();
        return makeUInt(precision);
    }

    final protected int makeColorIndex(int precision) {
        return makeUInt(precision);
    }

    final protected int makeDirectColor() {
        int precision = ColourPrecisionCommand.getPrecision();
        int[] mins = ColourValueExtentCommand.getMinimumColorValueRGB();
        int[] maxs = ColourValueExtentCommand.getMaximumColorValueRGB();
        int[] rgba = {
                255, 255, 255, 255
        };
        for (int i = 0; i < rgba.length && i < numArgs; i++) {
            float min = i >= mins.length ? mins[0] : mins[i];
            float max = i >= maxs.length ? maxs[0] : maxs[i];
            rgba[i] = (int) (255 * (makeUInt(precision) - min) / (max - min));
        }
        return Color.argb(rgba[3], rgba[0], rgba[1], rgba[2]);
    }

    final protected int sizeOfDirectColor() {
        int precision = ColourPrecisionCommand.getPrecision();
        return 3 * precision / 8;
    }

    /**
     * Clamp the given value between the given minimum and maximum
     * @param r The value to clamp
     * @param min The minimum value
     * @param max The maximum value
     * @return The clamped value
     */
    private int clamp(int r, int min, int max) {
        return Math.max(Math.min(r, max), min);
    }

    final protected double makeFloat32(int i) {
        skipBits();
        int sign = this.args[2 * i] & (1 << 16);
        int exponent = (this.args[2 * i] >> 8) & 255;
        int fraction = ((this.args[2 * i] & 127) << 16) | this.args[2 * i + 1];

        // only base 10 supported
        return Math.pow(1, sign) * fraction * Math.pow(10, exponent);
    }

    final protected double makeSizeSpecification(
            SpecificationMode specificationMode) {
        if (specificationMode.equals(SpecificationMode.ABSOLUTE)) {
            return makeVdc();
        }
        return makeReal();
    }

    final protected SDR makeSDR() {
        SDR ret = new SDR();
        int sdrLength = getStringCount();
        int startPos = this.currentArg;
        while (this.currentArg < (startPos + sdrLength)) {
            SDR.DataType dataType = SDR.DataType.get(makeIndex());
            int dataCount = makeInt();
            List<Object> data = new ArrayList<>();
            for (int i = 0; i < dataCount; i++) {
                switch (dataType) {
                    case SDR:
                        data.add(makeSDR());
                        break;
                    case CI:
                        data.add(makeColorIndex());
                        break;
                    case CD:
                        data.add(makeDirectColor());
                        break;
                    case N:
                        data.add(makeName());
                        break;
                    case E:
                        data.add(makeEnum());
                        break;
                    case I:
                        data.add(makeInt());
                        break;
                    case RESERVED:
                        // reserved
                        break;
                    case IF8:
                        data.add(makeSignedInt8());
                        break;
                    case IF16:
                        data.add(makeSignedInt16());
                        break;
                    case IF32:
                        data.add(makeSignedInt32());
                        break;
                    case IX:
                        data.add(makeIndex());
                        break;
                    case R:
                        data.add(makeReal());
                        break;
                    case S:
                        data.add(makeString());
                        break;
                    case SF:
                        data.add(makeString());
                        break;
                    case VC:
                        data.add(makeVc());
                        break;
                    case VDC:
                        data.add(makeVdc());
                        break;
                    case CCO:
                        data.add(makeDirectColor());
                        break;
                    case UI8:
                        data.add(makeUInt8());
                        break;
                    case UI32:
                        data.add(makeUInt32());
                        break;
                    case BS:
                        // bit stream? XXX how do we know how many bits to read?
                        break;
                    case CL:
                        // color list? XXX how to read?
                        break;
                    case UI16:
                        data.add(makeUInt16());
                        break;
                    default:
                        unsupported("makeSDR()-unsupported dataTypeIndex "
                                + dataType);
                }
            }
            ret.add(dataType, dataCount, data);
        }
        return ret;
    }

    /**
     * Align on a word boundary
     */
    final protected void alignOnWord() {
        if (this.currentArg >= numArgs) {
            // we reached the end of the array, nothing to skip
            return;
        }

        if (this.currentArg % 2 == 0 && this.currentArgBit > 0) {
            this.currentArgBit = 0;
            this.currentArg += 2;
        } else if (Math.abs(this.currentArg) % 2 == 1) {
            this.currentArgBit = 0;
            this.currentArg++;
        }
    }

    /**
     * Reads one command from the given input stream.
     * @param in Where to read the command from
     * @return The command or {@code null} if the end of stream was found
     * @throws IOException On I/O error
     */
    public static Command read(DataInput in) throws IOException {
        int k;
        try {
            k = in.readUnsignedByte();
            k = (k << 8) | in.readUnsignedByte();
        } catch (EOFException e) {
            return null;
        }

        // the element class
        int ec = k >> 12;
        int eid = (k >> 5) & 127;
        int l = k & 31;
        return readCommand(in, ec, eid, l);
    }

    protected static Command readCommand(DataInput in, int ec, int eid, int l)
            throws IOException {
        switch (CGMElement.getCGMElement(ec)) {

            case DELIMITER_ELEMENTS: // 0
                return readDelimiterElements(in, ec, eid, l);

            case METAFILE_DESCRIPTOR_ELEMENTS: // 1
                return readMetaFileDescriptorElements(in, ec, eid, l);

            case PICTURE_DESCRIPTOR_ELEMENTS: // 2
                return readPictureDescriptorElements(in, ec, eid, l);

            case CONTROL_ELEMENTS: // 3
                return readControlElements(in, ec, eid, l);

            case GRAPHICAL_PRIMITIVE_ELEMENTS: // 4
                return readGraphicalPrimitiveElements(in, ec, eid, l);

            case ATTRIBUTE_ELEMENTS: // 5
                return readAttributeCGMElements(in, ec, eid, l);

            case ESCAPE_ELEMENTS: // 6
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case EXTERNAL_ELEMENTS: // 7
                return readExternalElements(in, ec, eid, l);

            case SEGMENT_ELEMENTS: // 8
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case APPLICATION_STRUCTURE_ELEMENTS: // 9
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            default:
                if (BuildConfig.DEBUG && !(10 <= ec && ec <= 15))
                    throw new AssertionError("unsupported element class");
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);
        }
    }

    // Class: 0
    private static Command readDelimiterElements(DataInput in, int ec, int eid,
            int l)
            throws IOException {
        // Delimiter elements
        switch (DelimiterCGMElement.getElement(eid)) {

            // 0, 0
            case NO_OP:
                return new NoOpCommand(ec, eid, l, in);

            // 0, 1
            case BEGIN_METAFILE:
                return new BeginMetafile(ec, eid, l, in);

            // 0, 2
            case END_METAFILE:
                return new EndMetafile(ec, eid, l, in);

            // 0, 3
            case BEGIN_PICTURE:
                return new BeginPictureCommand(ec, eid, l, in);

            // 0, 4
            case BEGIN_PICTURE_BODY:
                return new BeginPictureBodyCommand(ec, eid, l, in);

            // 0, 5
            case END_PICTURE:
                return new EndPictureCommand(ec, eid, l, in);

            // 0, 6
            case BEGIN_SEGMENT:
                // 0, 7
            case END_SEGMENT:
                // 0, 8
            case BEGIN_FIGURE:
                // 0, 9
            case END_FIGURE:
                // 0, 13
            case BEGIN_PROTECTION_REGION:
                // 0, 14
            case END_PROTECTION_REGION:
                // 0, 15
            case BEGIN_COMPOUND_LINE:
                // 0, 16
            case END_COMPOUND_LINE:
                // 0, 17
            case BEGIN_COMPOUND_TEXT_PATH:
                // 0, 18
            case END_COMPOUND_TEXT_PATH:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            // 0, 19
            case BEGIN_TILE_ARRAY:
                return new BeginTileArrayCommand(ec, eid, l, in);

            // 0, 20
            case END_TILE_ARRAY:
                return new EndTileArrayCommand(ec, eid, l, in);

            // 0, 21
            case BEGIN_APPLICATION_STRUCTURE:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            // 0, 22
            case BEGIN_APPLICATION_STRUCTURE_BODY:
                return new BeginApplicationStructureBodyCommand(ec, eid, l, in);

            // 0, 23
            case END_APPLICATION_STRUCTURE:
                return new EndApplicationStructureCommand(ec, eid, l, in);

            default:
                if (BuildConfig.DEBUG && !false)
                    throw new AssertionError("unsupported element ID=" + eid);
                return new Command(ec, eid, l, in);
        }
    }

    // Class: 1
    private static Command readMetaFileDescriptorElements(DataInput in, int ec,
            int eid, int l) throws IOException {
        switch (MetafileDescriptorCGMElement.getElement(eid)) {

            case METAFILE_VERSION: // 1
                return new MetafileVersionCommand(ec, eid, l, in);

            case METAFILE_DESCRIPTION: // 2
                return new MetafileDescriptionCommand(ec, eid, l, in);

            case VDC_TYPE: // 3
                return new VDCTypeCommand(ec, eid, l, in);

            case INTEGER_PRECISION: // 4
                return new IntegerPrecisionCommand(ec, eid, l, in);

            case REAL_PRECISION: // 5
                return new RealPrecisionCommand(ec, eid, l, in);

            case INDEX_PRECISION: // 6
                return new IndexPrecisionCommand(ec, eid, l, in);

            case COLOUR_PRECISION: // 7
                return new ColourPrecisionCommand(ec, eid, l, in);

            case COLOUR_INDEX_PRECISION: // 8
                return new ColourIndexPrecisionCommand(ec, eid, l, in);

            case MAXIMUM_COLOUR_INDEX: // 9
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case COLOUR_VALUE_EXTENT: // 10
                return new ColourValueExtentCommand(ec, eid, l, in);

            case METAFILE_ELEMENT_LIST: // 11
                return new MetafileElementListCommand(ec, eid, l, in);

            case METAFILE_DEFAULTS_REPLACEMENT: // 12
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case FONT_LIST: // 13
                return new FontListCommand(ec, eid, l, in);

            case CHARACTER_SET_LIST: // 14
                return new CharacterSetListCommand(ec, eid, l, in);

            case CHARACTER_CODING_ANNOUNCER: // 15
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case NAME_PRECISION: // 16
                return new NamePrecisionCommand(ec, eid, l, in);

            case MAXIMUM_VDC_EXTENT: // 17
            case SEGMENT_PRIORITY_EXTENT: // 18
            case COLOUR_MODEL: // 19
            case COLOUR_CALIBRATION: // 20
            case FONT_PROPERTIES: // 21
            case GLYPH_MAPPING: // 22
            case SYMBOL_LIBRARY_LIST: // 23
            case PICTURE_DIRECTORY: // 24
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            default:
                if (BuildConfig.DEBUG && !false)
                    throw new AssertionError("unsupported element ID=" + eid);
                return new Command(ec, eid, l, in);
        }
    }

    // Class: 2
    private static Command readPictureDescriptorElements(DataInput in, int ec,
            int eid, int l) throws IOException {
        switch (PictureDescriptorCGMElement.getElement(eid)) {
            // 2, 1
            case SCALING_MODE:
                return new ScalingModeCommand(ec, eid, l, in);

            // 2, 2
            case COLOUR_SELECTION_MODE:
                return new ColourSelectionModeCommand(ec, eid, l, in);

            // 2, 3
            case LINE_WIDTH_SPECIFICATION_MODE:
                return new LineWidthSpecificationModeCommand(ec, eid, l, in);

            // 2, 4
            case MARKER_SIZE_SPECIFICATION_MODE:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            // 2, 5
            case EDGE_WIDTH_SPECIFICATION_MODE:
                return new EdgeWidthSpecificationModeCommand(ec, eid, l, in);

            // 2, 6
            case VDC_EXTENT:
                return new VDCExtentCommand(ec, eid, l, in);

            // 2, 7
            case BACKGROUND_COLOUR:
                return new BackgroundColourCommand(ec, eid, l, in);

            // 2, 8
            case DEVICE_VIEWPORT:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            // 2, 9
            case DEVICE_VIEWPORT_SPECIFICATION_MODE:
                // 2, 10
            case DEVICE_VIEWPORT_MAPPING:
                // 2, 11
            case LINE_REPRESENTATION:
                // 2, 12
            case MARKER_REPRESENTATION:
                // 2, 13
            case TEXT_REPRESENTATION:
                // 2, 14
            case FILL_REPRESENTATION:
                // 2, 15:
            case EDGE_REPRESENTATION:
                // 2, 16
            case INTERIOR_STYLE_SPECIFICATION_MODE:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            // 2, 17
            case LINE_AND_EDGE_TYPE_DEFINITION:
                return new LineAndEdgeTypeDefinitionCommand(ec, eid, l, in);

            // 2, 18
            case HATCH_STYLE_DEFINITION:
                // 2, 19
            case GEOMETRIC_PATTERN_DEFINITION:
                // 2, 20
            case APPLICATION_STRUCTURE_DIRECTORY:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            default:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);
        }
    }

    // Class: 3
    private static Command readControlElements(DataInput in, int ec, int eid,
            int l) throws IOException {
        switch (ControlCGMElement.getElement(eid)) {
            case VDC_INTEGER_PRECISION:
                return new VDCIntegerPrecisionCommand(ec, eid, l, in);
            case VDC_REAL_PRECISION:
                return new VDCRealPrecisionCommand(ec, eid, l, in);
            case CLIP_RECTANGLE:
            case CLIP_INDICATOR:
            default:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);
        }
    }

    // Class: 4
    private static Command readGraphicalPrimitiveElements(DataInput in, int ec,
            int eid, int l) throws IOException {
        switch (GraphicalPrimitiveCGMElement.getElement(eid)) {

            case POLYLINE: // 1
                return new PolylineCommand(ec, eid, l, in);

            case DISJOINT_POLYLINE: // 2
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case POLYMARKER: // 3
                return new PolyMarkerCommand(ec, eid, l, in);

            case TEXT: // 4
                return new TextCommand(ec, eid, l, in);

            case RESTRICTED_TEXT: // 5
            case APPEND_TEXT: // 6
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case POLYGON: // 7
                return new PolygonCommand(ec, eid, l, in);

            case POLYGON_SET: // 8
                return new PolygonSetCommand(ec, eid, l, in);

            case CELL_ARRAY: // 9
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case GENERALIZED_DRAWING_PRIMITIVE: // 10
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case RECTANGLE: // 11
                return new RectangleCommand(ec, eid, l, in);

            case CIRCLE: // 12
                return new CircleCommand(ec, eid, l, in);

            case CIRCULAR_ARC_3_POINT: // 13
            case CIRCULAR_ARC_3_POINT_CLOSE: // 14
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case CIRCULAR_ARC_CENTRE: // 15
                return new CircularArcCentreCommand(ec, eid, l, in);

            case CIRCULAR_ARC_CENTRE_CLOSE: // 16
                return new CircularArcCentreCloseCommand(ec, eid, l, in);

            case ELLIPSE: // 17
                return new EllipseCommand(ec, eid, l, in);

            case ELLIPTICAL_ARC: // 18
                return new EllipticalArcCommand(ec, eid, l, in);

            case ELLIPTICAL_ARC_CLOSE: // 19
                return new EllipticalArcCloseCommand(ec, eid, l, in);

            case CIRCULAR_ARC_CENTRE_REVERSED: // 20
            case CONNECTING_EDGE: // 21
            case HYPERBOLIC_ARC: // 22
            case PARABOLIC_ARC: // 23
            case NON_UNIFORM_B_SPLINE: // 24
            case NON_UNIFORM_RATIONAL_B_SPLINE: // 25
            case POLYBEZIER: // 26
            case POLYSYMBOL: // 27
            case BITONAL_TILE: // 28
            case TILE: // 29
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);
            default:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);
        }
    }

    // Class: 5
    private static Command readAttributeCGMElements(DataInput in, int ec,
            int eid, int l) throws IOException {
        switch (AttributeCGMElement.getElement(eid)) {
            case LINE_BUNDLE_INDEX: // 1
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case LINE_TYPE: // 2
                return new LineTypeCommand(ec, eid, l, in);

            case LINE_WIDTH: // 3
                return new LineWidthCommand(ec, eid, l, in);

            case LINE_COLOUR: // 4
                return new LineColourCommand(ec, eid, l, in);

            case MARKER_BUNDLE_INDEX: // 5
            case MARKER_TYPE: // 6
            case MARKER_SIZE: // 7
            case MARKER_COLOUR: // 8
            case TEXT_BUNDLE_INDEX: // 9:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case TEXT_FONT_INDEX: // 10
                return new TextFontIndexCommand(ec, eid, l, in);

            case TEXT_PRECISION: // 11
                return new TextPrecisionCommand(ec, eid, l, in);

            case CHARACTER_EXPANSION_FACTOR: // 12
                return new CharacterExpansionFactorCommand(ec, eid, l, in);

            case CHARACTER_SPACING: // 13
                return new CharacterSpacingCommand(ec, eid, l, in);

            case TEXT_COLOUR: // 14
                return new TextColourCommand(ec, eid, l, in);

            case CHARACTER_HEIGHT: // 15
                return new CharacterHeightCommand(ec, eid, l, in);

            case CHARACTER_ORIENTATION: // 16
                return new CharacterOrientationCommand(ec, eid, l, in);

            case TEXT_PATH: // 17
                return new TextPathCommand(ec, eid, l, in);

            case TEXT_ALIGNMENT: // 18
                return new TextAlignmentCommand(ec, eid, l, in);

            case CHARACTER_SET_INDEX: // 19
            case ALTERNATE_CHARACTER_SET_INDEX: // 20
            case FILL_BUNDLE_INDEX: // 21
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case INTERIOR_STYLE: // 22
                return new InteriorStyleCommand(ec, eid, l, in);

            case FILL_COLOUR: // 23
                return new FillColourCommand(ec, eid, l, in);

            case HATCH_INDEX: // 24
                return new HatchIndexCommand(ec, eid, l, in);

            case PATTERN_INDEX: // 25
            case EDGE_BUNDLE_INDEX: // 26
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);

            case EDGE_TYPE: // 27
                return new EdgeTypeCommand(ec, eid, l, in);

            case EDGE_WIDTH: // 28
                return new EdgeWidthCommand(ec, eid, l, in);

            case EDGE_COLOUR: // 29
                return new EdgeColourCommand(ec, eid, l, in);

            case EDGE_VISIBILITY: // 30
                return new EdgeVisibilityCommand(ec, eid, l, in);

            case FILL_REFERENCE_POINT: // 31
            case PATTERN_TABLE: // 32
            case PATTERN_SIZE: // 33
            case COLOUR_TABLE: // 34
            case ASPECT_SOURCE_FLAGS: // 35
            case PICK_IDENTIFIER: // 36
            case LINE_CAP: // 37
            case LINE_JOIN: // 38
            case LINE_TYPE_CONTINUATION: // 39
            case LINE_TYPE_INITIAL_OFFSET: // 40
            case TEXT_SCORE_TYPE: // 41
            case RESTRICTED_TEXT_TYPE: // 42
            case INTERPOLATED_INTERIOR: // 43
            case EDGE_CAP: // 44
            case EDGE_JOIN: // 45
            case EDGE_TYPE_CONTINUATION: // 46
            case EDGE_TYPE_INITIAL_OFFSET: // 47
            case SYMBOL_LIBRARY_INDEX: // 48
            case SYMBOL_COLOUR: // 49
            case SYMBOL_SIZE: // 50
            case SYMBOL_ORIENTATION: // 51
            default:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);
        }
    }

    // Class: 7
    private static Command readExternalElements(DataInput in, int ec, int eid,
            int l) throws IOException {
        switch (ExternalCGMElement.getElement(eid)) {
            case MESSAGE: // 1
            case APPLICATION_DATA: // 2
            default:
                unsupported(ec, eid);
                return new Command(ec, eid, l, in);
        }
    }

    private static String LogString(int ec, int eid, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(CGMElement.getCGMElement(ec)).append(" ");
        sb.append(CGMElement.getElement(ec, eid)).append(" ");
        sb.append(message);
        return sb.toString();
    }

    private static void unsupported(int ec, int eid) {
        if (ec == 0 && eid == 0)
            // 0, 0 is NO-OP
            return;

        Log.i(TAG, LogString(ec, eid, "Is unsupported in this profile"));
    }

    protected final void info(String message) {
        Log.i(TAG,
                LogString(this.elementClass, this.elementID, message + " "
                        + this));
    }

    protected final void unsupported(String message) {
        Log.i(TAG,
                LogString(this.elementClass, this.elementID, message + " "
                        + this));
    }

    protected final void unimplemented(String message) {
        Log.i(TAG,
                LogString(this.elementClass, this.elementID, message + " "
                        + this));
    }

    /**
     * Returns the element class for this command
     * @return An integer representing the class
     */
    public int getElementClass() {
        return this.elementClass;
    }

    /**
     * Returns the element ID for this command
     * @return An integer representing the identifier
     */
    public int getelementID() {
        return this.elementID;
    }
}
