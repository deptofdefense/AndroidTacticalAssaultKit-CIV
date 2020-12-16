
package com.atakmap.comms.app;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.TabHost;

import com.atakmap.android.metrics.activity.MetricTabActivity;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;

@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class CotManagerTabActivity extends MetricTabActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TabHost tabHost = getTabHost();
        Intent intent = getIntent();
        Resources res = getResources();

        Intent objectsIntent = new Intent(this, CotObjectsListActivity.class);
        Parcelable[] objects = intent.getParcelableArrayExtra("objects");
        objectsIntent.putExtra("objects", objects);
        TabHost.TabSpec objectsTabSpec = tabHost.newTabSpec("objects");
        objectsTabSpec.setIndicator("Objects");
        objectsTabSpec.setContent(objectsIntent);
        tabHost.addTab(objectsTabSpec);

        // ArrayList<Parcelable> initialInputs =
        // intent.getParcelableArrayListExtra("initialInputs");
        Intent inputsIntent = new Intent(this, CotInputsListActivity.class);
        // inputsIntent.putParcelableArrayListExtra("initialInputs", initialInputs);
        TabHost.TabSpec inputsTabSpec = tabHost.newTabSpec("inputs");
        inputsTabSpec.setIndicator("Inputs",
                res.getDrawable(R.drawable.ic_arrow_down));
        inputsTabSpec.setContent(inputsIntent);
        tabHost.addTab(inputsTabSpec);

        // ArrayList<Parcelable> initialOutputs =
        // intent.getParcelableArrayListExtra("initialInputs");
        Intent outputsIntent = new Intent(this, CotOutputsListActivity.class);
        // outputsIntent.putParcelableArrayListExtra("initialOutputs", initialOutputs);
        TabHost.TabSpec outputsTabSpec = tabHost.newTabSpec("outputs");
        outputsTabSpec.setIndicator("Outputs",
                res.getDrawable(R.drawable.ic_arrow_up));
        outputsTabSpec.setContent(outputsIntent);
        tabHost.addTab(outputsTabSpec);

    }
}
