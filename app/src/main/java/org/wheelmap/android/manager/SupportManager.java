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
package org.wheelmap.android.manager;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.preference.PreferenceManager;

import org.wheelmap.android.app.WheelmapApp;
import org.wheelmap.android.model.Extra;
import org.wheelmap.android.model.Extra.What;
import org.wheelmap.android.model.PrefKey;
import org.wheelmap.android.model.Support.CategoriesContent;
import org.wheelmap.android.model.Support.LastUpdateContent;
import org.wheelmap.android.model.Support.LocalesContent;
import org.wheelmap.android.model.Support.NodeTypesContent;
import org.wheelmap.android.model.WheelchairFilterState;
import org.wheelmap.android.net.MarkerIconExecutor;
import org.wheelmap.android.online.R;
import org.wheelmap.android.service.RestService;
import org.wheelmap.android.utils.DetachableResultReceiver;
import org.wheelmap.android.utils.GeoMath;
import org.wheelmap.android.utils.UtilsMisc;
import org.wheelmap.android.view.MyLayerDrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.akquinet.android.androlog.Log;
import de.greenrobot.event.EventBus;

public class SupportManager {

    private static final String TAG = SupportManager.class.getSimpleName();

    private Context mContext;

    private Map<Integer, NodeType> mNodeTypeLookup;
    private Map<Integer, NodeType> mNodeTypeLookupList;

    private Map<Integer, Category> mCategoryLookup;

    private Map<Integer, String> mCategoryIdentifierLookup = new HashMap<Integer, String>();

    private DetachableResultReceiver mStatusSender;

    private EventBus mBus;

    private NodeType mDefaultNodeType;
    private NodeType mDefaultNodeTypeList;

    private Category mDefaultCategory;

    private boolean mNeedsReloading;

    private final static long MILLISECS_PER_DAY = 1000 * 60 * 60 * 24;

    private final static long DATE_INTERVAL_FOR_UPDATE_IN_DAYS = 1;

    public final static String PREFS_SERVICE_LOCALE = "prefsServiceLocale";

    public final static String PREFS_KEY_UNIT_PREFERENCE = "prefsUnit";

    private boolean mUseAngloDistanceUnit;

    public final static int UNKNOWN_TYPE = 0;

    public static class AccessFilterAttributes {
        public final int titleStringId;

        public final int stringId;

        public final int settingsStringId;

        public final int drawableId;

        public final int colorId;

        public final String prefsKey;

        AccessFilterAttributes(int titleStringId, int stringId,
                               int settingsStringId, int drawableId, int colorId,
                               String prefsKey) {
            this.titleStringId = titleStringId;
            this.stringId = stringId;
            this.settingsStringId = settingsStringId;
            this.drawableId = drawableId;
            this.colorId = colorId;
            this.prefsKey = prefsKey;
        }

        public int getTitleStringId() {
            return titleStringId;
        }

        public int getStringId() {
            return stringId;
        }

        public int getSettingsStringId() {
            return settingsStringId;
        }

        public int getDrawableId() {
            return drawableId;
        }

        public int getColorId() {
            return colorId;
        }

        public String getPrefsKey() {
            return prefsKey;
        }
    }

    public final static Map<WheelchairFilterState, WheelchairAttributes> wsAttributes
            = new HashMap<WheelchairFilterState, WheelchairAttributes>();

    public static class WheelchairAttributes extends AccessFilterAttributes {

        WheelchairAttributes(int titleStringId, int stringId,
                             int settingsStringId, int drawableId, int colorId,
                             String prefsKey) {
            super(titleStringId, stringId, settingsStringId, drawableId, colorId, prefsKey);
        }
    }

    public final static Map<WheelchairFilterState, WheelchairToiletAttributes> wheelchairToiletAttributes
            = new HashMap<WheelchairFilterState, WheelchairToiletAttributes>();

    public static class WheelchairToiletAttributes extends AccessFilterAttributes {

        WheelchairToiletAttributes(int titleStringId, int stringId,
                                   int settingsStringId, int drawableId, int colorId,
                                   String prefsKey) {
            super(titleStringId, stringId, settingsStringId, drawableId, colorId, prefsKey);
        }
    }

    public static class NodeType {

        public NodeType(int id, String identifier, String localizedName,
                        int categoryId) {
            this.id = id;
            this.identifier = identifier;
            this.localizedName = localizedName;
            this.categoryId = categoryId;
        }

        public NodeType base;

        public String iconPath;

