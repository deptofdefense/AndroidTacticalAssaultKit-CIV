
package com.atakmap.android.cotselector;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

import com.atakmap.coremap.locale.LocaleUtil;

public class CustomListView extends ListView implements View.OnClickListener {
    private CoTSelector cs;
    private FileIO fio;

    private int selectedAffil = 1;
    private final List<a2525ArrayAdapter> adapterList = new ArrayList<>();

    private ImageButton backB = null;

    public CustomListView(Context context) {
        super(context);
    }

    public void init(final View v, final FileIO fio, final CoTSelector cs) {
        this.cs = cs;
        this.fio = fio;

        fio.setCustomListView(this);

        // this.possibleAffils = possibleAffils;

        // parse the file and get a map of adapters with string lists back e.g. map<req,adapter>
        // where the adapter has the items that have req as a requirement as a string list
        // also, set the image count to the biggest set
        fio.readAndParse2525DataFile();

        // get selected affil and possible affils from bundle
        //

        // set up the buttons

        backB = v.findViewById(R.id.BackB);
        backB.setOnClickListener(this);

    }

    public void setType(final String initCotType) {

        if (initCotType != null) {

            switch (initCotType.charAt(2)) {
                case 'p':
                    selectedAffil = CSUITConstants.COT_AFFIL_PENDING;
                    break;
                case 'u':
                    selectedAffil = CSUITConstants.COT_AFFIL_UNKNOWN;
                    break;
                case 'a':
                    selectedAffil = CSUITConstants.COT_AFFIL_ASSUMED_FRIEND;
                    break;
                case 'f':
                    selectedAffil = CSUITConstants.COT_AFFIL_FRIEND;
                    break;
                case 'n':
                    selectedAffil = CSUITConstants.COT_AFFIL_NEUTRAL;
                    break;
                case 's':
                    selectedAffil = CSUITConstants.COT_AFFIL_SUSPECT;
                    break;
                case 'h':
                    selectedAffil = CSUITConstants.COT_AFFIL_HOSTILE;
                    break;
                case 'j':
                    selectedAffil = CSUITConstants.COT_AFFIL_JOKER;
                    break;
                case 'k':
                    selectedAffil = CSUITConstants.COT_AFFIL_FAKER;
                    break;
                case 'o':
                default:
                    selectedAffil = CSUITConstants.COT_AFFIL_NONE;

            }

            this.handleDataChanged();
            for (a2525ArrayAdapter adptr : adapterList) { //Let everyone know about the change
                adptr.notifyDataSetChanged();
            }

            // start at?

            String s = "#REQ:" + get2525FromCoT(initCotType) + ".png";// "#REQ:ROOT"; // should get
                                                                      // some kind of string from
                                                                      // the bundle here, maybe

            a2525ArrayAdapter temp = findAdapter(s);

            if (temp != null) {
                // we ignore the "#PRE:" part of the getPrevious;
                temp = findAdapter("#REQ:" + temp.getPrev().substring(5));

            } else {
                String tempCot = initCotType;
                while (temp == null) {
                    if (tempCot.length() < 2)
                        temp = findAdapter("#REQ:ROOT");
                    else {
                        tempCot = tempCot.substring(0, tempCot.length() - 2);
                        String t = "#REQ:" + get2525FromCoT(tempCot) + ".png";
                        temp = findAdapter(t);
                    }

                }

            }
            if (temp != null) {
                temp.setType(get2525FromCoT(initCotType));
                setTheAdapter(temp);
                temp.notifyDataSetChanged();
            }

        }
    }

    private a2525ArrayAdapter findAdapter(String req) {
        a2525ArrayAdapter adp = null;
        for (a2525ArrayAdapter adptr : adapterList) {
            if (adptr.requires.equals(req)) {
                adp = adptr;
                break;
            }
        }
        return adp;
    }

    public void add2525ToList(String req, String pre, ArrayList<String> vals) {
        a2525ArrayAdapter newAdapter = new a2525ArrayAdapter(getContext(),
                R.layout.a2525listitem, this,
                vals, req, pre);
        adapterList.add(newAdapter);
    }

