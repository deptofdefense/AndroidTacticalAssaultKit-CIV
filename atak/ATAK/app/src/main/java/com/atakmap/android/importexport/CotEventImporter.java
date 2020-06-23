
package com.atakmap.android.importexport;

import android.os.Bundle;

import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.comms.CommsMapComponent.ImportResult;

public interface CotEventImporter extends Importer {
    ImportResult importData(CotEvent cot, Bundle bundle);
}