        private WeakHashMap<WheelchairFilterState, WeakReference<Drawable>> stateDrawables = new WeakHashMap<>();

        private Map<WheelchairFilterState, Drawable> defaults;

        public int id;

        public String identifier;

        public String localizedName;

        public int categoryId;

        public Drawable getStateDrawable(WheelchairFilterState state) {
            if (defaults != null) {
                return defaults.get(state);
            }
            if(!stateDrawables.containsKey(state) || stateDrawables.get(state).get() == null){
                 synchronized (this){
                     if(!stateDrawables.containsKey(state) || stateDrawables.get(state).get() == null){
                         Log.d(TAG, "load new: " + iconPath + " " + state);
                         Drawable drawable = WheelmapApp.getSupportManager().getStateDrawable(iconPath, false, base, state);
                         stateDrawables.put(state, new WeakReference<>(drawable));
                         return drawable;
                     }
                 }
            }
            if(!stateDrawables.containsKey(state) || stateDrawables.get(state).get() == null){
                 return base.getStateDrawable(state);
            }
            return stateDrawables.get(state).get();
        }


    }

    public static class NodeTypeComparator implements Comparator<NodeType> {

        @Override
        public int compare(NodeType n1, NodeType n2) {
            return n1.localizedName.compareTo(n2.localizedName);
        }
    }

    public static class Category {

        public Category(int id, String identifier, String localizedName) {
            this.id = id;
            this.identifier = identifier;
            this.localizedName = localizedName;
        }

        public int id;

        public String identifier;

        public String localizedName;
    }

    public static class CategoryComparator implements Comparator<Category> {

        @Override
        public int compare(Category c1, Category c2) {
            return c1.localizedName.compareTo(c2.localizedName);
        }
    }

    public static class DistanceUnitChangedEvent {

        public final boolean useAngloDistanceUnit;

        public DistanceUnitChangedEvent(boolean useAngloDistanceUnit) {
            this.useAngloDistanceUnit = useAngloDistanceUnit;
        }
    }

    public SupportManager(Context ctx) {
        mContext = ctx;

        mBus = EventBus.getDefault();

        mCategoryLookup = new HashMap<>();
        mNodeTypeLookup = new HashMap<>();
        mNodeTypeLookupList = new HashMap<>();

        mDefaultCategory = new Category(UNKNOWN_TYPE, "unknown",
                mContext.getString(R.string.support_category_unknown));
        mDefaultNodeType = new NodeType(UNKNOWN_TYPE, "unknown",
                mContext.getString(R.string.support_nodetype_unknown), 0);
        mDefaultNodeType.defaults = createDefaultDrawables();


        mDefaultNodeTypeList = new NodeType(UNKNOWN_TYPE, "unknown",
                mContext.getString(R.string.support_nodetype_unknown), 0);
        mDefaultNodeTypeList.defaults = createDefaultDrawables();

        mNeedsReloading = false;
        if (!(checkForLocales() && checkForCategories() && checkForNodeTypes())) {
            mNeedsReloading = true;
        } else {
            Log.i(TAG, "Loading lookup data");
            initLookup();
        }
        Log.i(TAG, "Loading lookup data");

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        mUseAngloDistanceUnit = prefs.getBoolean(PREFS_KEY_UNIT_PREFERENCE,
                false);
        mBus.postSticky(new DistanceUnitChangedEvent(mUseAngloDistanceUnit));
        GeoMath.useAngloDistanceUnit(mUseAngloDistanceUnit);
    }

    public void releaseReceiver() {
        if (mStatusSender != null) {
            mStatusSender.clearReceiver();
        }
    }

    public boolean needsReloading() {
        return mNeedsReloading;
    }

    private void initLookup() {
        initCategories();
        initNodeTypes();
    }

    public void reload(DetachableResultReceiver receiver) {
        mStatusSender = receiver;
        Intent localesIntent = new Intent(Intent.ACTION_SYNC, null, mContext,
                RestService.class);
        localesIntent.putExtra(Extra.WHAT, What.RETRIEVE_LOCALES);
        localesIntent.putExtra(Extra.STATUS_RECEIVER, mStatusSender);
        mContext.startService(localesIntent);
    }

    public void reloadStageTwo() {
        initLocales();
        retrieveCategories();
    }

    public void reloadStageThree() {
        initCategories();
        retrieveMarkerIcons();
    }

