
package com.atakmap.android.cotselector;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import androidx.annotation.NonNull;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.List;

class a2525ArrayAdapter extends ArrayAdapter<String> {

    private final CustomListView clv;
    private final Context context;
    private final List<String> items;
    public final String requires;
    public final String prev;

    private static String _currentType = "undefined";

    a2525ArrayAdapter(Context context, int textViewResourceId,
            CustomListView clv,
            ArrayList<String> objects, String requires, String prev) {
        super(context, textViewResourceId, objects);
        this.context = context;
        this.clv = clv;
        this.items = objects;
        this.requires = requires;
        this.prev = prev;
    }

    /**
     * Returns the previous type selected.
     * @return the previous type.
     */
    public String getPrev() {
        return prev;
    }

    public void setType(String type) {
        _currentType = type;
    }

    private boolean compareType(String type) {
        if (FileSystemUtils.isEmpty(_currentType))
            return false;
        String currentType = _currentType.substring(0,
                _currentType.indexOf("-"));
        String compareType = type.substring(0, type.indexOf("-"));
        return currentType.startsWith(compareType);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView,
            @NonNull ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater li = LayoutInflater.from(context);
            view = li.inflate(R.layout.a2525listitem, null, true);
        }

        String item = items.get(position);

        if (item != null) {
            String desc;
            final String[] split = item.split("/", 2);
            if (split.length == 2) {
                desc = split[0];
                final String affil = split[1].substring(1, 2);
                final String nextLevel = "s_" + split[1].substring(2);

                final Button b = view.findViewById(R.id.button1);

                SpannableString spanString = new SpannableString(desc);
                if (!_currentType.equals("undefined")
                        && (_currentType + ".png").equals(nextLevel)) {
                    spanString = new SpannableString("*" + desc + "*");
                }
                if (!_currentType.equals("undefined")
                        && compareType(nextLevel)) {
                    spanString.setSpan(new StyleSpan(Typeface.BOLD), 0,
                            spanString.length(), 0);
                }
                b.setText(spanString);
                OnClickListener onClickListener = new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clv.sendCoTFrom2525(split[1]);
                    }

                };
                if (!affil.equals("_")) {
                    onClickListener = new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            changeAffil(affil);
                            clv.goDeeper(nextLevel); //if it's the root level, we can always go deeper
                        }
                    };
                }
                b.setOnClickListener(onClickListener);
                // holdingImgs = true;
                // Drawable[] temp = b.getCompoundDrawables();
                // if(b.getCompoundDrawables()[0] == null){
                new RqstLoadImgThread(split[1], b).start();
                // }
                // }

                ImageButton ib = view.findViewById(R.id.button2);

                if (clv.canGoDeeper(nextLevel)) {
                    ib.setVisibility(View.VISIBLE);// since it could be being adapted, make sure
                                                   // it's visible
                    ib.setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            b.setCompoundDrawables(null, null, null, null);
                            clv.goDeeper(nextLevel);
                            if (!affil.equals("_")) {
                                changeAffil(affil);
                            }
                        }

                    });
                } else {
                    ib.setVisibility(View.INVISIBLE);
                }

            }
        }
        return view;
    }

    private void changeAffil(final String affil) {
        int result = CSUITConstants.COT_AFFIL_UNKNOWN;
        switch (affil) {
            case "f":
                result = CSUITConstants.COT_AFFIL_FRIEND;
                break;
            case "n":
                result = CSUITConstants.COT_AFFIL_NEUTRAL;
                break;
            case "h":
                result = CSUITConstants.COT_AFFIL_HOSTILE;
                break;
        }
        clv.setSelectedAffil(result);
    }

    // public void setSelectedAffil(int i){
    // selectedAffil = i;
    // }

    private class RqstLoadImgThread extends Thread {

        private final String fn;
        private final Button b;

        RqstLoadImgThread(String fn, Button b) {
            this.fn = fn;
            this.b = b;
        }

        @Override
        public void run() {
            final BitmapDrawable bmd = clv.requestLoadIcon(fn);

            if (bmd != null) {
                Activity a = (Activity) context;
                a.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        b.setCompoundDrawablesWithIntrinsicBounds(bmd, null,
                                null, null);
                    }

                });
            }

        }
    }

}
