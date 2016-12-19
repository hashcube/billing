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
import java.util.Set;
import java.util.Iterator;

import com.tealeaf.plugin.IPlugin;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.util.Log;

import android.content.ComponentName;
import android.os.IBinder;
import android.app.PendingIntent;

import com.tealeaf.EventQueue;
import com.tealeaf.event.*;

// Security
import android.util.Base64;
import android.text.TextUtils;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import com.android.vending.billing.IInAppBillingService;

public class BillingPlugin implements IPlugin {
	Context _ctx = null;
	Activity _activity = null;
	IInAppBillingService mService = null;
	ServiceConnection mServiceConn = null;
	Object mServiceLock = new Object();
	static private final int BUY_REQUEST_CODE = 12340; //Max value can be 65535
	private String mSignature;

	public class PurchaseEvent extends com.tealeaf.event.Event {
		String sku, token, failure, receiptString;

		public PurchaseEvent(String sku, String token, String failure, String receiptString) {
			super("billingPurchase");
			this.sku = sku;
			this.token = token;
			this.failure = failure;
			this.receiptString = receiptString;
		}
	}

	public class ConsumeEvent extends com.tealeaf.event.Event {
		String token, failure, receiptString;

		public ConsumeEvent(String token, String failure, String receiptString) {
			super("billingConsume");
			this.token = token;
			this.failure = failure;
			this.receiptString = receiptString;
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

	public static class Security {

		private static final String KEY_FACTORY_ALGORITHM = "RSA";
		private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

		/**
		 * Verifies that the data was signed with the given signature, and returns
		 * the verified purchase. The data is in JSON format and signed
		 * with a private key. The data also contains the {@link PurchaseState}
		 * and product ID of the purchase.
		 * @param base64PublicKey the base64-encoded public key to use for verifying.
		 * @param signedData the signed JSON string (signed, not encrypted)
		 * @param signature the signature for the data, signed with the private key
		 */
		public static boolean verifyPurchase(String base64PublicKey, String signedData, String signature) {
			PublicKey key;

			if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(base64PublicKey) ||
					TextUtils.isEmpty(signature)) {
				logger.log("{billing} Purchase verification failed: missing data.");
				return false;
			}

			try {
				key = Security.generatePublicKey(base64PublicKey);
			} catch(Exception e) {
				return false;
			}
			return Security.verify(key, signedData, signature);
		}

		/**
		 * Generates a PublicKey instance from a string containing the
		 * Base64-encoded public key.
		 *
		 * @param encodedPublicKey Base64-encoded public key
		 * @throws IllegalArgumentException if encodedPublicKey is invalid
		 */
		public static PublicKey generatePublicKey(String encodedPublicKey) {
			try {
				byte[] decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT);
				KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
				return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			} catch (InvalidKeySpecException e) {
				logger.log("{billing} Invalid key specification.");
				throw new IllegalArgumentException(e);
			}
		}

		/**
		 * Verifies that the signature from the server matches the computed
		 * signature on the data.  Returns true if the data is correctly signed.
		 *
		 * @param publicKey public key associated with the developer account
		 * @param signedData signed data from server
		 * @param signature server signature
		 * @return true if the data and signature match
		 */
		public static boolean verify(PublicKey publicKey, String signedData, String signature) {
			byte[] signatureBytes;
			try {
				signatureBytes = Base64.decode(signature, Base64.DEFAULT);
			} catch (IllegalArgumentException e) {
				logger.log("{billing} Base64 decoding failed.");
				return false;
			}
			try {
				Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
				sig.initVerify(publicKey);
				sig.update(signedData.getBytes());
				if (!sig.verify(signatureBytes)) {
					logger.log("{billing} Signature verification failed.");
					return false;
				}
				return true;
			} catch (NoSuchAlgorithmException e) {
				logger.log("{billing} NoSuchAlgorithmException.");
			} catch (InvalidKeyException e) {
				logger.log("{billing} Invalid key specification.");
			} catch (SignatureException e) {
				logger.log("{billing} Signature exception.");
			}
			return false;
		}
	}