    public void reloadStageFour() {
        initNodeTypes();
        if (UtilsMisc.isTablet(mContext)) {
            mNeedsReloading = false;
        } else {
            retrieveTotalNodeCount();
        }
    }

    public void reloadMarkerIcon() {
        initMarkerIcon();
        retrieveNodeTypes();
    }

    public void reloadTotalNodeCount() {
        initTotalNodeCount();
        mNeedsReloading = false;
    }

    public void retrieveTotalNodeCount() {
        Log.d(TAG, "retrieveTotalNodeCount");
        Intent nodeCountIntent = new Intent(Intent.ACTION_SYNC, null, mContext,
                RestService.class);
        nodeCountIntent.putExtra(Extra.WHAT, What.RETRIEVE_TOTAL_NODE_COUNT);
        nodeCountIntent.putExtra(Extra.STATUS_RECEIVER, mStatusSender);
        mContext.startService(nodeCountIntent);
    }

    public void retrieveMarkerIcons() {
        Log.d(TAG, "retrieveMarkerIcons");
        Intent nodeCountIntent = new Intent(Intent.ACTION_SYNC, null, mContext,
                RestService.class);
        nodeCountIntent.putExtra(Extra.WHAT, What.RETRIEVE_MARKER_ICONS);
        nodeCountIntent.putExtra(Extra.STATUS_RECEIVER, mStatusSender);
        mContext.startService(nodeCountIntent);
    }

    public void initMarkerIcon() {
        //rest is loaded in initNodeType    #
        mDefaultNodeType.defaults = createDefaultDrawables();
    }

    public void initTotalNodeCount() {

    }

    public void retrieveCategories() {
        Log.d(TAG, "retrieveCategories");

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        String locale = prefs.getString(PREFS_SERVICE_LOCALE, "");

        Intent categoriesIntent = new Intent(Intent.ACTION_SYNC, null,
                mContext, RestService.class);
        categoriesIntent.putExtra(Extra.WHAT, What.RETRIEVE_CATEGORIES);
        categoriesIntent.putExtra(Extra.LOCALE, locale);
        categoriesIntent.putExtra(Extra.STATUS_RECEIVER, mStatusSender);
        mContext.startService(categoriesIntent);
    }

    public void retrieveNodeTypes() {
        Log.d(TAG, "retrieveNodeTypes");
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        String locale = prefs.getString(PREFS_SERVICE_LOCALE, "");

        Intent nodeTypesIntent = new Intent(Intent.ACTION_SYNC, null, mContext,
                RestService.class);
        nodeTypesIntent.putExtra(Extra.WHAT, What.RETRIEVE_NODETYPES);
        nodeTypesIntent.putExtra(Extra.LOCALE, locale);

        nodeTypesIntent.putExtra(Extra.STATUS_RECEIVER, mStatusSender);
        mContext.startService(nodeTypesIntent);

    }


    private boolean checkIfUpdateDurationPassed(int module) {
        Date date = LastUpdateContent.queryTimeStamp(mContext.getContentResolver(), module);
        if (date == null) {
            return true;
        }

        long now = System.currentTimeMillis();

        long days = (now - date.getTime()) / MILLISECS_PER_DAY;
        Log.d(TAG, "checkIfUpdateDurationPassed: days = " + days);

        if (days >= DATE_INTERVAL_FOR_UPDATE_IN_DAYS) {
            Log.d(TAG, "checkIfUpdateDurationPassed: interval passed - forcing update module = "
                    + module);
            return true;
        }

        return false;

    }

    private boolean checkForLocales() {
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = resolver.query(LocalesContent.CONTENT_URI,
                LocalesContent.PROJECTION, null, null, null);
        if (cursor == null) {
            return false;
        }

        boolean dbEmpty = cursor.getCount() == 0;
        cursor.close();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String prefsLocale = prefs.getString(PREFS_SERVICE_LOCALE, "");
        boolean prefsEmpty = prefsLocale.equals("");
        boolean updateDurationPassed = checkIfUpdateDurationPassed(LastUpdateContent.MODULE_LOCALE);

        String locale = mContext.getResources().getConfiguration().locale
                .getLanguage();

        if (dbEmpty || prefsEmpty || !locale.equals(prefsLocale) || updateDurationPassed) {
            Log.d(TAG, "dbEmpty = " + dbEmpty + " prefsLocale = " + prefsLocale
                    + " locale = " + locale + " updateDurationPassed = " + updateDurationPassed);
            return false;
        } else {
            return true;
        }
    }

