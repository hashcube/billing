import event.Emitter as Emitter;
import fbinstant as fbinstant;
import quest.modules.util as util;

var Billing = Class(Emitter, function (supr) {
  this.purchase = function (product_id, access_token, payload) {
    var product = {
      productID: product_id
    },
    is_not_iOS = this.isNotiOSDevice();

    if (payload) {
      product.developerPayload = payload;
    }

    if (is_not_iOS || this.isPaymentsReady()) {
      fbinstant.purchaseAsync(product)
        .then(bind(this, function (purchase) {
          this.afterPurchase(purchase, access_token);
        }))
        .catch(bind(this, function (e) {
          this.onFailure(product_id);
        }));
    } else if (!is_not_iOS) {
        this.onFailure(product_id, true);
    } else {
        this.onFailure(product_id);
    }
  };

  this.afterPurchase = function (data, access_token) {
    this.consumeItem(data, access_token);
  };

  this.consumeItem = function (data, access_token) {
    var item = data.productID,
      purchase_token = data.purchaseToken;

    fbinstant.setDataAsync({purchasedItem: {
      item: item,
      purchase_token: purchase_token,
      access_token: access_token
    }})
      .then(bind(this, function () {
        return fbinstant.consumePurchaseAsync(purchase_token);
      }))
      .then(bind(this, function (){
        this.creditConsumed(data);
      }))
      .catch(bind(this, function(){
        setTimeout(bind(this, function () {
          this.consumeItem(data, access_token);
        }, 3000));
      }));
  };

  this.creditConsumed = function (data) {
    var item = data.productID,
      token = data.purchaseToken,
      reciept = JSON.stringify(data);

    if (token) {
      this.onPurchase(item, reciept, token);
      fbinstant.setDataAsync({purchasedItem: undefined})
        .catch(bind(this, function (){
          setTimeout(bind(this, function () {
            this.creditConsumed(data);
          }, 3000));
        }));
    }
  };

  this.isPaymentsReady = function () {
    if (GC.app.payments_ready) {
      return true;
    }
    return false;
  }

  this.isNotiOSDevice = function () {
    var device_type =  util.getDeviceInfo().type;
    return (device_type != 'IPhone'
      && device_type != 'IPad'
      && device_type != 'IPod');
  };

  this.onPurchase = function () {};
  this.onFailure = function () {};
});

exports = new Billing();