	private void consumeAsync(final String TOKEN, final String RECEIPT) {
		logger.log("{billing} Consuming:", TOKEN);

		new Thread() {
			public void run() {
				try {
					logger.log("{billing} Consuming from thread:", TOKEN);

					int response = 1;

					synchronized (mServiceLock) {
						if (mService == null) {
							EventQueue.pushEvent(new ConsumeEvent(TOKEN, "service", null));
							return;
						}

						response = mService.consumePurchase(3, _ctx.getPackageName(), TOKEN);
					}

					if (response != 0) {
						logger.log("{billing} Consume failed:", TOKEN, "for reason:", response);
						EventQueue.pushEvent(new ConsumeEvent(TOKEN, "cancel", null));
					} else {
						logger.log("{billing} Consume suceeded:", TOKEN);
						EventQueue.pushEvent(new ConsumeEvent(TOKEN, null, RECEIPT));
					}
				} catch (Exception e) {
					logger.log("{billing} WARNING: Failure in consume:", e);
					e.printStackTrace();
					EventQueue.pushEvent(new ConsumeEvent(TOKEN, "failed", null));
				}
			}
		}.start();
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
						mService = IInAppBillingService.Stub.asInterface(service);
					}

					EventQueue.pushEvent(new ConnectedEvent(true));
				}
		};
	}

	public void onCreate(Activity activity, Bundle savedInstanceState) {
		logger.log("{billing} Installing listener");

		_activity = activity;

		PackageManager manager = activity.getPackageManager();
		try {
			Bundle meta = manager.getApplicationInfo(activity.getPackageName(), PackageManager.GET_META_DATA).metaData;
			if (meta != null) {
				mSignature = meta.get("BILLING_SIGNATURE").toString();
			}
		} catch (Exception e) {
			android.util.Log.d("EXCEPTION", "" + e.getMessage());
		}

		Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
		_ctx.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
	}

	public void onResume() {
	}

	public void onStart() {
	}

	public void onPause() {
	}

	public void onStop() {
	}

	public void onFirstRun() {
	}

	public void onRenderPause() {
	}

	public void onRenderResume() {
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

	public void requestLocalizedPrices(String jsonData) {
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			JSONArray skus = jsonObject.getJSONArray("skus");
			ArrayList<String> skuList = new ArrayList<String> ();
			int length = skus.length();

			for (int i = 0; i < length; i++) {
				skuList.add(skus.getString(i));
			}
			final Bundle querySkus = new Bundle();
			querySkus.putStringArrayList("ITEM_ID_LIST", skuList);
			// TODO: Add client side verification with signatures
			new Thread() {
				public void run() {
					try {
						Bundle skuDetails = mService.getSkuDetails(3, _ctx.getPackageName(),
								"inapp", querySkus);
						Map<String, String> map = new HashMap<String, String>();
						int response = skuDetails.getInt("RESPONSE_CODE");
						if (response == 0) {
							ArrayList<String> responseList = skuDetails.getStringArrayList("DETAILS_LIST");

							for (String thisResponse : responseList) {
								JSONObject object = new JSONObject(thisResponse);
								map.put(object.getString("productId"), object.getString("price"));
							}
							EventQueue.pushEvent(new InfoEvent(map));
						}
					} catch(Exception e) {
						logger.log("{billing} WARNING: Failure in getting data:", e);
						e.printStackTrace();
					}
				}
			}.start();
		} catch (Exception e) {
			logger.log("{billing} WARNING: Failure in parsing JSON :", e);
			e.printStackTrace();
		}
	}

	public void purchase(String jsonData) {
		boolean success = false;
		String sku = null;

		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			sku = jsonObject.getString("sku");

			logger.log("{billing} Purchasing:", sku);

			Bundle buyIntentBundle = null;

			synchronized (mServiceLock) {
				if (mService == null) {
					EventQueue.pushEvent(new PurchaseEvent(sku, null, "service", null));
					return;
				}

				// TODO: Add additional security with extra field ("1")

				buyIntentBundle = mService.getBuyIntent(3, _ctx.getPackageName(),
						sku, "inapp", "1");
			}

			// If unable to create bundle,
			if (buyIntentBundle == null) {
				logger.log("{billing} WARNING: Unable to create intent bundle for sku", sku);
			} else if (buyIntentBundle.getInt("RESPONSE_CODE", 1) == 0) {
				PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");

				if (pendingIntent == null) {
					logger.log("{billing} WARNING: Unable to create pending intent for sku", sku);
				} else {
					_activity.startIntentSenderForResult(pendingIntent.getIntentSender(),
							BUY_REQUEST_CODE, new Intent(), Integer.valueOf(0),
							Integer.valueOf(0), Integer.valueOf(0));
					success = true;
				}
			} else if (getPurchases("{}")) {
				// When we try to purchase, if reponse code from the store is not success,
				// try checking pending purchases.
				// This is a temporary fix to avoid the condition where creating a new intent
				// breaks the purchase flow.
				success = true;
			}
		} catch (Exception e) {
			logger.log("{billing} WARNING: Failure in purchase:", e);
			e.printStackTrace();
		}

		if (!success && sku != null) {
			EventQueue.pushEvent(new PurchaseEvent(sku, null, "failed", null));
		}
	}

	public void consume(String jsonData) {
		String token = null;

		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String TOKEN = jsonObject.getString("token");
			final String RECEIPT = jsonObject.getString("receiptString");
			token = TOKEN;

			synchronized (mServiceLock) {
				if (mService == null) {
					EventQueue.pushEvent(new ConsumeEvent(TOKEN, "service", null));
					return;
				}
			}

			consumeAsync(TOKEN, RECEIPT);
		} catch (Exception e) {
			logger.log("{billing} WARNING: Failure in consume:", e);
			e.printStackTrace();
			EventQueue.pushEvent(new ConsumeEvent(token, "failed", null));
		}
	}

	public boolean getPurchases(String jsonData) {
		ArrayList<String> skus = new ArrayList<String>();
		ArrayList<String> tokens = new ArrayList<String>();
		boolean success = false;

		try {
			logger.log("{billing} Getting prior purchases");

			Bundle ownedItems = null;

			synchronized (mServiceLock) {
				if (mService == null) {
					EventQueue.pushEvent(new OwnedEvent(null, null, "service"));
					return success;
				}

				ownedItems = mService.getPurchases(3, _ctx.getPackageName(), "inapp", null);
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
				//ArrayList signatureList =
				//	ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE");
				//String continuationToken =
				//	ownedItems.getString("INAPP_CONTINUATION_TOKEN");

				for (int i = 0; i < ownedSkus.size(); ++i) {
					success = true;
					//String signature = signatureList.get(i);
					String sku = (String)ownedSkus.get(i);
					String purchaseData = (String)purchaseDataList.get(i);

					JSONObject json = new JSONObject(purchaseData);
					String token = json.getString("purchaseToken");

					// TODO: Provide purchase data

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
		return success;
	}

	private String getResponseCode(Intent data) {
		int responseCode = data.getIntExtra("RESPONSE_CODE", 0);

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

		return "unknown error";
	}

	public void onActivityResult(Integer request, Integer resultCode, Intent data) {
		if (data == null) {
			// If there is any onwned purchases, don't send failed event.
			// We assume that last purchase is the one pending.
			if (!getPurchases("{}")) {
				EventQueue.pushEvent(new PurchaseEvent(null, null, "failed", null));
				return;
			}
		}

		if (request == BUY_REQUEST_CODE) {
			String responseCode = this.getResponseCode(data);
			String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
			String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

			logger.log("{billing} responsecode", responseCode);

			if (resultCode == Activity.RESULT_OK && responseCode == "ok") {
				logger.log("{billing} purchase data", purchaseData);

				try {
					JSONObject jo = new JSONObject(purchaseData);
					String sku = jo.getString("productId");
					String token = jo.getString("purchaseToken");
					String receiptString;
					JSONObject receiptStringCombo = new JSONObject();
					receiptStringCombo.put("purchaseData", purchaseData);
					receiptStringCombo.put("dataSignature", dataSignature);
					receiptString = receiptStringCombo.toString();

					if (!Security.verifyPurchase(mSignature, purchaseData, dataSignature)) {
						logger.log("{billing} Purchase signature verification FAILED for sku " + sku);
						EventQueue.pushEvent(new PurchaseEvent(sku, null, "failed", null));
						consumeAsync(token, receiptString);
					} else {
						EventQueue.pushEvent(new PurchaseEvent(sku, token, null, receiptString));
					}
				}
				catch (JSONException e) {
					e.printStackTrace();
					EventQueue.pushEvent(new PurchaseEvent(null, null, "failed", null));
				}
			} else {
				EventQueue.pushEvent(new PurchaseEvent(null, null, "failed", null));
			}
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

