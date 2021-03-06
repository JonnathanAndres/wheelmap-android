/*
 * #%L
 * Wheelmap - App
 * %%
 * Copyright (C) 2011 - 2012 Michal Harakal - Michael Kroez - Sozialhelden e.V.
 * %%
 * Wheelmap App based on the Wheelmap Service by Sozialhelden e.V.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wheelmap.android.activity;

import org.mapsforge.android.maps.GeoPoint;
import org.wheelmap.android.fragment.ErrorDialogFragment;
import org.wheelmap.android.fragment.ErrorDialogFragment.OnErrorDialogListener;
import org.wheelmap.android.fragment.POIDetailFragment;
import org.wheelmap.android.fragment.POIDetailFragment.OnPOIDetailListener;
import org.wheelmap.android.fragment.WheelchairAccessibilityStateFragment;
import org.wheelmap.android.fragment.WheelchairToiletStateFragment;
import org.wheelmap.android.model.Extra;
import org.wheelmap.android.model.PrepareDatabaseHelper;
import org.wheelmap.android.model.WheelchairFilterState;
import org.wheelmap.android.model.Wheelmap.POIs;
import org.wheelmap.android.online.R;
import org.wheelmap.android.service.RestService;
import org.wheelmap.android.service.RestServiceException;
import org.wheelmap.android.service.RestServiceHelper;
import org.wheelmap.android.utils.DetachableResultReceiver;
import org.wheelmap.android.utils.DetachableResultReceiver.Receiver;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.view.MenuItem;
import android.view.Window;

import de.akquinet.android.androlog.Log;

public class POIDetailActivity extends MapActivity implements
        OnPOIDetailListener, OnErrorDialogListener, Receiver {

    private final static String TAG = POIDetailActivity.class.getSimpleName();

    // Definition of the one requestCode we use for receiving resuls.
    static final private int SELECT_WHEELCHAIRSTATE = 0;

    POIDetailFragment mFragment;

    private DetachableResultReceiver mReceiver;

    private String wmID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        setSupportProgressBarIndeterminateVisibility(false);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FragmentManager fm = getSupportFragmentManager();
        mFragment = (POIDetailFragment) fm
                .findFragmentByTag(POIDetailFragment.TAG);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (getIntent() != null) {
            executeIntent(getIntent());
            setIntent(null);
        }
    }

    private void executeIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        // check if this intent is started via custom scheme link
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri == null) {
                Log.d(TAG, "uri has no data - cant extract wmID");
                showErrorMessage(getString(R.string.error_noid_title),
                        getString(R.string.error_noid_message));
                return;
            }

            wmID = uri.getLastPathSegment();
            try {
                Long.parseLong(wmID);
            } catch (NumberFormatException e) {
                Log.e(TAG, " wmID = " + wmID, e);
                finish();
                return;
            }

            showDetailForWmId(wmID);
            return;
        }

        Long poiId = intent.getLongExtra(Extra.POI_ID, Extra.ID_UNKNOWN);
        showDetailFragment(poiId);
        setIntent(null);
    }

    private void showDetailForWmId(String wmId) {
        long poiId = PrepareDatabaseHelper.getRowIdForWMId(
                getContentResolver(), wmId, POIs.TAG_RETRIEVED);
        if (poiId != Extra.ID_UNKNOWN) {
            long copyPoiId = PrepareDatabaseHelper.createCopyIfNotExists(
                    getContentResolver(), poiId, false);
            showDetailFragment(copyPoiId);
            return;
        }

        mReceiver = new DetachableResultReceiver(new Handler());
        mReceiver.setReceiver(this);
        RestServiceHelper.retrieveNode(this, wmId, mReceiver);
    }

    private void showDetailFragment(long id) {
        if (id == Extra.ID_UNKNOWN) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        mFragment = POIDetailFragment.newInstance(id);

        fm.beginTransaction()
                .replace(android.R.id.content, mFragment, POIDetailFragment.TAG)
                .commit();
    }

    @Override
    public void onEditWheelchairState(WheelchairFilterState wState) {

        // Start the activity whose result we want to retrieve. The
        // result will come back with request code GET_CODE.
        Intent intent = new Intent(this, WheelchairStateActivity.class);
        intent.putExtra(Extra.WHEELCHAIR_STATE, wState.getId());
        startActivityForResult(intent, SELECT_WHEELCHAIRSTATE);

    }

    @Override
    public void onEditWheelchairToiletState(WheelchairFilterState wState) {

        // Start the activity whose result we want to retrieve. The
        // result will come back with request code GET_CODE.
        Intent intent = new Intent(this, WheelchairStateActivity.class);
        intent.putExtra(Extra.WHEELCHAIR_TOILET_STATE, wState.getId());
        startActivityForResult(intent, SELECT_WHEELCHAIRSTATE);

    }

    /**
     * This method is called when the sending activity has finished, with the result it supplied.
     *
     * @param requestCode The original request code as given to startActivity().
     * @param resultCode  From sending activity as per setResult().
     * @param data        From sending activity as per setResult().
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_WHEELCHAIRSTATE && resultCode == RESULT_OK && data != null) {

            long poiID = mFragment.getPoiId();

            if (data.hasExtra(WheelchairAccessibilityStateFragment.TAG)) {
                WheelchairFilterState state = WheelchairFilterState
                        .valueOf(data.getIntExtra(WheelchairAccessibilityStateFragment.TAG, Extra.UNKNOWN));
                if (state != null) {
                    updateDatabase(poiID, POIs.WHEELCHAIR, state);
                }
            } else if (data.hasExtra(WheelchairToiletStateFragment.TAG)) {
                WheelchairFilterState state = WheelchairFilterState
                        .valueOf(data.getIntExtra(WheelchairToiletStateFragment.TAG, Extra.UNKNOWN));
                if (state != null) {
                    updateDatabase(poiID, POIs.WHEELCHAIR_TOILET, state);
                }
            } else {
                return;
            }
            RestServiceHelper.executeUpdateServer(this, null);
        }
    }

    private void updateDatabase(long id, String poiColumnName, WheelchairFilterState state) {
        if (id == Extra.ID_UNKNOWN || state == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(poiColumnName, state.getId());
        values.put(POIs.DIRTY, POIs.DIRTY_STATE);

        PrepareDatabaseHelper.editCopy(getContentResolver(), id, values);
    }

    @Override
    public void onShowLargeMapAt(GeoPoint point) {
        Intent intent = new Intent(this, MainSinglePaneActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra(Extra.SELECTED_TAB, MyTabListener.TAB_MAP);
        intent.putExtra(Extra.CENTER_MAP, true);
        intent.putExtra(Extra.LATITUDE, point.getLatitude());
        intent.putExtra(Extra.LONGITUDE, point.getLongitude());
        intent.putExtra(Extra.REQUEST, true);
        startActivity(intent);
    }

    @Override
    public void dismissDetailView() {
        //not used
        finish();
    }

    @Override
    public void onEdit(long poiId, int focus) {
        Intent intent = new Intent(this, POIDetailEditableActivity.class);
        intent.putExtra(Extra.POI_ID, poiId);
        intent.putExtra("Focus", focus);
        startActivity(intent);
    }

    public void showErrorMessage(String title, String message) {
        FragmentManager fm = getSupportFragmentManager();
        ErrorDialogFragment errorDialog = ErrorDialogFragment.newInstance(
                title, message, Extra.UNKNOWN);
        if (errorDialog == null) {
            return;
        }

        errorDialog.show(fm, ErrorDialogFragment.TAG);
    }

    public void showErrorDialog(RestServiceException e) {
        FragmentManager fm = getSupportFragmentManager();
        ErrorDialogFragment errorDialog = ErrorDialogFragment.newInstance(e,
                Extra.UNKNOWN);
        if (errorDialog == null) {
            return;
        }

        errorDialog.show(fm, ErrorDialogFragment.TAG);
    }

    /**
     * {@inheritDoc}
     */
    public void onReceiveResult(int resultCode, Bundle resultData) {
        Log.d(TAG, "onReceiveResult in list resultCode = " + resultCode);
        switch (resultCode) {
            case RestService.STATUS_RUNNING: {
                setSupportProgressBarIndeterminateVisibility(true);
                break;
            }
            case RestService.STATUS_FINISHED: {
                long id = PrepareDatabaseHelper.getRowIdForWMId(
                        getContentResolver(), wmID, POIs.TAG_COPY);
                setSupportProgressBarIndeterminateVisibility(false);
                showDetailFragment(id);
                break;
            }
            case RestService.STATUS_ERROR: {
                setSupportProgressBarIndeterminateVisibility(false);
                final RestServiceException e = resultData
                        .getParcelable(Extra.EXCEPTION);
                showErrorDialog(e);
                break;
            }
        }
    }

    @Override
    public void onErrorDialogClose(int id) {
        finish();
    }
}
