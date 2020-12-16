
package com.atakmap.android.icons;

import android.content.Context;
import android.preference.PreferenceManager;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.SqliteMapDataRef;

import java.util.List;

import androidx.test.InstrumentationRegistry;

import static org.junit.Assert.assertNotNull;

public class IconModuleUtils {
    public static String randomIconUri() {
        Context appContext = InstrumentationRegistry.getTargetContext();

        UserIconDatabase db = IconsMapAdapter.initializeUserIconDB(appContext,
                PreferenceManager.getDefaultSharedPreferences(appContext));
        assertNotNull(db);
        UserIcon icon = IconModuleUtils.randomIcon(db);
        assertNotNull(icon);
        return IconModuleUtils.getIconUri(appContext, db, icon);
    }

    public static UserIcon randomIcon(UserIconDatabase db) {
        assertNotNull(db);

        int iconCount = 0;
        for (UserIconSet iconSet : db.getIconSets(true, false)) {
            assertNotNull(iconSet);
            List<UserIcon> icons = iconSet.getIcons();
            assertNotNull(icons);
            iconCount += icons.size();
        }

        int selectedIcon = RandomUtils.rng().nextInt(iconCount);
        for (UserIconSet iconSet : db.getIconSets(true, false)) {
            List<UserIcon> icons = iconSet.getIcons();
            if (icons.size() <= selectedIcon) {
                selectedIcon -= icons.size();
                continue;
            } else if (icons.size() > selectedIcon) {
                return icons.get(selectedIcon);
            }
        }

        throw new IllegalStateException();
    }

    public static String getIconUri(Context ctx, UserIconDatabase db,
            UserIcon icon) {
        String path = UserIcon.GetIconsetPath(icon.getIconsetUid(),
                icon.getGroup(), icon.getFileName());
        assertNotNull(path);
        String optimizedQuery = UserIcon.GetIconBitmapQueryFromIconsetPath(path,
                ctx);
        MapDataRef iconRef = new SqliteMapDataRef(db.getDatabaseName(),
                optimizedQuery);
        return iconRef.toUri();
    }
}
