/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.media.jfxmediaimpl;

import com.sun.media.jfxmedia.Media;
import com.sun.media.jfxmedia.locator.Locator;
import com.sun.media.jfxmedia.track.Track;
import com.sun.media.jfxmediaimpl.platform.Platform;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base {@link Media} implementation class. Platforms will extend this base class.
 *
 * TODO: Nuke this class, it's not really necessary. At most we should have an impl interface
 */
public abstract class NativeMedia extends Media {
    protected final Lock markerLock = new ReentrantLock();
    protected final Lock listenerLock = new ReentrantLock();
    protected Map<String,Double> markersByName;
    protected NavigableMap<Double,String> markersByTime;
    protected WeakHashMap<MarkerStateListener,Boolean> markerListeners;

    /**
     * Constructor.
     *
     * @param locator The location of the media.
     * @throws IllegalArgumentException if <code>locator</code> is
     * <code>null</code>.
     */
    protected NativeMedia(Locator locator) {
        super(locator);
    }

    // For comparison and player creation, *must* be implemented
    public abstract Platform getPlatform();

    // --- Tracks: widen access to allow calls from NativeMediaPlayer.

    @Override
    public void addTrack(Track track) {
        super.addTrack(track);
    }

    // --- Markers ---

    public void addMarker(String markerName, double presentationTime){
        if (markerName == null) {
            throw new IllegalArgumentException("markerName == null!");
        } else if (presentationTime < 0.0) {
            throw new IllegalArgumentException("presentationTime < 0");
        }

        markerLock.lock();
        try {
            if(markersByName == null) {
                markersByName = new HashMap<String,Double>();
                markersByTime = new TreeMap<Double,String>();
            }
            markersByName.put(markerName, presentationTime);
            markersByTime.put(presentationTime, markerName);
        } finally {
            markerLock.unlock();
        }

        fireMarkerStateEvent(true);
    }

    public Map<String, Double> getMarkers() {
        Map<String, Double> markers = null;
        markerLock.lock();
        try {
            if(markersByName != null && !markersByName.isEmpty()) {
                markers = Collections.unmodifiableMap(markersByName);
            }
        } finally {
            markerLock.unlock();
        }
        return markers;
    }

    public double removeMarker(String markerName) {
        if (markerName == null) {
            throw new IllegalArgumentException("markerName == null!");
        }

        double time = -1.0;
        boolean hasMarkers = false;

        markerLock.lock();
        try {
            if (markersByName.containsKey(markerName)) {
                time = markersByName.get(markerName);
                markersByName.remove(markerName);
                markersByTime.remove(time);
                hasMarkers = (markersByName.size() > 0);
            }
        } finally {
            markerLock.unlock();
        }

        fireMarkerStateEvent(hasMarkers);

        return time;
    }

    public void removeAllMarkers() {
        markerLock.lock();
        try {
            markersByName.clear();
            markersByTime.clear();
        } finally {
            markerLock.unlock();
        }

        fireMarkerStateEvent(false);
    }

    public abstract void dispose();

    Map.Entry<Double, String> getNextMarker(double time, boolean inclusive) {
        Map.Entry<Double, String> entry = null;
        markerLock.lock();
        try {
            if (markersByTime != null) {
                if (inclusive) {
                    entry = markersByTime.ceilingEntry(time);
                } else {
                    entry = markersByTime.higherEntry(time);
                }
            }
        } finally {
            markerLock.unlock();
        }
        return entry;
    }

    void addMarkerStateListener(MarkerStateListener listener) {
        if (listener != null) {
            listenerLock.lock();
            try {
                if (markerListeners == null) {
                    markerListeners = new WeakHashMap<MarkerStateListener,Boolean>();
                }
                markerListeners.put(listener, Boolean.TRUE);
            } finally {
                listenerLock.unlock();
            }
        }
    }


    void removeMarkerStateListener(MarkerStateListener listener) {
        if (listener != null) {
            listenerLock.lock();
            try {
                if (markerListeners != null) {
                    markerListeners.remove(listener);
                }
            } finally {
                listenerLock.unlock();
            }
        }
    }

    void fireMarkerStateEvent(boolean hasMarkers) {
        listenerLock.lock();
        try {
            if (markerListeners != null && !markerListeners.isEmpty()) {
                for(MarkerStateListener listener : markerListeners.keySet()) {
                    if(listener != null) {
                        listener.markerStateChanged(hasMarkers);
                    }
                }
            }
        } finally {
            listenerLock.unlock();
        }
    }
}
