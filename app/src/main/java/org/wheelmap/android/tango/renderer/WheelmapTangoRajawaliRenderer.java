package org.wheelmap.android.tango.renderer;

import android.content.Context;

import java.util.Vector;

public class WheelmapTangoRajawaliRenderer extends TangoRajawaliRenderer {

    private WheelmapModeRenderer renderer;
    private Vector<Runnable> actions = new Vector<Runnable>();

    private WheelmapRajawaliObjectFactory objectFactory = new WheelmapRajawaliObjectFactory();

    public WheelmapTangoRajawaliRenderer(Context context) {
        super(context);
    }


    public WheelmapRajawaliObjectFactory getWheelmapRajawaliObjectFactory() {
        return objectFactory;
    }

    public void setWheelmapRajawaliObjectFactory(WheelmapRajawaliObjectFactory factory) {
        objectFactory = factory;
    }

    public void setModeRenderer(WheelmapModeRenderer newRenderer) {

        if (renderer != null) {
            renderer.clear();
        }

        renderer = newRenderer;
        if (renderer != null) {
            renderer.setRajawaliRenderer(this);
        }
    }

    public WheelmapModeRenderer getModeRenderere() {
        return renderer;
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {

        synchronized (this) {
            // invoke all actions
            if (actions.size() > 0) {
                for (Runnable action : actions) {
                    action.run();
                }
                actions.clear();
            }
            renderer.onRender(ellapsedRealtime, deltaTime);
        }

        super.onRender(ellapsedRealtime, deltaTime);
    }

    public synchronized void invokeAction(Runnable runnable) {
        actions.add(runnable);
    }
}