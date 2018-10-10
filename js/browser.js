import event.Emitter as Emitter;
import fbinstant as fbinstant;
import util.underscore as _;

var Billing = Class(Emitter, function (supr) {
  this.purchase = function (product_id, access_token, payload) {
    var product = {
      productID: product_id
    };

    if (payload) {
      product.developerPayload = payload;
    }

    if (fbinstant.payments_ready) {
      fbinstant.purchaseAsync(product)
        .then(bind(this, function (purchase) {
          this.consumeItem(purchase, access_token);
        }))
        .catch(bind(this, function (e) {
          this.onFailure(product_id);
        }));
    } else {
      this.onFailure(product_id, true);
    }
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

  this.requestLocalizedPrices = function (products, cb) {
    var res = {}, id;

    if (!fbinstant.payments_ready) {
      cb(res);
      return;
    }

    fbinstant.getCatalogAsync()
      .then(function (catalog) {
        res['data'] = {};

        _.each(catalog, function (val) {
          if(_.contains(products, val['productID'])) {
            id = val['productID'];
            res['data'][id] = {};
            res['data'][id].currency_code = val['priceCurrencyCode'];
            res['data'][id].amount = val['price'];
          }
        });
        cb(res);
      })
      .catch(function (e){
        cb(res);
      });
  };

  this.onPurchase = function () {};
  this.onFailure = function () {};
});

exports = new Billing();
