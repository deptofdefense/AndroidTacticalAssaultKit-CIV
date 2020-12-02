package android.content.res;

import java.io.InputStream;
import java.util.ArrayList;

public class Resources {
    private ArrayList<String> resources = new ArrayList<>();

    public AssetManager getAssets() {
        return new AssetManager();
    }

    public InputStream openRawResource(int id) {
        if(id < 1 || id > resources.size())
            throw new IllegalArgumentException("Resource ID " + id + " not known");
        String resource = resources.get(id-1);
        return Resources.class.getResourceAsStream(resource);
    }

    public synchronized int getIdentifier(String name, String type, String packageName) {
        String resource = "/resources/" + type + "/" + name;
        for(int i = 0; i < resources.size(); i++) {
            if(resource.equals(resources.get(i)))
                return i;
        }
        resources.add(resource);
        return resources.size();
    }
}
