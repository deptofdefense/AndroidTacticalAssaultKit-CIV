
package com.atakmap.android.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.android.update.sorters.ProductInformationComparator;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.Collections;
import java.util.List;

public class ProductInformationWizard {

    private static final String TAG = "ProductInformationWizard";

    public interface IProductInformationWizardHandler {

        /**
         * Invoked once at beginning of wizard
         * @return true to proceed, false to cancel
         */
        boolean preProcess();

        /**
         * Invoked for each product in the list
         * @param app
         */
        void process(ProductInformation app);

        /**
         * Invoked once at end of wizard
         */
        void postProcess();

        /**
         * The user has cancelled the wizard
         */
        void cancel();
    }

    private final Context _context;
    private final ProductProviderManager _manager;
    private final String _dialogTitle;
    private final String _dialogText;
    private final String _dialogMoreInfo;
    private final String _wizardText;
    private final IProductInformationWizardHandler _handler;

    public ProductInformationWizard(Activity context,
            ProductProviderManager manager, String title,
            String message, String hint, String wizardText,
            IProductInformationWizardHandler handler) {
        _context = context;
        _manager = manager;
        _dialogTitle = title;
        _dialogText = message;
        _dialogMoreInfo = hint;
        _wizardText = wizardText;
        _handler = handler;
    }

    /**
     * Initiate the wizard with the specified list of products
     * @param products
     */
    public void begin(final List<ProductInformation> products) {
        Log.d(TAG, "begin: " + products.size());

        //allow handler to do validation up front
        if (!_handler.preProcess()) {
            Log.w(TAG, "pre process failed");
            return;
        }

        //sort by update availability, and then label alphabetically
        Collections.sort(products, new ProductInformationComparator());
        begin(products.toArray(new ProductInformation[0]));
    }

