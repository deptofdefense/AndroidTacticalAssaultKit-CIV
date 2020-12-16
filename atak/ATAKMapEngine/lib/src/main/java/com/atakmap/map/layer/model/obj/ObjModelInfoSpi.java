package com.atakmap.map.layer.model.obj;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoSpi;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;

public final class ObjModelInfoSpi implements ModelInfoSpi {
    public final static ModelInfoSpi INSTANCE = new ObjModelInfoSpi();
    public final static String TYPE = "OBJ";
    public final static String TAG = "ObjModelInfoSpi";

    private ObjModelInfoSpi() {}

    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public Set<ModelInfo> create(String object) {
        File[] f = new File[1];
        if(!isSupported(object, f))
            return null;
        ModelInfo retval = new ModelInfo();
        retval.uri = f[0].getAbsolutePath();
        retval.name = (new File(object)).getName();
        retval.type = TYPE;

        return Collections.<ModelInfo>singleton(retval);
    }

    @Override
    public boolean isSupported(String uri) {
        return this.isSupported(uri, new File[1]);
    }

    private boolean isSupported(String uri, File[] f) {
        f[0] = new File(uri);
        if(f[0].getName().endsWith(".zip") || f[0].getAbsolutePath().contains(".zip")) {
            try {
                File entry = ObjUtils.findObj(new ZipVirtualFile(f[0]));
                if (entry != null)
                    f[0] = entry;
            } catch(IllegalArgumentException ignored) {}
        } else if(!IOProviderFactory.exists(f[0]))
            return false;
        if(!f[0].getName().toLowerCase(LocaleUtil.getCurrent()).endsWith(".obj"))
            return false;

        Reader reader = null;
        try {
            reader = ObjUtils.open(f[0].getAbsolutePath());

            if (reader == null)
                return false;

            int invalidLines = 0;
            int validLines = 0;
            StringBuilder line;
            int code;
            parseloop: do {
                int c = reader.read();

                switch(c) {
                    case -1 :
                        // EOF
                        break parseloop;
                    case ' ' :
                    case '\n' :
                    case '\t' :
                    case '\r' :
                        // discard white space
                        break;
                    case 'v' :
                        // next character must be space, 't' or 'n'
                        c = reader.read();
                        if((c == -1) || !((c == 't') || (c == ' ') || (c == 'n'))) {
                            invalidLines++;
                            break;
                        }
                        // read the definition
                        line = new StringBuilder();
                        code = ObjUtils.advanceToNewline(reader, line, 1024);
                        if(code == ObjUtils.LIMIT)
                            return false;
                        String regex;
                        switch(c) {
                            case ' ' : // geometry vertex
                                // xyz[w] [rgba]
                                regex = "\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?(\\s+\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?){2,3}((\\s+\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?){3,4})?";
                                break;
                            case 't' : // texture coordinate
                                // three numeric values, one optional
                                regex = "\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?\\s+\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?(\\s+\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?)?";
                                break;
                            case 'n': // vertex normal
                                // three numeric values, none optional
                                regex = "\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?\\s+\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?\\s+\\-?\\d+(\\.\\d+)?(e[\\+\\-]\\d+)?";
                                break;
                            default :
                                throw new IllegalStateException();
                        }
                        if(line.toString().trim().matches(regex))
                            validLines++;
                        else
                            invalidLines++;
                        if(code == ObjUtils.EOF)
                            break parseloop;
                        break;
                    case 'f' :
                        // check valid face
                        // read the definition
                        line = new StringBuilder();
                        code = ObjUtils.advanceToNewline(reader, line, 1024);
                        if(code == ObjUtils.LIMIT)
                            return false;
                        if(line.toString().trim().matches("\\s+\\d+((\\/(\\d+)?)?\\/\\d+)?"))
                            validLines++;
                        else
                            invalidLines++;
                        if(code == ObjUtils.EOF)
                            break parseloop;
                        break;
                    case 'l' :
                        // check valid line definition
                        // read the definition
                        line = new StringBuilder();
                        code = ObjUtils.advanceToNewline(reader, line, 1024);
                        if(code == ObjUtils.LIMIT)
                            return false;
                        if(line.toString().trim().matches("(\\s+\\d+)+"))
                            validLines++;
                        else
                            invalidLines++;
                        if(code == ObjUtils.EOF)
                            break parseloop;

                        break;
                    case '#' :
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.LIMIT)
                            return false;
                        else if(code == ObjUtils.EOF)
                            break parseloop;
                        break;
                    case 'm' :
                        if(!ObjUtils.advanceThroughWord(reader, "tllib"))
                            invalidLines++;
                        else
                            validLines++;
                        // XXX - handle material file
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.LIMIT)
                            return false;
                        else if(code == ObjUtils.EOF)
                            break parseloop;
                        break;
                    case 'g' :   // could still be valid, just skip over this and advance
                    case 'o' :   // could still be valid, just skip over this and advance
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.LIMIT)
                            return false;
                        else if(code == ObjUtils.EOF)
                            break parseloop;
                        break;
                    case 'u' :
                        if(!ObjUtils.advanceThroughWord(reader, "semtl"))
                            invalidLines++;
                        else
                            validLines++;
                        // XXX - handle material file
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.LIMIT)
                            return false;
                        else if(code == ObjUtils.EOF)
                            break parseloop;
                        break;
                    // XXX - others
                    default :
                        invalidLines++;
                        break;
                }
            } while(validLines < 3 && invalidLines == 0);

            //Log.d(TAG, "number of invalid lines: " + uri + " " + invalidLines);
            return (invalidLines == 0);
        } catch(Throwable t) {
            Log.d(TAG, "error reading .obj file: " + uri, t);
            return false;
        } finally {
            if(reader != null)
                try {
                    reader.close();
                } catch(Throwable ignored) {}
        }
    }
}
