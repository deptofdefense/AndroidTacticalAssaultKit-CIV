
package com.atakmap.android.image;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.attachment.layer.GLAttachmentBillboardLayer;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.AttachmentWatcher;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.opengl.GLLayerFactory;

/**
 * Creates the component for displaying images in the system.   Images 
 */
public class ImageMapComponent extends DropDownMapComponent {

    private AttachmentWatcher _attWatcher;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);

        _attWatcher = new AttachmentWatcher(view, FileSystemUtils
                .getItem("attachments"));
        _attWatcher.start();

        DocumentedIntentFilter viewImagesFilter = new DocumentedIntentFilter();
        viewImagesFilter.addAction(ImageDropDownReceiver.IMAGE_DISPLAY,
                "Display an image in the image viewer drop-down",
                new DocumentedExtra[] {
                        new DocumentedExtra("imageURI",
                                "The image URI to display",
                                false, String.class),
                        new DocumentedExtra("imageURIs",
                                "Array of image URIs to display, where \"imageURI\" is the image displayed first",
                                true, String[].class),
                        new DocumentedExtra("uid",
                                "The UID of the map item to display image attachments for",
                                true, String.class),
                        new DocumentedExtra("title",
                                "The title of the image",
                                true, String.class),
                        new DocumentedExtra("titles",
                                "The title of each image in \"imageURIs\"",
                                true, String[].class),
                        new DocumentedExtra("noFunctionality",
                                "True to disable send and edit functionality",
                                true, Boolean.class),
                });
        viewImagesFilter.addAction(ImageDropDownReceiver.IMAGE_UPDATE,
                "Same as ImageDropDownReceiver.IMAGE_DISPLAY");
        viewImagesFilter.addAction(ImageDropDownReceiver.IMAGE_REFRESH,
                "Fired by Image Markup when an image has been edited",
                new DocumentedExtra[] {
                        new DocumentedExtra("imageURI",
                                "The URI of the edited image",
                                false, String.class),
                        new DocumentedExtra("uid",
                                "The UID of the associated map item, if any",
                                true, String.class)
                });
        viewImagesFilter.addAction(
                ImageDropDownReceiver.IMAGE_SELECT_RESOLUTION,
                "Select image resolution in preparation to send",
                new DocumentedExtra[] {
                        new DocumentedExtra("filepath",
                                "The image file path",
                                false, String.class),
                        new DocumentedExtra("uid",
                                "The UID of the associated map item, if any",
                                true, String.class),
                        new DocumentedExtra("sendTo",
                                "List of contact UIDs to send to",
                                true, String[].class)
                });
        registerDropDownReceiver(new ImageDropDownReceiver(view),
                viewImagesFilter);

        DocumentedIntentFilter viewEquirectangularImagesFilter = new DocumentedIntentFilter();
        viewEquirectangularImagesFilter.addAction(
                SphereImageViewerDropDownReceiver.SHOW_IMAGE,
                "View an Equirectangular Image",
                new DocumentedExtra[] {
                        new DocumentedExtra("filepath",
                                "The image file path",
                                false, String.class)
                });

        registerDropDownReceiver(new SphereImageViewerDropDownReceiver(view),
                viewEquirectangularImagesFilter);

        BroadcastReceiver imageMapReceiver = new ImageMapReceiver(view);
        DocumentedIntentFilter imageMapFilter = new DocumentedIntentFilter();
        imageMapFilter.addAction(ImageMapReceiver.IMAGE_DETAILS,
                "Select image's associated marker and open its details");
        this.registerReceiver(context, imageMapReceiver, imageMapFilter);

        BroadcastReceiver imageGalleryReceiver = new ImageGalleryReceiver(view);
        DocumentedIntentFilter imageGalleryFilter = new DocumentedIntentFilter();
        imageGalleryFilter.addAction(ImageGalleryReceiver.IMAGE_GALLERY,
                "Launch the image gallery drop-down",
                new DocumentedExtra[] {
                        new DocumentedExtra("title",
                                "Title to display at the top of the gallery",
                                true, String.class),
                        new DocumentedExtra("uid",
                                "The UID of the attached map item",
                                true, String.class),
                        new DocumentedExtra("directory",
                                "Directory to load files from",
                                true, String.class),
                        new DocumentedExtra("uris",
                                "Array of file URIs to display",
                                true, String[].class),
                        new DocumentedExtra("files",
                                "Array of file paths",
                                true, String[].class),
                        new DocumentedExtra("uids",
                                "Array of associated map item UIDs per each path in \"files\"",
                                true, String[].class),
                        new DocumentedExtra("fullscreen",
                                "True to expand drop-down to full screen",
                                true, Boolean.class),
                        new DocumentedExtra("callbackTag1",
                                "Intent action to broadcast when a file is added by the user",
                                true, String.class),
                        new DocumentedExtra("callbackTag1",
                                "Intent action to broadcast when a file is added by the user",
                                true, String.class),
                        new DocumentedExtra("callbackTag2",
                                "Intent action to broadcast when a file is deleted by the user",
                                true, String.class),
                        new DocumentedExtra("callbackTag3",
                                "Path to newly created image attachment",
                                true, String.class),
                });
        imageGalleryFilter.addAction(ImageGalleryReceiver.VIEW_ATTACHMENTS,
                "Launch the image gallery for a map item's attachments",
                new DocumentedExtra[] {
                        new DocumentedExtra("uid", "Map item UID",
                                false, String.class),
                        new DocumentedExtra("focusmap",
                                "True to focus on the map item",
                                true, Boolean.class)
                });
        imageGalleryFilter.addAction(ImageGalleryReceiver.SEND_FILES,
                "Sent by image file adapter to send files to selected contacts",
                new DocumentedExtra[] {
                        new DocumentedExtra("files", "Array of file paths",
                                false, String[].class),
                        new DocumentedExtra("sendTo",
                                "Array of contact UIDs to send files to",
                                false, String[].class)
                });
        this.registerReceiver(context, imageGalleryReceiver,
                imageGalleryFilter);

        GLLayerFactory.register(GLAttachmentBillboardLayer.SPI);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
        GLLayerFactory.unregister(GLAttachmentBillboardLayer.SPI);
        _attWatcher.dispose();
    }
}
