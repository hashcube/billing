#import "PluginManager.h"
#import <StoreKit/StoreKit.h>

@interface BillingPlugin : GCPlugin<SKPaymentTransactionObserver, SKProductsRequestDelegate>

@property (nonatomic, retain) NSMutableDictionary *purchases;
@property (nonatomic, retain) NSString *bundleID;
@property (nonatomic, retain) NSMutableDictionary *productDetails;
@property (nonatomic, retain) SKPayment *storeOrigin;

- (void) paymentQueue:(SKPaymentQueue *)queue updatedTransactions:(NSArray *)transactions;

- (void) requestPurchase:(NSString *)productIdentifier;

- (void) isConnected:(NSDictionary *)jsonObject;
- (void) purchase:(NSDictionary *)jsonObject;
- (void) consume:(NSDictionary *)jsonObject;
- (void) getPurchases:(NSDictionary *)jsonObject;
- (void) requestLocalizedPrices: (NSDictionary *)jsonObject;

@end
