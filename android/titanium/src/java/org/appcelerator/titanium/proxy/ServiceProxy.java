/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseService;
import org.appcelerator.titanium.TiBaseService.TiServiceBinder;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

@Kroll.proxy
public class ServiceProxy extends KrollProxy
{
	private Service service;
	private boolean forBoundServices;
	private int serviceInstanceId;
	private IntentProxy intentProxy;
	private ServiceConnection serviceConnection = null; // Set only if the service is started via bindService as opposed to startService
	private static final boolean DBG = TiConfig.LOGD;
	private static final String LCAT = "TiServiceProxy";
	
	public ServiceProxy()
	{
	}

	/**
	 * For when creating a service proxy directly, for later binding using bindService()
	 */
	public ServiceProxy(IntentProxy intentProxy)
	{
		setIntent(intentProxy);
		forBoundServices = true;
	}

	/**
	 * For when a service started via startService() creates a proxy when it starts running
	 */
	public ServiceProxy(Service service, Intent intent, int serviceInstanceId)
	{
		this.service = service;
		setIntent(intent);
		this.serviceInstanceId = serviceInstanceId;
	}

	@Kroll.getProperty @Kroll.method
	public int getServiceInstanceId()
	{
		return serviceInstanceId;
	}

	@Kroll.getProperty @Kroll.method
	public IntentProxy getIntent()
	{
		return intentProxy;
	}

	public void setIntent(Intent intent)
	{
		setIntent(new IntentProxy(intent));
	}

	public void setIntent(IntentProxy intentProxy)
	{
		this.intentProxy = intentProxy;
	}

	@Kroll.method
	public void start()
	{
		if (!forBoundServices) {
			Log.w(LCAT, "Only services created via Ti.Android.createService can be started via the start() command. Ignoring start() request.");
			return;
		}
		bindAndInvokeService();
	}

	@Kroll.method
	public void stop()
	{
		if (DBG) {
			Log.d(LCAT, "stop");
		}
		if (!forBoundServices) {
			if (DBG) {
				Log.d(LCAT, "stop via stopService");
			}
			service.stopSelf();
		} else {
			unbindService();
		}
		
	}

	private void bindAndInvokeService()
	{
		serviceConnection = new ServiceConnection()
		{
			public void onServiceDisconnected(ComponentName name) {}

			public void onServiceConnected(ComponentName name, IBinder service)
			{
				if (service instanceof TiServiceBinder) {
					TiServiceBinder binder = (TiServiceBinder) service;
					ServiceProxy proxy =  ServiceProxy.this;
					TiBaseService tiService =(TiBaseService) binder.getService();
					proxy.serviceInstanceId = tiService.nextServiceInstanceId();
					if (DBG) {
						Log.d(LCAT, tiService.getClass().getSimpleName() + " service successfully bound");
					}
					proxy.invokeBoundService(tiService);
				}
			}
		};

		TiApplication.getInstance().bindService(this.getIntent().getIntent(), serviceConnection, Context.BIND_AUTO_CREATE);
	}

	private void unbindService()
	{
		Context context = TiApplication.getInstance();
		if (context == null) {
			Log.w(LCAT, "Cannot unbind service.  tiContext.getTiApp() returned null");
			return;
		}

		if (service instanceof TiBaseService) {
			((TiBaseService) service).unbindProxy(this);
		}

		if (DBG) {
			Log.d(LCAT, "Unbinding service");
		}
		context.unbindService(serviceConnection);
		serviceConnection = null;
	}
	
	protected void invokeBoundService(Service boundService)
	{
		this.service = boundService;
		if (!(boundService instanceof TiBaseService)) {
			Log.w(LCAT, "Service " + boundService.getClass().getSimpleName() + " is not a Ti Service.  Cannot start directly.");
			return;
		}

		TiBaseService tiService = (TiBaseService) boundService;
		if (DBG) {
			Log.d(LCAT, "Calling tiService.start for this proxy instance");
		}

		tiService.start(this);
	}

	@Override
	public void release()
	{
		super.release();
		if (DBG) {
			Log.d(LCAT, "Nullifying wrapped service");
		}
		this.service = null;
	}
}
