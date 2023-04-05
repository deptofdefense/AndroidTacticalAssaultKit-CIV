
package com.atakmap.android.medline;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;

/**
 *
 */
final public class GenerateReadout {

    /**
     * Private constructor to make sure this class is never instantiated.
     */
    private GenerateReadout() {
    }

    /**
     * Function to create the readout for a 9-Line
     * @param v - Instance of MedLineView
     * @param context - Context
     */
    public static void create9LineReadout(final MedLineView v,
            Context context) {

        final Context con = context;
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(con);
        View readout = LayoutInflater.from(con)
                .inflate(R.layout.medline_readout_screen, null);
        StringBuilder sb = new StringBuilder();
        final StringBuilder fullString = new StringBuilder();

        //Line 00 ?
        TextView line00 = readout.findViewById(R.id.line00);
        sb.append(con.getString(R.string.readout_text1));
        sb.append(con.getString(R.string.readout_text4));
        line00.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n\n");
        sb.setLength(0);

        //Line 1 Location
        TextView line01 = readout.findViewById(R.id.line01);
        sb.append("1. ");
        sb.append(v.getLineOneText());
        line01.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 2 - Heading, Offset
        TextView line02 = readout.findViewById(R.id.line02);
        sb.append("2. ");
        sb.append("Call Sign: ").append(v.getCallSign());
        sb.append(" ");
        sb.append("Freq: ").append(v.getFreq());
        line02.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 3 - Patients
        TextView line03 = readout.findViewById(R.id.line03);
        sb.append("3. ");
        sb.append(v.getLineThreeText());
        line03.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 4 - Special Equipment
        TextView line04 = readout.findViewById(R.id.line04);
        sb.append("4. ");
        String lineFour = v.getLineFourText();
        if (lineFour.contains("Other")) {
            sb.append(lineFour.replace(" (Specify)", ": "));
            sb.append(v.getLineFourOther());
        } else {
            sb.append(lineFour);
        }
        line04.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 5 - Patient Type
        TextView line05 = readout.findViewById(R.id.line05);
        sb.append("5. ");
        sb.append(v.getLineFiveText());
        line05.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 6 - LZ Security
        TextView line06 = readout.findViewById(R.id.line06);
        sb.append("6. ");
        sb.append(v.getLineSixText());
        line06.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 7 - Type Mark/Terminal Guidance
        TextView line07 = readout.findViewById(R.id.line07);
        sb.append("7. ");
        String lineSeven = v.getLineSevenText();
        if (lineSeven.contains("Other")) {
            sb.append(v.getLineSevenOther());
        } else {
            sb.append(lineSeven);
        }
        line07.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 8 - Patient Nationality
        TextView line08 = readout.findViewById(R.id.line08);
        sb.append("8. ");
        sb.append(v.getLineEightText());
        line08.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Line 9 - HLZ Terrain
        TextView line09 = readout.findViewById(R.id.line09);
        sb.append("9. ");
        String lineNine = v.getLineNineText();
        if (lineNine.contains("Other")) {
            sb.append(lineNine.replace(" (Specify)", ": "));
            sb.append(v.getLineNineOther());
        } else {
            sb.append(lineNine);
        }
        line09.setText(sb.toString());
        fullString.append(sb);
        fullString.append("\n");
        sb.setLength(0);

        //Build the dialog
        alertBuilder.setTitle(R.string.medevac_brief)
                .setView(readout)
                .setNeutralButton(R.string.copy,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialogInterface, int i) {

                                ATAKUtilities.copyClipboard("readout",
                                        fullString.toString(), true);

                            }
                        })
                .setPositiveButton(R.string.ok, null).show();
    }

}