    private boolean checkForCategories() {
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = resolver.query(CategoriesContent.CONTENT_URI,
                CategoriesContent.PROJECTION, null, null, null);
        if (cursor == null) {
            return false;
        }

        boolean dbFull = cursor.getCount() != 0;
        cursor.close();

        boolean updateDurationPassed = checkIfUpdateDurationPassed(
                LastUpdateContent.MODULE_CATEGORIES);

        if (dbFull && !updateDurationPassed) {
            return true;
        } else {
            return false;
        }
    }

    private boolean checkForNodeTypes() {
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = resolver.query(NodeTypesContent.CONTENT_URI,
                NodeTypesContent.PROJECTION, null, null, null);
        if (cursor == null) {
            return false;
        }

        boolean dbFull = cursor.getCount() != 0;
        cursor.close();
        boolean updateDurationPassed = checkIfUpdateDurationPassed(
                LastUpdateContent.MODULE_CATEGORIES);

        if (dbFull && !updateDurationPassed) {
            return true;
        } else {
            return false;
        }
    }

    public void initLocales() {
        Log.d(TAG, "SupportManager:initLocales");
        String locale = mContext.getResources().getConfiguration().locale
                .getLanguage();
        Log.d(TAG, "SupportManager: locale = " + locale);
        ContentResolver resolver = mContext.getContentResolver();
        String whereClause = "( " + LocalesContent.LOCALE_ID + " = ? )";
        String[] whereValues = {locale};

        Cursor cursor = resolver.query(LocalesContent.CONTENT_URI,
                LocalesContent.PROJECTION, whereClause, whereValues, null);
        if (cursor == null) {
            return;
        }

        String serviceLocale = null;
        if (cursor.getCount() == 1) {
            serviceLocale = locale;
        } else {
            serviceLocale = "en";
        }
        cursor.close();

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mContext);

