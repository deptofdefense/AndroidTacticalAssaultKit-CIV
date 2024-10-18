
package com.atakmap.android.user.feedback;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class UserFeedbackAdapter extends BaseAdapter {

    private final static String TAG = "UserFeedbackAdapter";
    private final Context context;
    private final LayoutInflater mInflater;
    private final ArrayList<Feedback> feedbackList = new ArrayList<>();
    private final SharedPreferences prefs;

    public UserFeedbackAdapter(Context context) {
        this.context = context;
        mInflater = LayoutInflater.from(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        load();
    }

    @Override
    public int getCount() {
        return feedbackList.size();
    }

    @Override
    public Object getItem(int i) {
        return feedbackList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    /**
     * Add an entry to the adapter
     * @param entry the feedback entry
     */
    public void add(Feedback entry) {
        entry.save(context);

        if (!feedbackList.contains(entry))
            feedbackList.add(entry);
        Collections.sort(feedbackList, FeedbackComparator);
        notifyDataSetChanged();
    }

    /**
     * Remove an entry from the adapter
     * @param entry the feedback entry
     */
    public void remove(Feedback entry) {
        entry.dispose();
        feedbackList.remove(entry);
        notifyDataSetChanged();
    }

    @Override
    public View getView(final int position, View convertView,
            ViewGroup parent) {
        final ViewHolder holder;

        final Feedback entry = feedbackList.get(position);

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.user_feedback_item, null);

            holder.title = convertView
                    .findViewById(R.id.titleTextView);
            holder.delete = convertView
                    .findViewById(R.id.remove);
            holder.edit = convertView
                    .findViewById(R.id.edit);
            holder.submit = convertView.findViewById(R.id.submit);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.title.setText(entry.get("title"));

        holder.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder deleteWarning = createDelete(entry);
                deleteWarning.show();
            }
        });

        holder.submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit(entry, holder);
            }
        });

        holder.edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditor(entry);
            }
        });

        return convertView;
    }

    private void submit(Feedback entry, ViewHolder holder) {
        @SuppressWarnings("SpellCheckingInspection")
        int size = Integer.parseInt(
                prefs.getString("filesharingSizeThresholdNoGo", "20"));
        if (entry.getFeedbackSize() > size * 1024L * 1024L)
            Toast.makeText(context,
                    "Feedback exceeds the current maximum length for a mission packages ("
                            +
                            size + "mb)",
                    Toast.LENGTH_SHORT).show();
        else {

            entry.save(context);

            holder.submit.setEnabled(false);
            final ProgressDialog progressDialog = new ProgressDialog(
                    context);
            progressDialog.setTitle(R.string.processing);
            progressDialog
                    .setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setIndeterminate(false);
            progressDialog.setMessage("Collecting files to submit");

            if (entry.getAssociatedFiles().size() > 0)
                progressDialog.show();

            SubmitFeedbackHelper.submit(entry,
                    getProgressCallback(progressDialog, entry, holder));
        }
    }

    private SubmitFeedbackHelper.SubmitFeedbackProgress getProgressCallback(
            final ProgressDialog progressDialog,
            final Feedback entry,
            final ViewHolder holder) {
        return new SubmitFeedbackHelper.SubmitFeedbackProgress() {
            @Override
            public void progress(String file, long size,
                    int currentIdx, int maxIdx,
                    long currentBytes, long maxBytes) {
                ((Activity) context)
                        .runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.setMessage(
                                        "Processing " + file + " (" +
                                                formatFileSize(size) + ")");
                                progressDialog.setMax(maxIdx);
                                progressDialog.setProgress(currentIdx);
                            }
                        });
            }

            @Override
            public void finished(boolean error) {

                ((Activity) context)
                        .runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                if (error)
                                    Toast.makeText(context,
                                            context.getString(
                                                    R.string.error_occurred_submtting_feedback,
                                                    entry.get("title")),
                                            Toast.LENGTH_LONG).show();
                                else {
                                    notifyUser(entry);
                                }
                                holder.submit
                                        .setEnabled(true);
                            }
                        });
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }
    
    
    private void notifyUser(Feedback entry) { 
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.submit);
        builder.setMessage(context.getString(R.string.userfeedback_submit_message,
                entry.get("title")));
        builder.setNegativeButton(R.string.ok, null);
        builder.show();
    }

    /**
     * Constructs the editor dialog builder for modifying user supplied feedback
     * @param entry the feedback entry
     */
    public void showEditor(final Feedback entry) {

        View v = mInflater.inflate(R.layout.user_feeback_entry, null, false);

        final EditText userFeedbackTitleET = v
                .findViewById(R.id.user_feedback_title);
        final EditText userFeedbackDescriptionET = v
                .findViewById(R.id.user_feedback_description);
        final ImageButton add = v.findViewById(R.id.add_user_feedback_files);

        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchGallery(entry);
            }
        });

        userFeedbackTitleET.setText(entry.get("title"));
        userFeedbackDescriptionET.setText(entry.get("description"));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setCancelable(false).setView(v)
                .setTitle(R.string.user_feedback)
                .setPositiveButton(R.string.done,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface,
                                    int i) {

                                String title = userFeedbackTitleET.getText()
                                        .toString();
                                String description = userFeedbackDescriptionET
                                        .getText().toString();
                                entry.set("title", title);
                                entry.set("description", description);
                                add(entry);
                            }
                        });
        AlertDialog alertDialog = builder.show();
        AlertDialogHelper.adjustWidth(alertDialog, .90d);
        AlertDialogHelper.adjustHeight(alertDialog, .90d);
    }

    /**
     * Given a feedback entry, launch the associated file gallery
     * @param entry the feedback entry
     */
    public void launchGallery(Feedback entry) {
        final AlertDialog.Builder galleryDialog = new AlertDialog.Builder(
                context);
        galleryDialog.setTitle(R.string.associated_files);

        FeedbackGallery feedbackGallery = new FeedbackGallery(context);
        feedbackGallery.init(context);
        feedbackGallery.refresh(entry);

        galleryDialog.setView(feedbackGallery);
        galleryDialog.setPositiveButton(R.string.done, null);
        AlertDialog alertDialog = galleryDialog.show();
        AlertDialogHelper.adjustWidth(alertDialog, .90d);
        AlertDialogHelper.adjustHeight(alertDialog, .90d);

    }

    private AlertDialog.Builder createDelete(Feedback entry) {
        //Delete Dialog
        final AlertDialog.Builder deleteWarning = new AlertDialog.Builder(
                context);
        deleteWarning.setTitle(R.string.warning);
        deleteWarning
                .setMessage(context.getString(R.string.are_you_sure_delete2,
                        entry.get("title")));
        deleteWarning.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        remove(entry);
                    }
                });
        deleteWarning.setNegativeButton(R.string.cancel, null);
        return deleteWarning;
    }

    static class ViewHolder {
        TextView title;
        ImageButton edit;
        ImageButton delete;
        ImageButton submit;
    }

    private final Comparator<Feedback> FeedbackComparator = new Comparator<Feedback>() {
        @Override
        public int compare(Feedback t0, Feedback t1) {
            return t0.get("title").compareToIgnoreCase(t1.get("title"));
        }
    };

    private void load() {
        File root = FileSystemUtils
                .getItem(UserFeedbackCollector.FEEDBACK_FOLDER);
        if (!IOProviderFactory.isDirectory(root)) {
            if (!IOProviderFactory.mkdir(root)) {
                Log.w(TAG, "error making the directory: " + root);
            }
        }

        final File[] files = root.listFiles();

        final String callsign = prefs.getString("locationCallsign", "");

        if (files != null) {
            for (File f : files) {
                Feedback feedback = new Feedback(f, callsign);
                if (feedback.load()) {
                    feedbackList.add(feedback);
                }
            }
        }

    }

    /**
     * The list of all the feedback
     * @return the list of currently developed feedback
     */
    public List<Feedback> getCurrentList() {
        return new ArrayList<>(feedbackList);
    }

    private String formatFileSize(long size) {
        if (size < 1024)
            return size + "B";
        else if (size < (1024 * 1024))
            return (size / 1024) + "KB";
        else
            return size / (1024 * 1024) + "MB";

    }

}
