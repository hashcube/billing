package com.tealeaf.plugin.plugins;

import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import com.tealeaf.EventQueue;
import com.tealeaf.TeaLeaf;
import com.tealeaf.logger;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.io.StringWriter;
import java.io.PrintWriter;

import com.tealeaf.plugin.IPlugin;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.util.Log;

import android.content.ComponentName;
import android.os.IBinder;
import android.app.PendingIntent;
import android.os.Build;

import com.tealeaf.EventQueue;
import com.tealeaf.event.*;


import com.amazon.device.iap.*;
import com.amazon.device.iap.model.*;

public class BillingPlugin implements IPlugin {
	Context _ctx = null;
	Activity _activity = null;
	String mService = null;
	ServiceConnection mServiceConn = null;
	public enum DeviceType {
		KINDLE, ANDROID
	}
	private DeviceType deviceIs = DeviceType.KINDLE;
	Object mServiceLock = new Object();
	static private final int BUY_REQUEST_CODE = 123450;
	String currentUserId = null;
	String currentMarketPlace = null;

	private class MyListener implements PurchasingListener {

		public MyListener() {
			super();
		}

		@Override
		public void onProductDataResponse(ProductDataResponse itemDataResponse) {
			switch(itemDataResponse.getRequestStatus()) {
				case SUCCESSFUL:
					for (final String sku : itemDataResponse.getUnavailableSkus()) {
						logger.log("{BillingAmazon}", "Unavailable SKU:" + sku);
					}

					final Map<String, Product> products = itemDataResponse.getProductData();
					final Map<String, String> localizedPrices = new HashMap<String, String>();
					for (final String key : products.keySet()) {
						Product product = products.get(key);
						logger.log("\n{BillingAmazon}", String.format("Product: %s  Type: %s  SKU: %s  Price: %s  Description: %s\n", product.getTitle(), product.getProductType(), product.getSku(), product.getPrice(), product.getDescription()));
						localizedPrices.put(product.getSku().split("\\.")[3], product.getPrice().getCurrency() + " " + product.getPrice().getValue());
					}
					EventQueue.pushEvent(new InfoEvent(localizedPrices));
					break;
				case FAILED:
					logger.log("{BillingAmazon}", "Failed to fetch product data");
					break;
			}
		}

		@Override
		public void onUserDataResponse(UserDataResponse response) {
			final UserDataResponse.RequestStatus status = response.getRequestStatus();
			switch (status) {
				case SUCCESSFUL:
					currentUserId = response.getUserData().getUserId();
					currentMarketPlace = response.getUserData().getMarketplace();
					logger.log("{BillingAmazon}", String.format("User: %s market: %s\n", currentUserId, currentMarketPlace));
					break ;

				case FAILED:
					logger.log("{BillingAmazon}", "Failed to fetch user data");
				case NOT_SUPPORTED:
    					logger.log("{BillingAmazon}", "This call is not supported");
					break ;
			}
		}

		@Override
		public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse purchaseUpdatesResponse) {}

