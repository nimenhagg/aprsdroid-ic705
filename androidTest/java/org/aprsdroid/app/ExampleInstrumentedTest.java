package org.aprsdroid.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("me.nimenhagg.aprsdroidic705mod", appContext.getPackageName());
    }

    @Test
    public void privateBroadcastTargetsInstalledApp() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = AprsService.privateIntent(appContext, AprsService.MESSAGE);

        assertEquals(appContext.getPackageName(), intent.getPackage());
        assertEquals(AprsService.MESSAGE, intent.getAction());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void aprsServiceIsNotExported() throws PackageManager.NameNotFoundException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ComponentName component = new ComponentName(appContext, AprsService.class);
        ServiceInfo service = appContext.getPackageManager().getServiceInfo(component, 0);

        assertFalse(service.exported);
    }
}
