package org.wheelmap.android.async;


import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.wheelmap.android.fragment.POIDetailFragment;
import org.wheelmap.android.modules.ICredentials;
import org.wheelmap.android.modules.UserCredentials;
import org.wheelmap.android.online.BuildConfig;
import org.wheelmap.android.online.R;

import android.app.Application;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;


public class UploadPhotoTask extends AsyncTask<File, Void, Boolean> {

    private static final String TAG = UploadPhotoTask.class.getSimpleName();

    ProgressDialog progress;
    long wmID;
    Application mContext;
    Fragment mFragment;
    Cursor mCursor;

    public UploadPhotoTask(Cursor cursor, Fragment fragment, Application context, ProgressDialog d, long wmID) {
        mContext = context;
        this.wmID = wmID;
        progress = d;
        mCursor = cursor;
        mFragment = fragment;

    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (progress != null && !progress.isShowing()) {
            progress.show();
        }
    }

    @Override
    protected Boolean doInBackground(File... params) {
        String server = BuildConfig.API_BASE_URL;
        ICredentials credentials = new UserCredentials(mContext);
        String url = server + "/api/nodes/" + wmID + "/photos?api_key=" + credentials.getApiKey();
        Log.d(TAG, url);

        File image = params[0];

        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(url);

        try {
            MultipartEntity entity = new MultipartEntity();
            entity.addPart("photo", new FileBody(image));
            httppost.setEntity(entity);
            HttpResponse response = httpclient.execute(httppost);
            String result = EntityUtils.toString(response.getEntity());
            Log.d(TAG, result + "");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getLocalizedMessage());
            return false;
        }

    }

    @Override
    protected void onPostExecute(Boolean b) {
        super.onPostExecute(b);

        if (progress != null) {
            progress.dismiss();
        }

        if (!b) {
            Toast.makeText(mContext, R.string.photo_upload_failed, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(mContext, R.string.photo_upload_successfully, Toast.LENGTH_LONG).show();
            if (mFragment != null && mFragment.isAdded() && mCursor != null && mCursor.getCount() > 0) {
                ((POIDetailFragment) mFragment).reloadData();
            }
        }
    }
}
