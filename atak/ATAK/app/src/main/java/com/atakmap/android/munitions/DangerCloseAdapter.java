
package com.atakmap.android.munitions;

import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.xml.XMLUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * In charge of rendering munitions and explosive tables that can be used for danger rings.
 * This does contain a mechism for overriding the rendered look and feel by a single plugin.
 */
public class DangerCloseAdapter extends BaseAdapter
        implements CustomCreator.CustomCreateListener {

    public static final String DIRNAME = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "fires";

    public static final String ORDNANCE_XML = "ordnance/ordnance_table.xml";

    private static final int ORDNANCE_LAST_ID = 219; //the is the ID of the last weapon in ordnance_table.xml

    /**
     * Allows for a plugin developer to customize or tweak the the display of of an item.
     */
    public interface CustomViewAdapter {
        /**
         * This method is run as part of the the getView method and is guaranteed to the be the
         * last call prior to returning the view.   Note - the view passed in is what is currently
         * saved in the view holder.  If you pass a new view back, please keep this in mind and
         * appropriately cache your views to the original view in the view holder.
         *
         * @param viewHolder the view holder as an opaque object to key into any view management done by the plugin
         * @param v the view as constructed using the standard mechanics within ATAK
         * @param targetUID the target currently associated with the adapter.   This uid may or
         *                  may not be valid when looked up using MapView.getMapItem().
         * @return the modified view.
         */
        public View adapt(ViewHolder viewHolder, View v, String targetUID);
    }

    private final Context _context;
    private final MapView _mapView;
    private final String target;
    private final LayoutInflater mInflater;
    private boolean addMode;
    private boolean removeMode;
    private boolean removeCustomMode;
    private boolean flightsGroupSelected;

    private int[] currList;
    private static Node currNode;
    private static Node flightsNode;
    private static Node flightsParentNode;
    private static Node favoritesNode;
    private static Node customNode;
    private static Node ordnanceNode;
    private final String fromLine;

    private static CustomViewAdapter customViewAdapter;

    public static HashSet<Integer> favorites;
    public static HashSet<Integer> removing;
    private static final ArrayList<Integer> customTBRemoved = new ArrayList<>();

    public static final String TAG = "DangerCloseAdapter";

    public DangerCloseAdapter(Context context, MapView mapView,
            String targetUID) {
        _context = context;
        _mapView = mapView;
        target = targetUID;
        addMode = false;
        removeMode = false;
        removeCustomMode = false;
        fromLine = null;
        parseData(context);
        loadFavorites();
        loadCustoms();

        mInflater = LayoutInflater.from(context);
    }

    public DangerCloseAdapter(Context context, MapView mapView,
            String targetUID, String from) {
        _context = context;
        _mapView = mapView;
        target = targetUID;
        addMode = false;
        removeMode = false;
        removeCustomMode = false;
        fromLine = from;
        parseData(context);
        loadFavorites();
        loadCustoms();

        mInflater = LayoutInflater.from(context);
    }

    boolean isAddMode() {
        return addMode;
    }

    boolean isRemoveMode() {
        return removeMode;
    }

    boolean isRemoveCustomMode() {
        return removeCustomMode;
    }

    String getCurrentNode() {
        if (currNode == null)
            return "";

        String result = currNode.getNodeName();

        if (result.equals("category")) {
            Element e = (Element) currNode;
            result = e.getAttribute("name");
        }

        return result;
    }

    boolean atRoot() {
        if (currNode == null)
            return true;

        return currNode.getNodeName().equals("munitions");
    }

    @Override
    public int getCount() {
        return currList.length;
    }

    @Override
    public Object getItem(int arg0) {
        return currList[arg0];
    }

    @Override
    public long getItemId(int arg0) {
        return arg0;
    }

    @Override
    public View getView(final int position, View convertView,
            ViewGroup parent) {

        ViewHolder holder;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.danger_row, null);
            holder = new ViewHolder();
            holder.checkBox = convertView
                    .findViewById(R.id.dangerCheck);
            holder.addFavorite = convertView
                    .findViewById(R.id.addFavorite);
            holder.removeFavorite = convertView
                    .findViewById(R.id.removeFavorite);
            holder.removeCustom = convertView
                    .findViewById(R.id.removeCustom);
            holder.weaponText = convertView
                    .findViewById(R.id.dangerRow);
            holder.weaponTextSub = convertView
                    .findViewById(R.id.dangerRowSubText);
            holder.standText = convertView
                    .findViewById(R.id.dangerStandingRangeRow);
            holder.activeText = convertView
                    .findViewById(R.id.dangerActiveItems);
            holder.descText = convertView
                    .findViewById(R.id.dangerDesc);
            holder.editCustom = convertView
                    .findViewById(R.id.dangerEdit);
            holder.nextArrow = convertView
                    .findViewById(R.id.dangerNextArrow);

            //checkbox for setting active status
            holder.checkBox
                    .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                        @Override
                        public void onCheckedChanged(CompoundButton button,
                                boolean isChecked) {

                            if (isChecked) {
                                Weapon w = (Weapon) button.getTag();
                                sendRRCreateIntent(w);
                            } else {
                                Weapon w = (Weapon) button.getTag();
                                sendRRDeleteIntent(w);
                            }
                        }
                    });
            //checkbox for adding favorites
            holder.addFavorite
                    .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                boolean isChecked) {
                            if (isChecked) {
                                Weapon w = (Weapon) buttonView.getTag();
                                Log.d(TAG, "adding " + w.id + " to favs");
                                // don't allow addition of flight weapons to favorites
                                if (!flightsGroupSelected) {
                                    addFavorite(w.id);
                                }
                            } else {
                                Weapon w = (Weapon) buttonView.getTag();
                                removeFavorite(w.id);
                            }
                        }
                    });
            //removing favorites checkbox
            holder.removeFavorite
                    .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                boolean isChecked) {
                            if (isChecked) {
                                Weapon w = (Weapon) buttonView.getTag();
                                removeFavorite(w.id);
                                removing.add(w.id);
                            } else {
                                Weapon w = (Weapon) buttonView.getTag();
                                addFavorite(w.id);
                                removing.remove(w.id);
                            }
                        }
                    });

            //removing custom weapon checkbox
            holder.removeCustom
                    .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                boolean isChecked) {
                            if (isChecked) {
                                Weapon w = (Weapon) buttonView.getTag();
                                customTBRemoved.add(w.id);
                            } else {
                                Weapon w = (Weapon) buttonView.getTag();
                                if (customTBRemoved.contains(w.id))
                                    customTBRemoved.remove(Integer
                                            .valueOf(w.id));
                            }
                        }
                    });

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        updateView(holder, position);

        try {
            if (customViewAdapter != null)
                customViewAdapter.adapt(holder, convertView, target);
        } catch (Exception e) {
            Log.e(TAG, "error using the registered customViewAdapter"
                    + customViewAdapter.getClass(), e);
        }

        return convertView;
    }

    /**
     * Update array backing the adapter with the children of the current Node.
     * 
     * @param p the position in the array of integers representing the children of the current node
     * @return true if the node has children and the list has been updated
     */
    boolean descend(int p) {
        if (currNode == null)
            return false;

        if (!currNode.getNodeName().equals("Favorites")
                && !currNode.getNodeName().equals("Custom_Threat_Rings")) {
            NodeList t = null;
            if (currNode.getNodeName().equals("Current_Flights")) {
                flightsParentNode = currNode.getParentNode();
                if (flightsNode != null) {
                    t = flightsNode.getChildNodes();
                }
                flightsGroupSelected = true;
            } else {
                t = currNode.getChildNodes();
            }

            if (t != null) {
                Node temp = t.item(currList[p]);

                if (!temp.getNodeName().equals("weapon")) {
                    if (t.getLength() > 0) {
                        currNode = temp;
                        updateList(currNode);
                        return true;
                    }
                }
            }

        }

        return false;
    }

    private static class Weapon {
        String name = "";
        String description = "";
        int innerRange = 0;
        int outerRange = 0;
        String ricochetfan;
        boolean active = false;
        boolean proneprotected = false;
        int id;
    }

    private Weapon getWeaponData(Node n) {
        Weapon w = new Weapon();
        NamedNodeMap attr = n.getAttributes();

        if (!n.getNodeName().startsWith("#")) {
            if (attr.getLength() < 1)
                w.name = n.getNodeName();
            else {
                for (int i = 0; i < attr.getLength(); i++) {
                    Node a = attr.item(i);

                    String name = a.getNodeName();
                    if (name.equals("name"))
                        w.name = a.getNodeValue();
                    else if (name.equals("description"))
                        w.description = a.getNodeValue();
                    else if (name.equals("prone")
                            && !a.getNodeValue().equals(""))
                        w.innerRange = Integer.parseInt(a.getNodeValue());
                    else if (name.equals("active"))
                        w.active = Boolean.parseBoolean(a.getNodeValue());
                    else if (name.equals("ID"))
                        w.id = Integer.parseInt(a.getNodeValue());
                    else if (name.equals("standing"))
                        w.outerRange = Integer.parseInt(a.getNodeValue());
                    else if (name.equals("proneprotected")
                            && !a.getNodeValue().equals("")) {
                        w.proneprotected = true;
                        w.innerRange = Integer.parseInt(a.getNodeValue());
                    } else if (name.equals("ricochetfan")
                            && !a.getNodeValue().equals(""))
                        w.ricochetfan = a.getNodeValue();
                }
            }

        }

        return w;
    }

    private static Node findNodeWithId(int id, NodeList nl) {
        Node result;
        for (int i = 0; i < nl.getLength(); i++) {
            Node t = nl.item(i);
            if (t.hasChildNodes()) {
                result = findNodeWithId(id, t.getChildNodes());
                if (result != null)
                    return result;
            } else if (t.getNodeType() == Element.ELEMENT_NODE) {
                NamedNodeMap attr = t.getAttributes();

                for (int z = 0; z < attr.getLength(); z++) {
                    Node a = attr.item(z);
                    if (a.getNodeName().equals("ID")) {

                        int temp = Integer.parseInt(a.getNodeValue());
                        if (temp == id) {
                            return t;
                        }
                    }

                }
            }
        }
        return null;
    }

    private void updateView(ViewHolder holder, int position) {
        int current = currList[position];
        boolean isCustom = false;
        Node n = null;

        if (currNode == null)
            return;

        switch (currNode.getNodeName()) {
            case "Favorites":
                Node p = currNode.getParentNode();
                n = findNodeWithId(current, p.getChildNodes());
                if (n == null)
                    n = findNodeWithId(current, customNode.getChildNodes());
                break;
            case "Current_Flights":
                if (flightsNode != null) {
                    n = flightsNode.getChildNodes().item(current);
                }
                break;
            default:
                n = currNode.getChildNodes().item(current);
                break;
        }

        if (currNode.getNodeName().equals("Custom_Threat_Rings")) {
            isCustom = true;
        }

        if (n == null) {
            Log.d(TAG, "Node not found with id " + current);
            return;
        }

        Weapon w = getWeaponData(n);

        holder.weaponTextSub.setVisibility(View.GONE);
        holder.editCustom.setVisibility(View.GONE);

        String name = w.name.replace("_", " ");

        //if the name has an additional part in parenthesis
        //we're going to section it off to improve the UI
        if (name.contains("(") && name.contains(")")) {
            int start = name.indexOf("(");
            //wrap substring without parenthesis
            String nameSubstring = name.substring(start);
            //remove the substring from name
            name = name.replace(" " + nameSubstring, "");
            //strip the substring of the parentheses
            nameSubstring = nameSubstring.substring(1,
                    nameSubstring.length() - 1);

            holder.weaponTextSub.setText(nameSubstring);
            holder.weaponTextSub.setVisibility(View.VISIBLE);
        } else if (name.toLowerCase(LocaleUtil.getCurrent())
                .endsWith("airburst")) {
            name = name.substring(0, name.length() - 8);
            holder.weaponTextSub.setText("airburst");
            holder.weaponTextSub.setVisibility(View.VISIBLE);

        } else if (name.toLowerCase(LocaleUtil.getCurrent())
                .endsWith("contact")) {
            name = name.substring(0, name.length() - 7);
            holder.weaponTextSub.setText("contact");
            holder.weaponTextSub.setVisibility(View.VISIBLE);
        }

        holder.weaponText.setText(name);

        if (!FileSystemUtils.isEmpty(w.description)) {
            holder.descText.setVisibility(View.VISIBLE);
            holder.descText.setText(w.description);
        } else {
            holder.descText.setVisibility(View.GONE);
            holder.descText.setText("");
        }

        if (w.outerRange < 1) { //category
            holder.weaponText.setTextSize(18f);
            holder.weaponText.setTypeface(null, Typeface.NORMAL);
            holder.standText.setVisibility(View.GONE);

            holder.descText.setVisibility(View.GONE);
            holder.checkBox.setVisibility(View.GONE);
            holder.addFavorite.setVisibility(View.GONE);
            holder.removeFavorite.setVisibility(View.GONE);
            holder.removeCustom.setVisibility(View.GONE);
            holder.nextArrow.setVisibility(View.VISIBLE);
            holder.activeText.setVisibility(View.GONE);
            if (!addMode && !removeMode && !removeCustomMode) {
                int active = getActiveGroupSize(n);
                if (active > 0) {
                    holder.activeText.setVisibility(View.VISIBLE);
                    holder.activeText.setText("" + active);
                }
            }
        } else { //weapon
            holder.nextArrow.setVisibility(View.GONE);
            holder.weaponText.setTypeface(null, Typeface.NORMAL);
            holder.weaponText.setTextSize(17f);
            holder.checkBox.setVisibility(View.GONE);
            holder.addFavorite.setVisibility(View.GONE);
            holder.removeFavorite.setVisibility(View.GONE);
            holder.removeCustom.setVisibility(View.GONE);

            //set the correct checkbox based on the current mode
            if (!addMode && !removeMode && !removeCustomMode) {
                holder.checkBox.setVisibility(View.VISIBLE);
                holder.checkBox.setTag(w);
                holder.checkBox.setChecked(w.active);

                if (isCustom) {
                    //edit button
                    holder.editCustom.setVisibility(View.VISIBLE);
                    holder.editCustom
                            .setOnClickListener(new EditCustomOnClickListener(
                                    n, this));
                }
            } else if (addMode) {
                boolean b = favorites.contains(w.id);
                holder.addFavorite.setVisibility(View.VISIBLE);
                holder.addFavorite.setTag(w);
                holder.addFavorite.setChecked(b);
            } else if (removeMode) {
                boolean b = removing.contains(w.id);
                holder.removeFavorite.setVisibility(View.VISIBLE);
                holder.removeFavorite.setTag(w);
                holder.removeFavorite.setChecked(b);
            } else {
                holder.removeCustom.setVisibility(View.VISIBLE);
                holder.removeCustom.setTag(w);
                holder.removeCustom.setChecked(false);
            }

            holder.standText.setVisibility(View.VISIBLE);
            holder.standText.setText("s: " + w.outerRange + "m"); // fix me
            holder.activeText.setVisibility(View.GONE);
        }
    }

    private class EditCustomOnClickListener implements View.OnClickListener {
        final Node node;
        final DangerCloseAdapter adapter;

        EditCustomOnClickListener(final Node n, final DangerCloseAdapter dca) {
            this.node = n;
            this.adapter = dca;
        }

        @Override
        public void onClick(View v) {
            CustomCreator customCreator = new CustomCreator(_context,
                    this.adapter);
            if (!node.getAttributes().getNamedItem("ricochetfan")
                    .getNodeValue().equals(""))
                customCreator.buildCustomMSDDialog(node);
            else
                customCreator.buildCustomREDDialog(node);
        }
    }

    private void setWeaponActiveStatus(Weapon w) {
        Node n = (!currNode.getNodeName().equals("Favorites")) ? currNode
                : currNode.getParentNode();

        Log.d(TAG, "current Node: " + n.getNodeName());
        Element weaponNode = (Element) findNodeWithId(w.id, n.getChildNodes());

        if (weaponNode == null) {
            weaponNode = (Element) findNodeWithId(w.id,
                    customNode.getChildNodes());
        }

        if (weaponNode != null) {
            if (weaponNode.getNodeType() == Element.ELEMENT_NODE) {
                int id = Integer.parseInt(weaponNode.getAttribute("ID"));
                if (w.id == id) {

                    weaponNode.setAttribute("active",
                            Boolean.toString(w.active));

                    Log.d(TAG,
                            "set " + w.id + " active status to "
                                    + w.active);

                    if (weaponNode.getParentNode().getNodeName()
                            .equals("Custom_Threat_Rings")) {
                        setCustomActiveStatus(w);
                    }
                }
            }
        }
    }

    /**
     * Active state must be written to the customs.xml file
     * @param w - the Weapon object
     */
    private void setCustomActiveStatus(Weapon w) {
        String location = FileSystemUtils.getItem(DIRNAME).getPath()
                + File.separator;
        String item = "fires/customs" + ".xml";

        try {
            Log.d(TAG, "load customs: " + location + item);
            File f = new File(location + item);
            if (IOProviderFactory.exists(f)) {
                try {
                    DocumentBuilderFactory docFactory = XMLUtils
                            .getDocumenBuilderFactory();
                    DocumentBuilder docBuilder = docFactory
                            .newDocumentBuilder();

                    Document dom;
                    try (InputStream is = IOProviderFactory.getInputStream(f)) {
                        dom = docBuilder.parse(is);
                    }

                    if (dom != null) {
                        NodeList nodes = dom
                                .getElementsByTagName("Custom_Threat_Rings");

                        if (nodes.getLength() > 0)
                            customNode = nodes.item(0);
                        else
                            Log.w(TAG, "Custom element does not exist");

                        Node child = findNodeWithId(w.id, nodes);
                        if (child != null) {
                            //if the item is active, set active to false
                            if (child.getNodeName().equals("weapon")) {
                                if (child
                                        .getNodeType() == Element.ELEMENT_NODE) {
                                    Element e = (Element) child;
                                    e.setAttribute("active",
                                            String.valueOf(w.active));
                                }
                            }
                        }

                        Log.d(TAG,
                                "set " + w.id + " active status to "
                                        + w.active);

                        //Overwrite the existing XML file
                        TransformerFactory transformerFactory = XMLUtils
                                .getTransformerFactory();
                        javax.xml.transform.Transformer transformer = transformerFactory
                                .newTransformer();
                        DOMSource source = new DOMSource(dom);
                        StreamResult result = new StreamResult(
                                new File(location + item));
                        transformer.transform(source, result);
                    }

                } catch (ParserConfigurationException | SAXException e) {
                    Log.w(TAG, e);
                }

            }
        } catch (IOException i) {
            Log.w(TAG, "Error " + i);
        } catch (TransformerException e) {
            Log.w(TAG, "Error: " + e);
        }
    }

    void ascendToRoot() {
        while (!currNode.getNodeName().equals("munitions")) {
            ascend();
        }
    }

    private String getCategory(Weapon w) {

        String category = currNode.getNodeName();
        if (category == null)
            return "";

        if (category.equals("category"))
            return currNode.getAttributes().getNamedItem("name").getNodeValue();
        else if (!category.equals("Favorites")) {
            return category;
        } else {
            if (w == null)
                return "null";
            Node n = findNodeWithId(w.id,
                    customNode.getChildNodes());

            if (n != null) {
                return n.getParentNode().getNodeName();
            }

            final Node child = findNodeWithId(w.id,
                    currNode.getParentNode().getChildNodes());
            if (child != null)
                n = child.getParentNode();

            // based on the former signature, if the value is null, return "null".
            if (n == null)
                return "null";

            if (n.getNodeName().equals("category"))
                return n.getAttributes().getNamedItem("name")
                        .getNodeValue();
            else
                return n.getNodeName();

        }
    }

    private void sendRRDeleteIntent(Weapon w) {
        if (w == null)
            return;

        String category = getCategory(w);

        w.active = false;
        setWeaponActiveStatus(w);

        Intent i = new Intent(DangerCloseReceiver.TOGGLE);
        i.putExtra("name", w.name + "[" + w.id + "]");
        i.putExtra("remove", true);
        i.putExtra("category", category);
        i.putExtra("target", (target != null) ? target
                : UUID.randomUUID().toString());
        i.putExtra("fromLine", fromLine);
        i.putExtra("persist", false);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    private void sendRRCreateIntent(Weapon w) {
        if (w == null)
            return;

        String category = getCategory(w);

        w.active = true;
        setWeaponActiveStatus(w);

        Intent i = new Intent(DangerCloseReceiver.TOGGLE);
        i.putExtra("name", w.name + "[" + w.id + "]");
        i.putExtra("innerRange", w.innerRange);
        i.putExtra("outerRange", w.outerRange);
        i.putExtra("description", w.description);
        i.putExtra("category", category);
        i.putExtra("target", (target != null) ? target
                : UUID.randomUUID().toString());
        i.putExtra("fromLine", fromLine);
        i.putExtra("persist", false);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    private void addFavorite(int id) {
        if (favorites != null) {
            favorites.add(id);
            saveFavorites();
        }
    }

    private void removeFavorite(int id) {
        if (favorites != null) {
            favorites.remove(id);
            saveFavorites();
        }
    }

    void toggleAddMode() {
        addMode = !addMode;
    }

    void toggleRemoveMode() {
        removeMode = !removeMode;
        if (removeMode)
            removing = new HashSet<>();
    }

    void toggleRemoveCustomMode() {
        removeCustomMode = !removeCustomMode;
    }

    private void checkEachItemForActiveStatus(NodeList nl) {
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Element.ELEMENT_NODE
                    && n.getNodeName().equals("weapon")) {
                Element e = (Element) n;

                String wName = e.getAttribute("name");
                String wId = e.getAttribute("ID");

                if (checkItemStatus(wName + "[" + wId + "]", fromLine)) {
                    Log.d(TAG, "[" + e.getAttribute("ID")
                            + "] set active to true");
                    e.setAttribute("active", "true");
                }
            } else {
                NodeList childNodes = n.getChildNodes();
                if (childNodes != null)
                    checkEachItemForActiveStatus(childNodes);
            }
        }
    }

    private void saveFavorites() {

        String location = FileSystemUtils.getItem(DIRNAME).getPath() +
                File.separator;
        String item = "fav_muni.txt";
        try {
            File f = new File(location + item);
            boolean first = true;
            try (BufferedWriter writer = new BufferedWriter(
                    IOProviderFactory.getFileWriter(f))) {
                for (Integer i : favorites) {
                    StringBuilder sBuilder = new StringBuilder();
                    if (first)
                        first = false;
                    else
                        sBuilder.append("\n");

                    sBuilder.append(i.toString());
                    Log.d(TAG, "write: " + sBuilder);
                    writer.write(sBuilder.toString());

                }
            }

        } catch (IOException io) {
            Log.e(TAG, "error: ", io);
        }

    }

    private void loadFavorites() {
        favorites = new HashSet<>();
        String location = FileSystemUtils.getItem(DIRNAME).getPath() +
                File.separator;
        String item = "fav_muni.txt";

        try {

            Log.d(TAG, "load favorites: " + location + item);
            File f = new File(location + item);
            if (IOProviderFactory.exists(f)) {

                try (Reader r = IOProviderFactory.getFileReader(f);
                        BufferedReader reader = new BufferedReader(r)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.replace("\n", "");
                        int id = Integer.parseInt(line);
                        favorites.add(id);
                    }
                }

            } else {
                File fd = new File(location);
                if (!IOProviderFactory.mkdir(fd))
                    Log.w(TAG,
                            "Failed to create directory"
                                    + fd.getAbsolutePath());
            }
        } catch (IOException ignored) {

        }

    }

    /**
     * Load the customs from the Customs/customs.xml file located in
     * FileSystem Utils
     */
    private void loadCustoms() {
        String location = FileSystemUtils.getItem(DIRNAME).getPath()
                + File.separator;
        String item = "customs.xml";

        try {
            Log.d(TAG, "load customs: " + location + item);
            File f = new File(location + item);
            if (IOProviderFactory.exists(f)) {
                try {
                    DocumentBuilderFactory docFactory = XMLUtils
                            .getDocumenBuilderFactory();
                    DocumentBuilder docBuilder = docFactory
                            .newDocumentBuilder();
                    Document dom;
                    try (InputStream is = IOProviderFactory.getInputStream(f)) {
                        dom = docBuilder.parse(is);
                    }

                    if (dom != null) {
                        NodeList nodes = dom
                                .getElementsByTagName("Custom_Threat_Rings");

                        if (nodes.getLength() > 0)
                            customNode = nodes.item(0);
                        else
                            Log.w(TAG, "Custom element does not exist");
                    }

                } catch (ParserConfigurationException | SAXException e) {
                    Log.w(TAG, e);
                }

            } else {
                //wrap xml file
                File fd = new File(location);
                if (!IOProviderFactory.mkdir(fd))
                    Log.w(TAG,
                            "Failed to create directory"
                                    + fd.getAbsolutePath());

                DocumentBuilderFactory dbFactory = XMLUtils
                        .getDocumenBuilderFactory();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.newDocument();

                if (doc != null) {
                    Element rootEle = doc.createElement("Custom_Threat_Rings");
                    doc.appendChild(rootEle);

                    NodeList nodes = doc
                            .getElementsByTagName("Custom_Threat_Rings");
                    customNode = nodes.item(0);

                    TransformerFactory transformerFactory = XMLUtils
                            .getTransformerFactory();
                    javax.xml.transform.Transformer transformer = transformerFactory
                            .newTransformer();
                    DOMSource source = new DOMSource(doc);
                    StreamResult result = new StreamResult(
                            new File(location + item));
                    transformer.transform(source, result);
                }

            }
        } catch (IOException i) {
            Log.w(TAG, "Error " + i);
        } catch (ParserConfigurationException | TransformerException e) {
            Log.w(TAG, "Error: " + e);
        }

    }

    /**
     * Remove all custom variables that are checked
     */
    void removeCustoms() {
        if (customTBRemoved.size() == 0) {
            return;
        }
        String location = FileSystemUtils.getItem(
                FileSystemUtils.TOOL_DATA_DIRECTORY).getPath()
                + File.separator;
        String item = "fires/customs" + ".xml";

        try {
            Log.d(TAG, "removing customs: " + location + item);
            File f = new File(location + item);
            if (IOProviderFactory.exists(f)) {
                try {
                    //build the DOM
                    DocumentBuilderFactory docFactory = XMLUtils
                            .getDocumenBuilderFactory();
                    DocumentBuilder docBuilder = docFactory
                            .newDocumentBuilder();
                    Document dom;
                    try (InputStream is = IOProviderFactory.getInputStream(f)) {
                        dom = docBuilder.parse(is);
                    }

                    //get all the nodes under 'Custom' tag
                    NodeList nodes = dom
                            .getElementsByTagName("Custom_Threat_Rings");
                    if (nodes.getLength() == 0)
                        return;

                    Node custom = nodes.item(0);
                    NodeList children = custom.getChildNodes();

                    //remove the custom weapons using the ID's
                    for (Integer i : customTBRemoved) {
                        if (favorites.contains(i))
                            removeFavorite(i);

                        Node child = findNodeWithId(i, children);
                        if (child != null) {
                            //if the item is active, set active to false
                            if (child.getNodeName().equals("weapon")) {
                                if (child
                                        .getNodeType() == Element.ELEMENT_NODE) {
                                    Element e = (Element) child;
                                    boolean b = Boolean.parseBoolean(e
                                            .getAttribute("active"));
                                    if (b) {
                                        Weapon w = getWeaponData(child);
                                        sendRRDeleteIntent(w);
                                    }
                                }
                            }

                            custom.removeChild(child);
                        }

                    }
                    customTBRemoved.clear();

                    customNode = custom;

                    //Overwrite the existing XML file
                    TransformerFactory transformerFactory = XMLUtils
                            .getTransformerFactory();
                    javax.xml.transform.Transformer transformer = transformerFactory
                            .newTransformer();
                    DOMSource source = new DOMSource(dom);
                    StreamResult result = new StreamResult(
                            new File(location + item));
                    transformer.transform(source, result);

                } catch (ParserConfigurationException | TransformerException
                        | SAXException e) {
                    Log.w(TAG, e);
                }

            }
        } catch (IOException i) {
            Log.w(TAG, i);
        }

    }

    /**
     * Creates a custom weapon and adds it to the
     * Customs/custom.xml folder
     * @param name - name attribute
     * @param description - description attribute
     * @param standing - standing value
     * @param prone - prone value
     * @param proneProtected - prone protected attribute
     * @param ricochetFan - ricochet fan attribute
     */
    private void createCustomWeapon(int id, String name, String description,
            String standing, String prone, String proneProtected,
            String ricochetFan) {
        String location = FileSystemUtils.getItem(
                FileSystemUtils.TOOL_DATA_DIRECTORY).getPath()
                + File.separator;
        String item = "fires/customs" + ".xml";

        try {
            //build the DOM
            DocumentBuilderFactory docFactory = XMLUtils
                    .getDocumenBuilderFactory();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            Document dom;
            try (InputStream is = IOProviderFactory
                    .getInputStream(new File(location + item))) {
                dom = docBuilder.parse(is);
            }
            //custom list
            NodeList nodes = dom.getElementsByTagName("Custom_Threat_Rings");
            Element custom;
            if (nodes == null) {
                custom = dom.createElement("Custom_Threat_Rings");
                dom.appendChild(custom);
            } else
                custom = (Element) nodes.item(0);

            //if id is greater than 0, we are editing
            if (id > 0) {
                Node n = findNodeWithId(id, custom.getChildNodes());
                if (n != null) {
                    n.getAttributes().getNamedItem("name").setNodeValue(name);
                    n.getAttributes().getNamedItem("description")
                            .setNodeValue(description);
                    n.getAttributes().getNamedItem("standing")
                            .setNodeValue(String.valueOf(standing));
                    n.getAttributes().getNamedItem("prone")
                            .setNodeValue(String.valueOf(prone));
                    n.getAttributes().getNamedItem("proneprotected")
                            .setNodeValue(String.valueOf(proneProtected));
                    n.getAttributes().getNamedItem("ricochetfan")
                            .setNodeValue(ricochetFan);
                } else {
                    Log.e(TAG, "There was a problem with the Weapon ID");
                }
            } else {

                //custom weapon to be created
                Element customWeapon = dom.createElement("weapon");

                //wrap ID
                //get the ID of the last weapon created
                NodeList children = custom.getChildNodes();
                int lastId;
                if (children.getLength() > 0) {
                    Node last = children.item(children.getLength() - 1);
                    lastId = Integer.parseInt(last.getAttributes()
                            .getNamedItem("ID").getNodeValue());
                    lastId++;
                } else
                    lastId = ORDNANCE_LAST_ID + 1; //this is the ID of the last weapon in ordnance_table.xml

                Attr idAttr = dom.createAttribute("ID");
                idAttr.setValue(String.valueOf(lastId));
                customWeapon.setAttributeNode(idAttr);

                //active attribute initialized to false
                Attr activeAttr = dom.createAttribute("active");
                activeAttr.setValue(String.valueOf(false));
                customWeapon.setAttributeNode(activeAttr);

                //wrap description attribute
                Attr descAttr = dom.createAttribute("description");
                descAttr.setValue(description);
                customWeapon.setAttributeNode(descAttr);

                //wrap name attribute
                Attr nameAttr = dom.createAttribute("name");
                nameAttr.setValue(name);
                customWeapon.setAttributeNode(nameAttr);

                //wrap standing value attribute
                Attr standAttr = dom.createAttribute("standing");
                standAttr.setValue(standing);
                customWeapon.setAttributeNode(standAttr);

                Attr proneAttr = dom.createAttribute("prone");
                Attr proneProtAttr = dom.createAttribute("proneprotected");
                Attr ricoFanAttr = dom.createAttribute("ricochetfan");

                if (ricochetFan.equals("")) { //RED creation
                    //wrap prone value attribute
                    proneAttr.setValue(prone);

                    if (!proneProtected.equals("")) {
                        //wrap prone protected value attribute
                        proneProtAttr.setValue(proneProtected);
                    }
                } else { //MSD creation
                    //wrap ricochet fan attribute
                    ricoFanAttr.setValue(ricochetFan);
                }
                customWeapon.setAttributeNode(proneAttr);
                customWeapon.setAttributeNode(proneProtAttr);
                customWeapon.setAttributeNode(ricoFanAttr);

                //add to custom list
                custom.appendChild(customWeapon);
            }

            //overwrite the existing XML file
            TransformerFactory transformerFactory = XMLUtils
                    .getTransformerFactory();
            javax.xml.transform.Transformer transformer = transformerFactory
                    .newTransformer();
            transformer.transform(new DOMSource(dom),
                    new StreamResult(new File(location + item)));

            customNode = custom;

        } catch (ParserConfigurationException | IOException
                | TransformerException | SAXException e) {
            Log.e(TAG, "error: ", e);
        }
    }

    void parseFlightMunitions(String flightMunitionsXML) {
        if (flightMunitionsXML != null && !flightMunitionsXML.isEmpty()) {
            try {
                DocumentBuilderFactory docFactory = XMLUtils
                        .getDocumenBuilderFactory();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                InputStream is = new ByteArrayInputStream(
                        flightMunitionsXML
                                .getBytes(FileSystemUtils.UTF8_CHARSET));
                Document dom = docBuilder.parse(is);
                checkEachItemForActiveStatus(dom.getChildNodes());
                flightsNode = dom.getFirstChild(); // OSRMunitions

            } catch (ParserConfigurationException | SAXException
                    | IOException e) {
                Log.e(TAG, "error: ", e);
            }
        }

    }

    private void parseData(Context context) {
        try {
            DocumentBuilderFactory docFactory = XMLUtils
                    .getDocumenBuilderFactory();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            InputStream is = null;

            // see if the flavor supplies the ordnance tables.
            FlavorProvider provider = SystemComponentLoader.getFlavorProvider();
            if (provider != null) {
                is = provider.getAssetInputStream(ORDNANCE_XML);
            }

            // if the flavor does not, then just grab the localized.
            if (is == null) {
                is = context.getAssets().open(ORDNANCE_XML);
            }

            Document dom = docBuilder.parse(is);
            checkEachItemForActiveStatus(dom.getChildNodes());
            currNode = dom.getFirstChild();
            ordnanceNode = dom.getFirstChild();

            updateList(currNode);

        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.e(TAG, "error: ", e);
        }
    }

    private void updateList(final Node node) {
        if (node == null)
            return;

        ArrayList<Integer> temp = new ArrayList<>();

        final String nodeName = node.getNodeName();

        switch (nodeName) {
            case "Favorites":
                if (favorites != null && favorites.size() > 0) {
                    int[] arr = new int[favorites.size()];
                    int index = 0;
                    for (Integer i : favorites) {
                        arr[index++] = i;

                    }

                    currList = arr;
                } else
                    currList = new int[0];
                break;
            case "Current_Flights":
                // Need to retrieve the nodes built from received OSR's
                if (flightsNode != null) {
                    NodeList nodes = flightsNode.getChildNodes();
                    flightsParentNode = node.getParentNode();

                    if (nodes.getLength() > 0) {
                        for (int i = 0; i < nodes.getLength(); i++) {
                            Node n = nodes.item(i);
                            if (n.getNodeName().startsWith("category")) {
                                temp.add(i);
                            }
                        }

                        currList = convertIntegers(temp);
                    }
                } else {
                    currList = new int[0];
                }
                break;
            default:
                NodeList nodes = node.getChildNodes();

                if (nodes.getLength() > 0) {
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                        if (!n.getNodeName().startsWith("#")
                                && !n.getNodeName()
                                        .equals("Custom_Threat_Rings")) {
                            if (n.getNodeName().equals("Favorites"))
                                favoritesNode = n;
                            else if (!(n.getNodeName()
                                    .equals("Unguided_Mortar") &&
                                    fromLine != null
                                    && fromLine.equals("nineline"))) {
                                //don't want mortar showing up in nineline
                                temp.add(i);
                            }
                        }
                    }

                    currList = convertIntegers(temp);
                } else if (node.getNodeName().equals("Custom_Threat_Rings")) {
                    currList = new int[0];
                }
                break;
        }
        notifyDataSetChanged();
    }

    private static int[] convertIntegers(List<Integer> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    private boolean checkItemStatus(String currWeapon, String fromLine) {

        MapItem ring;
        if (fromLine == null) {
            ring = _mapView.getRootGroup().deepFindItem("uid",
                    target + "." + currWeapon);
        } else {
            ring = _mapView.getRootGroup().deepFindItem("uid",
                    target + "." + currWeapon + "." + fromLine);
        }
        return ring != null;
    }

    void ascend() {
        if (currNode == null)
            return;

        if (!currNode.getNodeName().equals("munitions")) {
            switch (currNode.getNodeName()) {
                case "OSRMunitions":
                    currNode = flightsParentNode;
                    flightsGroupSelected = false;
                    break;
                case "Current_Flights":
                    currNode = ordnanceNode;
                    break;
                case "Custom_Threat_Rings":
                    currNode = ordnanceNode;
                    break;
                default:
                    currNode = currNode.getParentNode();
                    break;
            }
            updateList(currNode);
            notifyDataSetChanged();
        }
    }

    void getFavorites() {
        currNode = favoritesNode;
        updateList(currNode);
    }

    void getCustoms() {
        currNode = customNode;
        updateList(currNode);
    }

    public static class ViewHolder {
        ImageView nextArrow;
        TextView activeText;
        TextView descText;
        CheckBox checkBox;
        CheckBox addFavorite;
        CheckBox removeFavorite;
        CheckBox removeCustom;
        TextView weaponText;
        TextView weaponTextSub;
        TextView standText;
        ImageButton editCustom;
    }

    private int getActiveGroupSize(Node n) {
        int count = 0;

        NodeList nl;

        if (n.getNodeName().equals("Current_Flights") && flightsNode != null) {
            nl = flightsNode.getChildNodes();
        } else {
            nl = n.getChildNodes();
        }

        for (int i = 0; i < nl.getLength(); i++) {
            Node temp = nl.item(i);

            if (temp.hasChildNodes()) {
                count += getActiveGroupSize(temp);
            } else {
                if (temp.getNodeName().equals("weapon")) {
                    if (temp.getNodeType() == Element.ELEMENT_NODE) {
                        Element e = (Element) temp;
                        boolean b = Boolean.parseBoolean(e
                                .getAttribute("active"));
                        if (b)
                            count++;
                    }
                }
            }
        }
        Log.d(TAG, "active group size is " + count);
        return count;
    }

    private void deActivateNodeList(NodeList nl) {
        for (int i = 0; i < nl.getLength(); i++) {
            Node temp = nl.item(i);
            if (temp.getNodeName().equals("Current_Flights")) {
                // go thru flights data and deactivate if active
                if (flightsNode != null) {
                    deActivateNodeList(flightsNode.getChildNodes());
                }
            } else if (temp.hasChildNodes()) {
                deActivateNodeList(temp.getChildNodes());
            } else {
                if (temp.getNodeName().equals("weapon")) {
                    if (temp.getNodeType() == Element.ELEMENT_NODE) {
                        Element e = (Element) temp;
                        boolean b = Boolean.parseBoolean(e
                                .getAttribute("active"));
                        if (b) {
                            Log.d(TAG, "[" + e.getAttribute("ID")
                                    + "] set active to false");
                            e.setAttribute("active", "false");

                            if (temp.getParentNode().getNodeName()
                                    .equals("Custom_Threat_Rings")) {
                                Weapon w = getWeaponData(temp);
                                setCustomActiveStatus(w);
                            }
                        }
                    }
                }
            }
        }
    }

    public void removeAll() {
        deActivateNodeList(ordnanceNode.getChildNodes());
        deActivateNodeList(customNode.getChildNodes());
        if (currNode != null &&
                currNode.getNodeName().equals("Custom_Threat_Rings"))
            getCustoms();

        notifyDataSetChanged();
    }

    /**
    private static void DEBUG_node(Node n)
    {
        Log.d(TAG, "- Checking node: " + n.getNodeName());
    
        NamedNodeMap attr = n.getAttributes();
    
        Log.d(TAG, "now checking attributes");
        for (int z = 0; z < attr.getLength(); z++)
        {
            Node a = attr.item(z);
            Log.d(TAG,
                    "attr [" + z + "] " + a.getNodeName() + ": "
                            + a.getNodeValue());
        }
    
        NodeList nl = n.getChildNodes();
        Log.d(TAG, "now checking children");
    
        for (int i = 0; i < nl.getLength(); i++)
        {
            Node c = nl.item(i);
    
            if (!c.getNodeName().startsWith("#"))
            {
                attr = c.getAttributes();
                for (int t = 0; t < attr.getLength(); t++)
                {
                    Node a = attr.item(t);
                    Log.d(TAG,
                            "[" + i + "] attr [" + t + "] " + a.getNodeName()
                                    + ": "
                                    + a.getNodeValue());
                }
    
            }
    
        }
    }
    
    private static void DEBUG_weapon(Weapon w)
    {
        Log.d(TAG, "Weapon " + w.id);
        Log.d(TAG, "name       : " + w.name);
        Log.d(TAG, "active     : " + w.active);
        Log.d(TAG, "description: " + w.description);
        Log.d(TAG, "inner      : " + w.innerRange);
        Log.d(TAG, "outer      : " + w.outerRange);
    }
    **/

    /**
     * This method will initiate the creation of a custom weapon.
     * Alerted by the CustomCreator class
     * @param name - name of weapon (cannot be empty)
     * @param description - description (can be empty)
     * @param standing - standing value (cannot be null or negative)
     * @param prone - prone value (cannot be null or negative)
     * @param proneProt - prone protected value (can be null)
     */
    @Override
    public void onCustomInfoReceived(int id, String name, String description,
            String standing, String prone,
            String proneProt, String ricochetFan) {
        createCustomWeapon(id, name, description, standing, prone, proneProt,
                ricochetFan);
        getCustoms();
    }

    /**
     * This will build the dialog that appears after long
     * pressing a list item
     * @param curr_pos - the position in the list of the item clicked
     */
    void buildInfoDialog(int curr_pos) {
        LayoutInflater inflater = LayoutInflater.from(_context);
        final View v = inflater
                .inflate(R.layout.danger_close_info_dialog, null);

        AlertDialog.Builder alt_bld = new AlertDialog.Builder(_context);

        alt_bld.setCancelable(false)
                .setPositiveButton(R.string.ok, null);

        alt_bld.setView(v);
        final AlertDialog alert = alt_bld.create();
        alt_bld.setPositiveButton(R.string.ok, null);
        int current = currList[curr_pos];
        final Node n;

        if (currNode != null) {
            if (currNode.getNodeName().equals("Favorites")) {
                Node p = currNode.getParentNode();
                Node find = findNodeWithId(current, p.getChildNodes());
                if (find == null)
                    find = findNodeWithId(current, customNode.getChildNodes());
                n = find;
            } else {
                n = currNode.getChildNodes().item(current);
            }
            alert.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    boolean msd = false;
                    if (n == null)
                        return;
                    if (n.getAttributes().getNamedItem("ricochetfan") != null
                            && !n.getAttributes().getNamedItem("ricochetfan")
                                    .getNodeValue().equals(""))
                        msd = true;

                    //name value
                    TextView weapon_name = v
                            .findViewById(R.id.weapon_name);
                    View sub_view = v.findViewById(R.id.subtext_view);
                    TextView weapon_sub = v
                            .findViewById(R.id.weapon_subtext);

                    String name = n.getAttributes().getNamedItem("name")
                            .getNodeValue();

                    //if the name has an additional part in parenthesis
                    //we're going to section it off to improve the UI
                    if (name.contains("(") && name.contains(")")) {
                        int start = name.indexOf("(");
                        //wrap substring without parenthesis
                        String nameSubstring = name.substring(start);
                        //remove the substring from name
                        name = name.replace(" " + nameSubstring, "");
                        //strip the substring of the parentheses
                        nameSubstring = nameSubstring.substring(1,
                                nameSubstring.length() - 1);

                        weapon_sub.setText(nameSubstring);
                        sub_view.setVisibility(View.VISIBLE);
                    }

                    weapon_name.setText(name);

                    //There is not always a description so make sure to check for it
                    if (n.getAttributes().getNamedItem("description") != null
                            &&
                            !n.getAttributes().getNamedItem("description")
                                    .getNodeValue().equals("")) {
                        View desc_view = v.findViewById(R.id.desc_view);
                        desc_view.setVisibility(View.VISIBLE);
                        TextView weapon_desc = v
                                .findViewById(R.id.weapon_description);
                        weapon_desc.setText(n.getAttributes()
                                .getNamedItem("description").getNodeValue());
                    }

                    //standing value
                    TextView weapon_stand = v
                            .findViewById(R.id.weapon_standing);
                    String standing = n.getAttributes()
                            .getNamedItem("standing")
                            .getNodeValue()
                            + "m";
                    weapon_stand.setText(standing);
                    if (msd) {
                        v.findViewById(R.id.standing_label)
                                .setVisibility(View.GONE);
                        v.findViewById(R.id.msd_label).setVisibility(
                                View.VISIBLE);
                    }

                    //prone value
                    if (n.getAttributes().getNamedItem("prone") != null
                            &&
                            !n.getAttributes().getNamedItem("prone")
                                    .getNodeValue()
                                    .equals("")) {
                        TextView weapon_prone = v
                                .findViewById(R.id.weapon_prone);
                        LinearLayout prone_view = v
                                .findViewById(R.id.prone_view);
                        prone_view.setVisibility(View.VISIBLE);
                        String prone = n.getAttributes().getNamedItem("prone")
                                .getNodeValue()
                                + "m";
                        weapon_prone.setText(prone);
                    }

                    //prone protected value
                    if (n.getAttributes().getNamedItem("proneprotected") != null
                            &&
                            !n.getAttributes().getNamedItem("proneprotected")
                                    .getNodeValue().equals("")) {
                        TextView weapon_proneP = v
                                .findViewById(R.id.weapon_proneP);
                        LinearLayout proneP_view = v
                                .findViewById(R.id.prone_protected_view);
                        View proneP_line = v.findViewById(R.id.proneP_line);
                        //Set the visibility of the line between prone and prone protected
                        proneP_line.setVisibility(View.VISIBLE);
                        proneP_view.setVisibility(View.VISIBLE);
                        String proneP = n.getAttributes()
                                .getNamedItem("proneprotected").getNodeValue()
                                + "m";
                        weapon_proneP.setText(proneP);
                    }

                    //ricochet fan value for minimum safe distances
                    if (msd) {
                        TextView weapon_ricochet = v
                                .findViewById(R.id.weapon_ricochet_fan);
                        LinearLayout ricochet_view = v
                                .findViewById(R.id.ricochet_fan_view);
                        ricochet_view.setVisibility(View.VISIBLE);
                        String ricochetFan = n.getAttributes()
                                .getNamedItem("ricochetfan").getNodeValue();
                        weapon_ricochet.setText(ricochetFan);
                    }

                }
            });

            alert.show();
        }
    }

    /**
     * Register a custom view adapter for modifying the muninition visual display.   Care must be
     * taken to unregister this when unloading the plugin.   Unregistration is performed by passing null.
     * @param cva the custom view adapter
     */
    public static void registerCustomViewAdapter(CustomViewAdapter cva) {
        customViewAdapter = cva;
    }

}