        String storedLocale = prefs.getString(PREFS_SERVICE_LOCALE, "");
        if (storedLocale.equals("") || !storedLocale.equals(serviceLocale)) {
            prefs.edit().putString(PREFS_SERVICE_LOCALE, serviceLocale)
                    .commit();
        }
        Log.i(TAG, "SupportManager:initLocales: serviceLocale = " + serviceLocale);
    }

    public void initCategories() {
        Log.d(TAG, "SupportManager:initCategories");
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = resolver.query(CategoriesContent.CONTENT_URI,
                CategoriesContent.PROJECTION, null, null, null);
        if (cursor == null) {
            return;
        }

        cursor.moveToFirst();
        mCategoryLookup.clear();
        mCategoryIdentifierLookup.clear();

        while (!cursor.isAfterLast()) {
            int id = CategoriesContent.getCategoryId(cursor);
            String identifier = CategoriesContent.getIdentifier(cursor);
            String localizedName = CategoriesContent.getLocalizedName(cursor);
            mCategoryLookup.put(id, new Category(id, identifier,
                    localizedName));
            mCategoryIdentifierLookup.put(id, identifier);

            cursor.moveToNext();
        }

        cursor.close();
        Log.i(TAG, "Categories count = " + mCategoryLookup.size());
    }

    public void initNodeTypes() {

        // Log.d(TAG, "SupportManager:initNodeTypes");
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = resolver.query(NodeTypesContent.CONTENT_URI,
                NodeTypesContent.PROJECTION, null, null, null);
        if (cursor == null) {
            return;
        }

        cursor.moveToFirst();
        mNodeTypeLookup.clear();
        mNodeTypeLookupList.clear();

        while (!cursor.isAfterLast()) {
            int id = NodeTypesContent.getNodeTypeId(cursor);
            String identifier = NodeTypesContent.getIdentifier(cursor);
            String localizedName = CategoriesContent.getLocalizedName(cursor);
            int categoryId = NodeTypesContent.getCategoryId(cursor);
            String iconPath = NodeTypesContent.getIconURL(cursor);

            NodeType nodeType = new NodeType(id, identifier, localizedName,
                    categoryId);
            nodeType.iconPath = iconPath;
            nodeType.base = mDefaultNodeType;
            mNodeTypeLookup.put(id, nodeType);

            nodeType = new NodeType(id, identifier, localizedName,
                    categoryId);
            nodeType.iconPath = iconPath;
            nodeType.base = mDefaultNodeTypeList;
            mNodeTypeLookupList.put(id, nodeType);

            cursor.moveToNext();
        }

        cursor.close();
        Log.i(TAG, "NodeTypes count = " + mNodeTypeLookup.size());
    }

    private Map<WheelchairFilterState, Drawable> createDefaultDrawables() {

        try {
            Map<WheelchairFilterState, Drawable> lookupMap = new HashMap<WheelchairFilterState, Drawable>();

            lookupMap.put(WheelchairFilterState.UNKNOWN,
                    mContext.getResources().getDrawable(R.drawable.marker_unknown));

            lookupMap.put(WheelchairFilterState.LIMITED,
                    mContext.getResources().getDrawable(R.drawable.marker_limited));

            lookupMap.put(WheelchairFilterState.NO,
                    mContext.getResources().getDrawable(R.drawable.marker_no));

            lookupMap.put(WheelchairFilterState.YES,
                    mContext.getResources().getDrawable(R.drawable.marker_yes));
            return lookupMap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    Drawable getStateDrawable(String assetPathPattern,
                              boolean fileNotFoundIsFatal, NodeType defaultNodes, WheelchairFilterState state) {

        String path = String.format(assetPathPattern, state.toString().toLowerCase());
        path = path.replace(".png", "@2x.png");
        Drawable drawable;
        try {
            InputStream is = null;
            if (MarkerIconExecutor.markerIconsDownloaded()) {
                File dir = MarkerIconExecutor.getMarkerPath(mContext);
                File asset = new File(dir + "/" + path);
                is = new FileInputStream(asset);
            }
            drawable = Drawable.createFromStream(is, null);
            Drawable bg = defaultNodes.getStateDrawable(state);

            float density = mContext.getResources().getDisplayMetrics().density;

            Drawable[] layers = {bg, drawable};
            LayerDrawable layerDrawable = new MyLayerDrawable(layers);
            layerDrawable.setLayerInset(1, (int) (6 * density), (int) (5 * density), (int) (6 * density), (int) (12 * density));
            drawable = layerDrawable;

            is.close();
        } catch (IOException e) {
            if (e instanceof FileNotFoundException && fileNotFoundIsFatal) {
                throw new IllegalStateException(
                        "createDrawableLookup: This shouldnt happen. Asset " + path
                                + " could not be found.");
            }
            Log.w(TAG, "Error in createDrawableLookup. Assigning fallback. ", e);
            drawable = mDefaultNodeType.getStateDrawable(state);
        } catch (OutOfMemoryError ofe) {
            System.gc();
            return null;
        }
        return drawable;
    }

    public Drawable getDefaultOverlayDrawable() {
        return mDefaultNodeType.getStateDrawable(WheelchairFilterState.UNKNOWN);
    }

    public void cleanReferences() {
        // Log.d(TAG, "clearing callbacks for mDefaultNodeType ");
        cleanReferences(mDefaultNodeType.stateDrawables);
        cleanReferences(mDefaultNodeTypeList.stateDrawables);

        for (Integer nodeTypeId : mNodeTypeLookup.keySet()) {
            NodeType nodeType = mNodeTypeLookup.get(nodeTypeId);
            // Log.d(TAG, "clearing callbacks for " + nodeType.identifier);
            cleanReferences(nodeType.stateDrawables);
        }

        for (Integer nodeTypeId : mNodeTypeLookupList.keySet()) {
            NodeType nodeType = mNodeTypeLookupList.get(nodeTypeId);
            // Log.d(TAG, "clearing callbacks for " + nodeType.identifier);
            cleanReferences(nodeType.stateDrawables);
        }
    }

    public void cleanReferences(Map<WheelchairFilterState, WeakReference<Drawable>> lookupMap) {
        for (Map.Entry<WheelchairFilterState, WeakReference<Drawable>> entry : lookupMap
                .entrySet()) {
            Drawable value = entry.getValue().get();
            if (value != null) {
                value.setCallback(null);
            }
        }
    }

    public Category lookupCategory(int id) {
        if (mCategoryLookup.containsKey(id)) {
            return mCategoryLookup.get(id);
        } else {
            return mDefaultCategory;
        }
    }

    public NodeType lookupNodeType(int id) {
        if (mNodeTypeLookup.containsKey(id)) {
            return mNodeTypeLookup.get(id);
        } else {
            return mDefaultNodeType;
        }
    }

    public NodeType lookupNodeTypeList(int id) {
        if (mNodeTypeLookupList.containsKey(id)) {
            return mNodeTypeLookupList.get(id);
        } else {
            return mDefaultNodeTypeList;
        }
    }

    public List<Category> getCategoryList() {
        Set<Integer> keys = mCategoryLookup.keySet();
        List<Category> list = new ArrayList<Category>();
        for (Integer key : keys) {
            list.add(mCategoryLookup.get(key));
        }
        return list;
    }

    public List<NodeType> getNodeTypeListByCategory(int categoryId) {
        Set<Integer> keys = mNodeTypeLookup.keySet();
        List<NodeType> list = new ArrayList<NodeType>();
        for (Integer key : keys) {
            NodeType nodeType = mNodeTypeLookup.get(key);

            if (nodeType.categoryId == categoryId) {
                list.add(nodeType);
            }
        }
        return list;
    }

    private void insertContentValues(Uri contentUri, String[] projection,
                                     String whereClause, String[] whereValues, ContentValues values) {
        ContentResolver resolver = mContext.getContentResolver();
        Cursor c = resolver.query(contentUri, projection, whereClause,
                whereValues, null);
        if (c == null) {
            return;
        }

        int cursorCount = c.getCount();
        c.close();
        if (cursorCount == 0) {
            resolver.insert(contentUri, values);
        } else if (cursorCount == 1) {
            resolver.update(contentUri, values, whereClause, whereValues);
        } else {
            // do nothing, as more than one file would be updated
        }
    }

    private OnSharedPreferenceChangeListener prefsListener
            = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                                              String key) {
            Log.d(TAG, "onPreferencesChanged: key = " + key);

            if (key.equals(PREFS_KEY_UNIT_PREFERENCE)) {
                mUseAngloDistanceUnit = prefs.getBoolean(
                        PREFS_KEY_UNIT_PREFERENCE, false);
                GeoMath.useAngloDistanceUnit(mUseAngloDistanceUnit);

                mBus.postSticky(new DistanceUnitChangedEvent(mUseAngloDistanceUnit));
            }
        }
    };

    static {
        wsAttributes.put(WheelchairFilterState.YES, new WheelchairAttributes(
                R.string.ws_enabled_title, R.string.ws_enabled,
                R.string.settings_wheelchair_yes,
                R.drawable.marker_yes, R.color.wheel_enabled,
                PrefKey.WHEELCHAIR_STATE_YES));
        wsAttributes.put(WheelchairFilterState.LIMITED, new WheelchairAttributes(
                R.string.ws_limited_title, R.string.ws_limited,
                R.string.settings_wheelchair_limited,
                R.drawable.marker_limited, R.color.wheel_limited,
                PrefKey.WHEELCHAIR_STATE_LIMITED));
        wsAttributes.put(WheelchairFilterState.NO, new WheelchairAttributes(
                R.string.ws_disabled_title, R.string.ws_disabled,
                R.string.settings_wheelchair_no,
                R.drawable.marker_no, R.color.wheel_disabled,
                PrefKey.WHEELCHAIR_STATE_NO));
        wsAttributes.put(WheelchairFilterState.UNKNOWN, new WheelchairAttributes(
                R.string.ws_unknown_title, R.string.ws_unknown,
                R.string.settings_wheelchair_unknown,
                R.drawable.marker_unknown, R.color.wheel_unknown,
                PrefKey.WHEELCHAIR_STATE_UNKNOWN));
    }

    static {
        wheelchairToiletAttributes.put(WheelchairFilterState.TOILET_YES, new WheelchairToiletAttributes(
                R.string.ws_enabled_title_toilet, R.string.ws_enabled_toilet,
                R.string.settings_wheelchair_yes,
                R.drawable.marker_yes, R.color.wheel_enabled,
                PrefKey.WHEELCHAIR_TOILET_STATE_YES));
        wheelchairToiletAttributes.put(WheelchairFilterState.TOILET_NO, new WheelchairToiletAttributes(
                R.string.ws_disabled_title_toilet, R.string.ws_disabled_toilet,
                R.string.settings_wheelchair_no,
                R.drawable.marker_no, R.color.wheel_disabled,
                PrefKey.WHEELCHAIR_TOILET_STATE_NO));
        wheelchairToiletAttributes.put(WheelchairFilterState.TOILET_UNKNOWN, new WheelchairToiletAttributes(
                R.string.ws_unknown_title_toilet, R.string.ws_unknown_toilet,
                R.string.settings_wheelchair_unknown,
                R.drawable.marker_unknown, R.color.wheel_unknown,
                PrefKey.WHEELCHAIR_TOILET_STATE_UNKNOWN));
    }
}