		@Override
		public void onPurchaseResponse(PurchaseResponse purchaseResponse) {

			//Check purchaseResponse.getPurchaseRequestStatus();
			//If SUCCESSFUL, fulfill content;
			logger.log("{billing} Entering Amazon Kindle Billing Plugin Handler");
			try {
				String responseCode = purchaseResponse.getRequestStatus().toString();
				if(responseCode == "FAILED")
				{
					EventQueue.pushEvent(new PurchaseEvent(null, null, "cancel"));
					return;
				}
				Receipt receipt = purchaseResponse.getReceipt();
				String receiptID = receipt.getReceiptId();
				if (responseCode.equals("SUCCESSFUL"))
				{
					String shortSKU = receipt.getSku();
					shortSKU = shortSKU.substring(shortSKU.lastIndexOf(".") + 1);
					logger.log("{billing} Successfully purchased SKU: \""+ shortSKU+ " \"with token: " + receiptID);
					PurchasingService.notifyFulfillment(receiptID, FulfillmentResult.FULFILLED);
					EventQueue.pushEvent(new PurchaseEvent(shortSKU, receiptID, null));

				}
				else
				{
					if(responseCode.equals("ALREADY_ENTITLED"))
					{
						logger.log("{billing} WARNING: Already Entitled to the Goods with response code:", responseCode);
						PurchasingService.notifyFulfillment(receiptID, FulfillmentResult.FULFILLED);
					}
					else
					{
						logger.log("{billing} WARNING: Ignored because of ", responseCode);
						PurchasingService.notifyFulfillment(receiptID, FulfillmentResult.UNAVAILABLE);
					}
					EventQueue.pushEvent(new PurchaseEvent(null, null, responseCode));
				}
			} catch (Exception e) {
				logger.log("{billing} WARNING: Failed to parse purchase data:", e);
				e.printStackTrace();
				EventQueue.pushEvent(new PurchaseEvent(null, null, "failed"));
			}
		}
	}

	public class PurchaseEvent extends com.tealeaf.event.Event {
		String sku, token, failure;

		public PurchaseEvent(String sku, String token, String failure) {
			super("billingPurchase");
			this.sku = sku;
			this.token = token;
			this.failure = failure;
		}
	}

	public class ConsumeEvent extends com.tealeaf.event.Event {
		String token, failure, userid = currentUserId;

		public ConsumeEvent(String token, String failure) {
			super("billingConsume");
			this.token = token;
			this.failure = failure;
			this.userid = userid;
		}
	}

	public class OwnedEvent extends com.tealeaf.event.Event {
		ArrayList<String> skus, tokens;
		String failure;

		public OwnedEvent(ArrayList<String> skus, ArrayList<String> tokens, String failure) {
			super("billingOwned");
			this.skus = skus;
			this.tokens = tokens;
			this.failure = failure;
		}
	}

	public class ConnectedEvent extends com.tealeaf.event.Event {
		boolean connected;

		public ConnectedEvent(boolean connected) {
			super("billingConnected");
			this.connected = connected;
		}
	}

	public class InfoEvent extends com.tealeaf.event.Event {
		Map<String, String> data;

		public InfoEvent(Map<String, String> prices) {
			super("billingLocalizedPrices");
			this.data = prices;
		}
	}

	public BillingPlugin() {
	}

	public void onCreateApplication(Context applicationContext) {
		_ctx = applicationContext;

		mServiceConn = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {
				synchronized (mServiceLock) {
					mService = null;
				}

				EventQueue.pushEvent(new ConnectedEvent(false));
			}

			@Override
			public void onServiceConnected(ComponentName name,
				IBinder service) {
				synchronized (mServiceLock) {
					mService = null;
				}

				EventQueue.pushEvent(new ConnectedEvent(true));
			}
		};
	}

	public void onCreate(Activity activity, Bundle savedInstanceState) {
		logger.log("{billing} Installing listener");
		_activity = activity;
	}

	public void onResume() {
		PurchasingService.getUserData();
	}

	public void onStart() {
		final PackageManager packageManager = _ctx.getPackageManager();
		switch(deviceIs)
		{
			case KINDLE:
				logger.log("{billing} Switched to KINDLE ");
				try {
					PurchasingService.registerListener(_ctx, new MyListener());
				} catch (Exception e) {
					logger.log("{billing} WARNING: Failure in purchase init:", e);
					e.printStackTrace();
					StringWriter writer = new StringWriter();
					PrintWriter printWriter = new PrintWriter( writer );
					e.printStackTrace( printWriter );
					printWriter.flush();
					String stackTrace = writer.toString();
					logger.log("{billing} onstart stackTrace: "+stackTrace);
				}
				break;
			case ANDROID:
				logger.log("{billing} Switched to ANDROID");
				break;
				default:
				logger.log("{billing} Switched to ANDROID BY DEFAULT");
				deviceIs = DeviceType.ANDROID;
				break;
		}
	}

	public void onPause() {
	}

	public void onStop() {
	}

	public void onDestroy() {
		if (mServiceConn != null) {
			_ctx.unbindService(mServiceConn);
		}
	}

	public void isConnected(String jsonData) {
		synchronized (mServiceLock) {
			if (mService == null) {
				EventQueue.pushEvent(new ConnectedEvent(false));
			} else {
				EventQueue.pushEvent(new ConnectedEvent(true));
			}
		}
	}

	public void purchaseForKindle(String jsonData) {
		String sku = null;
		String pkgName = _ctx.getPackageName();
		logger.log("{billing} In transaction purchase for Amazon Kindle");
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			sku = jsonObject.getString("sku");
			String fullSKU = pkgName+"."+sku;
			logger.log("{billing} Doing Billing for sku: "+fullSKU);
			String requestId = PurchasingService.purchase(fullSKU).toString();
		} catch (Exception e) {
			logger.log("{billing} WARNING: Failure in purchase:", e);
			e.printStackTrace();
			EventQueue.pushEvent(new PurchaseEvent(sku, null, "failed"));
		}
	}

	public void purchase(String jsonData) {
		if(deviceIs == DeviceType.KINDLE)
		{
			logger.log("{billing} Initiating purchase for Amazon Kindle");
			purchaseForKindle(jsonData);
			return;
		}
		boolean success = false;
		String sku = null;

		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			sku = jsonObject.getString("sku");

			logger.log("{billing} Purchasing:", sku);

			Bundle buyIntentBundle = null;

			synchronized (mServiceLock) {
				if (mService == null) {
					EventQueue.pushEvent(new PurchaseEvent(sku, null, "service"));
					return;
				}
			}

			// If unable to create bundle,
			if (buyIntentBundle == null || buyIntentBundle.getInt("RESPONSE_CODE", 1) != 0) {
				logger.log("{billing} WARNING: Unable to create intent bundle for sku", sku);
			} else {
				PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");

				if (pendingIntent == null) {
					logger.log("{billing} WARNING: Unable to create pending intent for sku", sku);
				} else {
					_activity.startIntentSenderForResult(pendingIntent.getIntentSender(),
						BUY_REQUEST_CODE, new Intent(), Integer.valueOf(0),
						Integer.valueOf(0), Integer.valueOf(0));
					success = true;
				}
			}
		} catch (Exception e) {
			logger.log("{billing} WARNING: Failure in purchase:", e);
			e.printStackTrace();
		}

		if (!success && sku != null) {
			EventQueue.pushEvent(new PurchaseEvent(sku, null, "failed"));
		}
	}

	public void consume(String jsonData) {
		String token = null;

		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String TOKEN = jsonObject.getString("token");
			token = TOKEN;

			if(deviceIs == DeviceType.KINDLE)
			{
				logger.log("{billing} Consuming:", TOKEN);
				logger.log("{billing} Consume suceeded:", TOKEN);
				EventQueue.pushEvent(new ConsumeEvent(TOKEN, null));
				return;
			}

			synchronized (mServiceLock) {
				if (mService == null) {
					EventQueue.pushEvent(new ConsumeEvent(TOKEN, "service"));
					return;
				}
			}

			logger.log("{billing} Consuming:", TOKEN);

			new Thread() {
				public void run() {
					try {
						logger.log("{billing} Consuming from thread:", TOKEN);
						int response = 1;
						synchronized (mServiceLock) {
							if (mService == null) {
								EventQueue.pushEvent(new ConsumeEvent(TOKEN, "service"));
								return;
							}
						}

						if (response != 0) {
							logger.log("{billing} Consume failed:", TOKEN, "for reason:", response);
							EventQueue.pushEvent(new ConsumeEvent(TOKEN, "cancel"));
						} else {
							logger.log("{billing} Consume suceeded:", TOKEN);
							EventQueue.pushEvent(new ConsumeEvent(TOKEN, null));
						}
					} catch (Exception e) {
						logger.log("{billing} WARNING: Failure in consume:", e);
						e.printStackTrace();
						EventQueue.pushEvent(new ConsumeEvent(TOKEN, "failed"));
					}
				}
			}.start();
		} catch (Exception e) {
			logger.log("{billing} WARNING: Failure in consume:", e);
			e.printStackTrace();
			EventQueue.pushEvent(new ConsumeEvent(token, "failed"));
		}
	}

	public void getPurchaseRequestStatus(String jsonData) {
		ArrayList<String> skus = new ArrayList<String>();
		ArrayList<String> tokens = new ArrayList<String>();
		boolean success = false;
		logger.log("{billing}=======getPurchase: "+jsonData);
		try {
			logger.log("{billing} Getting prior purchases");

			Bundle ownedItems = null;

			synchronized (mServiceLock) {
				if (mService == null) {
					EventQueue.pushEvent(new OwnedEvent(null, null, "service"));
					return;
				}
			}

			// If unable to create bundle,
			int responseCode = ownedItems.getInt("RESPONSE_CODE", 1);
			if (responseCode != 0) {
				logger.log("{billing} WARNING: Failure to create owned items bundle:", responseCode);
				EventQueue.pushEvent(new OwnedEvent(null, null, "failed"));
			} else {
				ArrayList ownedSkus =
				ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
				ArrayList purchaseDataList =
				ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
				for (int i = 0; i < ownedSkus.size(); ++i) {
					//String signature = signatureList.get(i);
					String sku = (String)ownedSkus.get(i);
					String purchaseData = (String)purchaseDataList.get(i);

					JSONObject json = new JSONObject(purchaseData);
					String token = json.getString("purchaseToken");
					logger.log("{billing}====== token: " + token);
					// TODO: Provide purchase data
					// TODO: Verify signatures

					if (sku != null && token != null) {
						skus.add(sku);
						tokens.add(token);
					}
				}

				// TODO: Use continuationToken to retrieve > 700 items
				EventQueue.pushEvent(new OwnedEvent(skus, tokens, null));
			}
		} catch (Exception e) {
			logger.log("{billing} WARNING: Failure in getPurchases:", e);
			e.printStackTrace();
			EventQueue.pushEvent(new OwnedEvent(null, null, "failed"));
		}
	}

	private String getResponseCode(Intent data) {
		try {
			Bundle bundle = data.getExtras();

			int responseCode = bundle.getInt("RESPONSE_CODE");

			switch (responseCode) {
				case 0:
					return "ok";
				case 1:
					return "cancel";
				case 2:
					return "service";
				case 3:
					return "billing unavailable";
				case 4:
					return "item unavailable";
				case 5:
					return "invalid arguments provided to API";
				case 6:
					return "fatal error in API";
				case 7:
					return "already owned";
				case 8:
					return "item not owned";
			}
		} catch (Exception e) {
		}

		return "unknown error";
	}

	public void onActivityResult(Integer request, Integer resultCode, Intent data) {
		if (request == BUY_REQUEST_CODE) {
			try {
				String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
				String sku = null;
				String responseCode = this.getResponseCode(data);

				if (purchaseData == null) {
					logger.log("{billing} WARNING: Ignored null purchase data with result code:", resultCode, "and response code:", responseCode);
					EventQueue.pushEvent(new PurchaseEvent(null, null, responseCode));
				} else {
					JSONObject jo = new JSONObject(purchaseData);
					sku = jo.getString("productId");
					logger.log("{billing}==========onReturn: "+jo.toString());
					if (sku == null) {
						logger.log("{billing} WARNING: Malformed purchase json");
					} else {
						switch (resultCode) {
							case Activity.RESULT_OK:
								String token = jo.getString("purchaseToken");

								logger.log("{billing} Successfully purchased SKU:", sku);
								EventQueue.pushEvent(new PurchaseEvent(sku, token, null));
								break;
								case Activity.RESULT_CANCELED:
								logger.log("{billing} Purchase canceled for SKU:", sku, "with result code:", resultCode, "and response code:", responseCode);
								EventQueue.pushEvent(new PurchaseEvent(sku, null, responseCode));
								break;
							default:
								logger.log("{billing} Unexpected result code for SKU:", sku, "with result code:", resultCode, "and response code:", responseCode);
								EventQueue.pushEvent(new PurchaseEvent(sku, null, responseCode));
						}
					}
				}
			} catch (JSONException e) {
				logger.log("{billing} WARNING: Failed to parse purchase data:", e);
				e.printStackTrace();
				EventQueue.pushEvent(new PurchaseEvent(null, null, "failed"));
			}
		}
	}

	public void requestLocalizedPrices(String jsonData) {
		final Set<String> productSkus = new HashSet<String>();
		try {
			JSONObject json = new JSONObject(jsonData);
			JSONArray data = json.getJSONArray("skus");
			int length = data.length();
			for (int i = 0; i < length; i++) {
				productSkus.add(_ctx.getPackageName() + "." + data.getString(i));
			}
			PurchasingService.getProductData(productSkus);
		} catch (JSONException ex) {
			ex.printStackTrace();
		}
	}

	public void onNewIntent(Intent intent) {
	}

	public void setInstallReferrer(String referrer) {
	}

	public void logError(String error) {
	}

	public boolean consumeOnBackPressed() {
		return true;
	}

	public void onBackPressed() {
	}
}
