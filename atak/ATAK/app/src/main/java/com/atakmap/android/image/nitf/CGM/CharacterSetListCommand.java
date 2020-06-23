
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;
import java.util.Map;

/**
 *
 */
public class CharacterSetListCommand extends Command {
    enum Type {
        _94_CHAR_G_SET,
        _96_CHAR_G_SET,
        _94_CHAR_MBYTE_G_SET,
        _96_CHAR_MBYTE_G_SET,
        COMPLETE_CODE
    }

    private Map<Type, String> characterSets;

    public CharacterSetListCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        // Must be called to properly increment reader
        makeEnum();

        /**this.characterSets = new HashMap<Type, String>();
        
        while (this.currentArg < this.args.length) {
        
            Type type = Type._94_CHAR_G_SET;
            switch (typ) {
                        case 0: // 94 character G set
                            type = Type._94_CHAR_G_SET;
                            break;
                        case 1: // 96 character G set
                            type = Type._96_CHAR_G_SET;
                            break;
                        case 2: // 94 character multibyte G set
                            type = Type._94_CHAR_MBYTE_G_SET;
                            break;
                        case 3: // 96 character multibyte G set
                            type = Type._96_CHAR_MBYTE_G_SET;
                            break;
                        case 4:
                            type = Type.COMPLETE_CODE;
                            break;
                        default:
                            // TODO: which default to use?
                            type = Type.COMPLETE_CODE;
                            unsupported("unsupported character set type "+typ);
                      }
        */

        makeFixedString();
        /*this.characterSets.put(type, characterSetDesignation);
        
        if (characterSetDesignation.length() > 2) {
            int c = characterSetDesignation.charAt(0);
            if (c == 27) {
                // 27 == ESC
                c = characterSetDesignation.charAt(1);
                if (c == 22) {
                    int revNumber = characterSetDesignation.charAt(2);
                }
            }
        }
        }*/

        //unimplemented("CharacterSetList");

        // make sure all the arguments were read
        //assert (this.currentArg == this.args.length);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CharacterSetList ");

        /*for (Type type: this.characterSets.keySet()) {
            sb.append("[").append(type).append(",").append(this.characterSets.get(type)).append("]");
        }*/

        return sb.toString();
    }
}
