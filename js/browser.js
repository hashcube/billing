import event.Emitter as Emitter;
import fbinstant as fbinstant;

var Billing = Class(Emitter, function (supr) {
  this.purchase = function (product_id, access_token, payload) {
    var product = {
      productID: product_id
    };

    if (payload) {
      product.developerPayload = payload;
    }

    if (GC.app.payments_ready) {
      fbinstant.purchaseAsync(product)
        .then(bind(this, function (purchase) {
          this.afterPurchase(purchase, access_token);
        }))
        .catch(bind(this, function (e) {
          this.onFailure(product_id);
        }));
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
          this.consumeItem(item, purchase_token, access_token);
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

  this.onPurchase = function () {};
  this.onFailure = function () {};
});

exports = new Billing();
