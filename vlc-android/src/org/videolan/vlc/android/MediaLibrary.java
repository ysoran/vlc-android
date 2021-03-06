/*****************************************************************************
 * MediaLibrary.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.android;

import java.io.File;
import java.io.FileFilter;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;

public class MediaLibrary {
    public final static String TAG = "VLC/MediaLibrary";

    protected static final int MEDIA_ITEMS_UPDATED = 100;

    private static MediaLibrary mInstance;
    private DatabaseManager mDBManager;
    private ArrayList<Media> mItemList;
    private ArrayList<Handler> mUpdateHandler;
    protected Context mContext;
    protected Thread mLoadingThread;

    private MediaLibrary(Context context) {
        mInstance = this;
        mContext = context;
        mItemList = new ArrayList<Media>();
        mUpdateHandler = new ArrayList<Handler>();
        mDBManager = DatabaseManager.getInstance();
    }

    public void loadMediaItems() {
        if (mLoadingThread == null || mLoadingThread.getState() == State.TERMINATED) {
            mLoadingThread = new Thread(mGetMediaItems);
            mLoadingThread.start();
        }
    }

    public static MediaLibrary getInstance(Context context) {
        if (mInstance == null)
            mInstance = new MediaLibrary(context);
        return mInstance;
    }

    public void addUpdateHandler(Handler handler) {
        mUpdateHandler.add(handler);
    }

    public void removeUpdateHandler(Handler handler) {
        mUpdateHandler.remove(handler);
    }

    public ArrayList<Media> getVideoItems() {
        ArrayList<Media> videoItems = new ArrayList<Media>();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getType() == Media.TYPE_VIDEO) {
                videoItems.add(item);
            }
        }
        return videoItems;
    }

    public ArrayList<Media> getAudioItems() {
        ArrayList<Media> audioItems = new ArrayList<Media>();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getType() == Media.TYPE_AUDIO) {
                audioItems.add(item);
            }
        }
        return audioItems;
    }

    public ArrayList<Media> getAudioItems(String name, int mode) {
        ArrayList<Media> audioItems = new ArrayList<Media>();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getType() == Media.TYPE_AUDIO) {

                boolean valid = false;
                switch (mode) {
                    case AudioBrowserActivity.MODE_ARTIST:
                        valid = name.equals(item.getArtist());
                        break;
                    case AudioBrowserActivity.MODE_ALBUM:
                        valid = name.equals(item.getAlbum());
                        break;
                    case AudioBrowserActivity.MODE_GENRE:
                        valid = name.equals(item.getGenre());
                        break;
                    default:
                        break;
                }
                if (valid)
                    audioItems.add(item);

            }
        }
        return audioItems;
    }

    public ArrayList<Media> getMediaItems() {
        return mItemList;
    }

    public Media getMediaItem(String path) {
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getPath().equals(path)) {
                return item;
            }
        }
        return null;
    }

    public ArrayList<Media> getMediaItems(List<String> pathList) {
        ArrayList<Media> items = new ArrayList<Media>();
        for (int i = 0; i < pathList.size(); i++) {
            Media item = getMediaItem(pathList.get(i));
            items.add(item);
        }
        return items;
    }

    private final Runnable mGetMediaItems = new Runnable() {

        private Stack<File> directorys = new Stack<File>();
        private MainActivity mMainActivity;

        public void run() {
            // Initialize variables
            mMainActivity = MainActivity.getInstance();
            Handler mainHandler = mMainActivity.mHandler;
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mMainActivity);

            String root = pref.getString("directories_root", null);

            // use the external storage as our default root directory (most often /mnt/sdcard)
            if (root == null) {
                root = Environment.getExternalStorageDirectory().getAbsolutePath();
            }

            // show progressbar in header
            mainHandler.sendEmptyMessage(MainActivity.SHOW_PROGRESSBAR);

            // get directories from database
            directorys.addAll(mDBManager.getMediaDirs());
            if (directorys.isEmpty())
                directorys.add(new File(root));

            // get all existing media items
            HashMap<String, Media> existingMedias = mDBManager.getMedias();

            // list of all added files
            HashSet<File> addedFiles = new HashSet<File>();

            // clear all old item
            mItemList.clear();

            MediaItemFilter mediaFileFilter = new MediaItemFilter();

            int count = 0;
            int total = 0;

            //first pass : count total files
            while (!directorys.isEmpty()) {
                File dir = directorys.pop();
                File[] f = null;
                if ((f = dir.listFiles(mediaFileFilter)) != null) {
                    for (int i = 0; i < f.length; i++) {
                        File file = f[i];
                        if (file.isFile()) {
                            total++;
                        } else if (file.isDirectory()) {
                            directorys.push(file);
                        }
                    }
                }
            }
            directorys.addAll(mDBManager.getMediaDirs());
            if (directorys.isEmpty())
                directorys.add(new File(root));

            //second pass : load Medias
            while (!directorys.isEmpty()) {
                File dir = directorys.pop();
                File[] f = null;
                if ((f = dir.listFiles(mediaFileFilter)) != null) {
                    for (int i = 0; i < f.length; i++) {
                        File file = f[i];

                        if (file.isFile()) {

                            MainActivity.sendTextInfo(mainHandler, file.getName(), count, total);
                            count++;

                            if (existingMedias.containsKey(file.getPath())) {
                                /** only add file if it is not already in the
                                 * list. eg. if user select an subfolder as well
                                 */
                                if (!addedFiles.contains(file)) {
                                    // get existing media item from database
                                    mItemList.add(existingMedias.get(file.getPath()));
                                    addedFiles.add(file);
                                }
                            } else {
                                // create new media item
                                mItemList.add(new Media(mContext, file));
                            }
                        } else if (file.isDirectory()) {
                            directorys.push(file);
                        }
                    }
                }
            }

            // update the video and audio activities
            for (int i = 0; i < mUpdateHandler.size(); i++) {
                Handler h = mUpdateHandler.get(i);
                h.sendEmptyMessage(MEDIA_ITEMS_UPDATED);
            }

            // remove file from database
            for (File file : addedFiles) {
                existingMedias.remove(file.getPath());
            }
            for (String path : existingMedias.keySet()) {
                mDBManager.removeMedia(path);
            }

            // hide progressbar in header
            MainActivity.clearTextInfo(mainHandler);
            mainHandler.sendEmptyMessage(MainActivity.HIDE_PROGRESSBAR);
        }
    };

    /**
     * Filters all irrelevant files
     */
    private class MediaItemFilter implements FileFilter {

        // FIXME: save extensions in external database
        private String[] extensions = Media.EXTENTIONS;

        public boolean accept(File f) {
            boolean accepted = false;
            if (!f.isHidden()) {
                if (f.isDirectory()) {
                    accepted = true;
                } else {
                    String fileName = f.getName().toLowerCase();
                    int dotIndex = fileName.lastIndexOf(".");
                    if (dotIndex != -1) {
                        String fileExt = fileName.substring(dotIndex);
                        accepted = Arrays.asList(extensions).contains(fileExt);
                    }
                }
            }
            return accepted;
        }
    }
}
