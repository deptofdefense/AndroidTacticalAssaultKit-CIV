
package com.atakmap.comms.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.SimpleAdapter;

import com.atakmap.android.metrics.activity.MetricListActivity;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.HashMap;

@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class CotObjectsListActivity extends MetricListActivity {
    // private ObjectProperties[] _props;
    private SimpleAdapter _adapter;
    private ArrayList<HashMap<String, String>> _listData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Parcelable[] objects = intent.getParcelableArrayExtra("objects");

        _listData = new ArrayList<>();

        if (objects != null) {
            for (Parcelable p : objects) {
                Bundle b = (Bundle) p;
                String uid = b.getString("uid");
                String callsign = b.getString("type");
                HashMap<String, String> data = new HashMap<>();
                data.put("uid", uid);
                data.put("callsign", callsign);
                _listData.add(data);
            }
        }

        String[] adaptFrom = {
                "uid", "callsign"
        };
        int[] adaptTo = {
                R.id.cot_object_uid, R.id.cot_object_callsign
        };
        _adapter = new SimpleAdapter(this, _listData, R.layout.cot_object_item,
                adaptFrom, adaptTo);

        setListAdapter(_adapter);

    }

}