    /**
     * Display initial dialog to allow user to begin
     * @param data
     */
    private void begin(final ProductInformation[] data) {
        LayoutInflater inflater = LayoutInflater.from(_context);
        View productView = inflater.inflate(
                R.layout.app_mgmt_product_wizard_layout, null);

        final ListView productListView = productView
                .findViewById(R.id.app_mgmt_wizard_listview);
        ProductInformationListAdapter productListAdapter = new ProductInformationListAdapter(
                _context, data);
        productListView.setAdapter(productListAdapter);

        ((TextView) productView.findViewById(R.id.app_mgmt_wizard_moreinfo))
                .setText(_dialogText);
        //View header = inflater.inflate(R.layout.app_mgmt_wizard_header, null);
        //pluginListView.addHeaderView(header);

        final String title = _dialogTitle + " " + data.length
                + " Products";

        final AlertDialog dialog = new AlertDialog.Builder(_context)
                .setIcon(com.atakmap.android.util.ATAKConstants.getIconId())
                .setTitle(title)
                .setView(productView)
                .setCancelable(false)
                .setPositiveButton("Begin",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                Log.d(TAG, "Update products");
                                process(data, 0);
                            }
                        })
                .setNegativeButton(R.string.cancel, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Log.d(TAG, "Cancel products");
                                _handler.cancel();
                            }
                        })
                .create();

        ImageButton helpButton = productView
                .findViewById(R.id.app_mgmt_wizard_moreinfoBtn);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "More info on products");
                dialog.dismiss();
                new AlertDialog.Builder(_context)
                        .setIcon(com.atakmap.android.util.ATAKConstants
                                .getIconId())
                        .setTitle(title)
                        .setMessage(_dialogMoreInfo)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        begin(data);
                                    }
                                })
                        .show();
            }
        });

        // set dialog dims appropriately based on device size
        WindowManager.LayoutParams screenLP = new WindowManager.LayoutParams();
        final Window w = dialog.getWindow();
        if (w != null) {
            screenLP.copyFrom(w.getAttributes());
            screenLP.width = WindowManager.LayoutParams.MATCH_PARENT;
            screenLP.height = WindowManager.LayoutParams.MATCH_PARENT;
            w.setAttributes(screenLP);
        }
        dialog.show();
    }

    /**
     * Dialog based wizard to handle products. Process the specified Product
     *
     * @param products
     * @param index
     */
    private void process(final ProductInformation[] products, final int index) {
        Log.d(TAG, "process size: " + products.length + ", index: " + index);
        if (products.length == 0) {
            _handler.postProcess();
            return;
        }

        final ProductInformation product = products[index];

        String message = _wizardText + " " + product.getSimpleName();
        if (index > 0) {
            message = "Wait for previous step to complete. " + message;
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(_context)
                .setTitle(
                        product.getSimpleName() + " - " + (index + 1) + " of "
                                + products.length)
                .setMessage(message)
                //.setCancelable(false)
                .setPositiveButton("Process",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (index < products.length - 1) {
                                    //show next dialog
                                    dialog.dismiss();
                                    process(products, index + 1);

                                    //update the product
                                    _handler.process(product);
                                } else {
                                    //update the product
                                    _handler.process(product);

                                    //check for updates
                                    dialog.dismiss();
                                    _handler.postProcess();
                                }
                            }
                        })
                .setNegativeButton("Skip", // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (index < products.length - 1) {
                                    //show next dialog
                                    dialog.dismiss();
                                    process(products, index + 1);
                                } else {
                                    //check for updates
                                    dialog.dismiss();
                                    _handler.postProcess();
                                }
                            }
                        });

        Drawable icon = product.getIcon();
        if (icon != null) {
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, icon));
        } else {
            dialog.setIcon(com.atakmap.android.util.ATAKConstants.getIconId());
        }

        dialog.show();
    }

    /**
     * Adapter of list of products
     */
    private static class ProductInformationListAdapter extends
            ArrayAdapter<ProductInformation> {

        final public static String TAG = "ProductInformationListAdapter";

        private final ProductInformation[] _products;
        private final Context ctx;
        private final LayoutInflater _inflater;

        public ProductInformationListAdapter(Context context,
                ProductInformation[] products) {
            super(context, R.layout.app_mgmt_product_wizard_row, products);

            this.ctx = context;
            this._products = products;
            this._inflater = LayoutInflater.from(ctx);

            Log.d(TAG, "created with " + this._products.length
                    + " plugins listed");
        }

        @NonNull
        @Override
        public View getView(int position, View convertView,
                @NonNull ViewGroup parent) {
            //TODO display any more details e.g. version #s, why not compat
            //TODO cache labels/icons for faster loading of views?
            View row = convertView;
            ProductInformationViewHolder holder = null;
            if (row == null) {
                row = _inflater.inflate(R.layout.app_mgmt_product_wizard_row,
                        parent, false);
                holder = new ProductInformationViewHolder();
                holder.appIcon = row
                        .findViewById(R.id.app_mgmt_row_wizard_icon);
                holder.appLabel = row
                        .findViewById(R.id.app_mgmt_row_wizard_title);
                row.setTag(holder);
            } else {
                holder = (ProductInformationViewHolder) row.getTag();
            }

            final ProductInformation product = _products[position];
            if (product == null) {
                Log.w(TAG, "ProductInformation is empty");
                return _inflater.inflate(R.layout.empty, parent, false);
            }
            if (!FileSystemUtils.isEmpty(product.getSimpleName())) {
                holder.appLabel.setText(product.getSimpleName());
            }

            Drawable icon = product.getIcon();
            if (icon != null) {
                holder.appIcon.setVisibility(View.VISIBLE);
                holder.appIcon.setImageDrawable(icon);
            } else {
                holder.appIcon.setVisibility(View.INVISIBLE);
            }

            return row;
        }

        static class ProductInformationViewHolder {
            ImageView appIcon;
            TextView appLabel;
        }

    }
}