    public BitmapDrawable requestLoadIcon(String fn) {
        String replacement;
        switch (selectedAffil) {
            case CSUITConstants.COT_AFFIL_ASSUMED_FRIEND:
            case CSUITConstants.COT_AFFIL_FRIEND:
                replacement = "f";
                break;

            case CSUITConstants.COT_AFFIL_NEUTRAL:
                replacement = "n";
                break;

            case CSUITConstants.COT_AFFIL_SUSPECT:
            case CSUITConstants.COT_AFFIL_JOKER:
            case CSUITConstants.COT_AFFIL_FAKER:
            case CSUITConstants.COT_AFFIL_HOSTILE:
                replacement = "h";
                break;

            case CSUITConstants.COT_AFFIL_UNKNOWN:
            case CSUITConstants.COT_AFFIL_PENDING:
            case CSUITConstants.COT_AFFIL_NONE:
            default:
                replacement = "u";
                break;
        }

        fn = fn.replaceFirst("_", replacement);

        return fio.loadIcon(fn);
    }

    public void setTheAdapter(ListAdapter adapter) {
        // clean the image cache
        super.setAdapter(adapter);
        // oldTitle = context.getTitle();
        // context.setTitle(title);
    }

    @Override
    public void onClick(View arg0) {
        if (arg0.equals(backB)) {
            a2525ArrayAdapter adp = (a2525ArrayAdapter) getAdapter();
            goBack(adp.getPrev());
        }
    }

    private String get2525FromCoT(String cot) {
        if (cot != null && cot.indexOf("a") == 0 && cot.length() > 3) {
            StringBuilder s2525C = new StringBuilder("s_");

            for (int x = 4; x < cot.length(); x += 2) {
                char[] t = {
                        cot.charAt(x)
                };
                String s = new String(t);
                s2525C.append(s.toLowerCase(LocaleUtil.getCurrent()));
                if (x == 4) {
                    s2525C.append("p");
                }
            }
            for (int x = s2525C.length(); x < 15; x++) {
                if (x == 10 && s2525C.charAt(2) == 'g'
                        && s2525C.charAt(4) == 'i') {
                    s2525C.append("h");
                } else {
                    s2525C.append("-");
                }
            }
            return s2525C.toString();
        }

        return "";
    }

    private String getCoTFrom2525(String string) {
        char c = 'o';
        switch (selectedAffil) {
            case CSUITConstants.COT_AFFIL_PENDING:
                c = 'p';
                break;
            case CSUITConstants.COT_AFFIL_UNKNOWN:
                c = 'u';
                break;
            case CSUITConstants.COT_AFFIL_ASSUMED_FRIEND:
                c = 'a';
                break;
            case CSUITConstants.COT_AFFIL_FRIEND:
                c = 'f';
                break;
            case CSUITConstants.COT_AFFIL_NEUTRAL:
                c = 'n';
                break;
            case CSUITConstants.COT_AFFIL_SUSPECT:
                c = 's';
                break;
            case CSUITConstants.COT_AFFIL_HOSTILE:
                c = 'h';
                break;
            case CSUITConstants.COT_AFFIL_JOKER:
                c = 'j';
                break;
            case CSUITConstants.COT_AFFIL_FAKER:
                c = 'k';
                break;
            case CSUITConstants.COT_AFFIL_NONE:
                c = 'o';
            default:
        }

        char[] temp = string.toCharArray();

        String pre = "a-" + c;

        StringBuilder sb = new StringBuilder();

        for (int i = 2; i < temp.length; i++) {
            if (temp[i] == '-') {
                break;
            }
            if (i != 3) {
                sb.append('-');
                sb.append(temp[i]);
            }
        }

        String post = sb.toString().toUpperCase(LocaleUtil.getCurrent());

        return pre + post;
    }

    public void sendCoTFrom2525(String s2525) {
        cs.notifyChanged(getCoTFrom2525(s2525));
    }

    public boolean canGoDeeper(String string) {
        String s = "#REQ:" + string;
        a2525ArrayAdapter adp = findAdapter(s);
        return adp != null;
    }

    public boolean goDeeper(String string) {
        String s = "#REQ:" + string;
        a2525ArrayAdapter adp = findAdapter(s);
        if (adp != null) {
            setTheAdapter(adp);
            return true;
        }
        return false;

    }

    public boolean goBack(final String string) {
        if (string != null) {
            String[] split = string.split(":");
            if (split.length == 2) {
                String str = "#REQ:" + split[1];
                setTheAdapter(findAdapter(str));
                return true;
            }
        }
        return false;
    }

    public void setSelectedAffil(int adffil) {
        selectedAffil = adffil;
    }

}
