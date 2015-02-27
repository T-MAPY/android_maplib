/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2015. NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplib.util;

import android.util.Log;
import com.nextgis.maplib.api.IJSONStore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.nextgis.maplib.util.Constants.NOT_FOUND;
import static com.nextgis.maplib.util.Constants.TAG;
import static com.nextgis.maplib.util.Constants.JSON_ID_KEY;
import static com.nextgis.maplib.util.Constants.JSON_CHANGES_KEY;

/**
 * A class to store changes in feature of vector layer
 */
public class ChangeFeatureItem implements IJSONStore
{
    public static final int TYPE_NEW     = 1 << 1;
    public static final int TYPE_CHANGED = 1 << 2;
    public static final int TYPE_DELETE  = 1 << 3;
    public static final int TYPE_ATTACH  = 1 << 4;

    protected static final String JSON_OPERATION_KEY = "operation";

    protected int mFeatureId;
    protected int mOperation;


    public class ChangeAttachItem
            implements IJSONStore
    {
        protected int mAttachId;
        protected int mOperation;


        public ChangeAttachItem(
                int attachId,
                int operation)
        {
            mAttachId = attachId;
            mOperation = operation;
        }


        public int getAttachId()
        {
            return mAttachId;
        }


        public int getOperation()
        {
            return mOperation;
        }


        public void setOperation(int operation)
        {
            mOperation = operation;
        }


        @Override
        public JSONObject toJSON()
                throws JSONException
        {
            JSONObject rootObject = new JSONObject();
            rootObject.put(JSON_ID_KEY, mAttachId);
            rootObject.put(JSON_OPERATION_KEY, mOperation);
            return rootObject;
        }


        @Override
        public void fromJSON(JSONObject jsonObject)
                throws JSONException
        {
            mAttachId = jsonObject.getInt(JSON_ID_KEY);
            mOperation = jsonObject.getInt(JSON_OPERATION_KEY);
        }
    }


    protected List<ChangeAttachItem> mAttachItems;


    public ChangeFeatureItem(
            int featureId,
            int operation)
    {
        mFeatureId = featureId;
        mOperation = operation;

        mAttachItems = new ArrayList<>();
    }


    public int getFeatureId()
    {
        return mFeatureId;
    }


    public List<ChangeAttachItem> getAttachItems()
    {
        return mAttachItems;
    }


    public int getOperation()
    {
        return mOperation;
    }


    public void setOperation(int operation)
    {
        mOperation = operation;
        if(!mAttachItems.isEmpty())
            mOperation |= TYPE_ATTACH;
    }


    @Override
    public JSONObject toJSON()
            throws JSONException
    {
        JSONObject rootObject = new JSONObject();
        rootObject.put(JSON_ID_KEY, mFeatureId);
        rootObject.put(JSON_OPERATION_KEY, mOperation);
        JSONArray changes = new JSONArray();
        for (ChangeAttachItem attachItem : mAttachItems) {
            changes.put(attachItem.toJSON());
        }
        rootObject.put(JSON_CHANGES_KEY, changes);
        return rootObject;
    }


    @Override
    public void fromJSON(JSONObject jsonObject)
            throws JSONException
    {
        mFeatureId = jsonObject.getInt(JSON_ID_KEY);
        mOperation = jsonObject.getInt(JSON_OPERATION_KEY);

        if (jsonObject.has(JSON_CHANGES_KEY)) {
            JSONArray array = jsonObject.getJSONArray(JSON_CHANGES_KEY);
            for (int i = 0; i < array.length(); i++) {
                JSONObject change = array.getJSONObject(i);
                ChangeAttachItem item = new ChangeAttachItem(0, 0);
                item.fromJSON(change);
                mAttachItems.add(item);
            }
        }
    }


    public void addAttachChange(
            String attachId,
            int operation)
    {
        if(0 == (mOperation & ChangeFeatureItem.TYPE_ATTACH))
            mOperation |= ChangeFeatureItem.TYPE_ATTACH;

        //1. if featureId == NOT_FOUND remove all changes and add this one
        if (attachId.equals("" + NOT_FOUND) && operation == ChangeFeatureItem.TYPE_DELETE) {
            mAttachItems.clear();
            mAttachItems.add(new ChangeAttachItem(NOT_FOUND, operation));
        } else {
            int id = Integer.parseInt(attachId);
            for (int i = 0; i < mAttachItems.size(); i++) {
                ChangeAttachItem item = mAttachItems.get(i);
                if (item.getAttachId() == id) {
                    //2. if featureId == some id and op is delete - remove and other operations
                    if (operation == ChangeFeatureItem.TYPE_DELETE) {
                        if (item.getOperation() == ChangeFeatureItem.TYPE_DELETE) {
                            return;
                        }
                        mAttachItems.remove(i);
                        i--;
                    }
                    //3. if featureId == some id and op is update and previous op was add or update - skip
                    else if (operation == ChangeFeatureItem.TYPE_CHANGED) {
                        if (item.getOperation() == ChangeFeatureItem.TYPE_CHANGED ||
                            item.getOperation() == ChangeFeatureItem.TYPE_NEW) {
                            return;
                        } else {
                            item.setOperation(operation);
                            return;
                        }
                    }
                    //4. if featureId == some id and op is add and value present - warning
                    else if (0 != (operation & ChangeFeatureItem.TYPE_NEW)) {
                        Log.w(TAG, "Something wrong. Should nether get here");
                        return;
                    }
                }
            }
            mAttachItems.add(new ChangeAttachItem(id, operation));
        }
    }
}
