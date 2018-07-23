import event.Emitter as Emitter;

var Billing = Class(Emitter, function (supr) {
  var consumedItems = {};
  var purchasedItems = {};


  this.purchase = function (item, access_token) {
    FB.ui({
      method: 'pay',
      action: 'purchaseiap',
      product_id: item
    }, bind(this, function (data) {
      this.callback(data, item, access_token);
    }));
  };

  this.callback = function (data, item, access_token) {
    if (!data || data.error_code) {
      this.onFailure(item);
    } else if (data.status === 'completed') {
      purchasedItems[item] = 1;
      this.consumeItem(item, data.purchase_token, access_token);
    }
  };

  this.consumeItem = function (item, purchase_token, access_token) {
    if (purchasedItems[item]) {
      delete purchasedItems[item];
      consumedItems[item] = {
        token: access_token,
        receipt: null
      };
      FB.api('/' + purchase_token + '/consume',
        'post', {
          access_token: access_token
        }, bind(this, function (response) {
          if (response && response.success) {
            this.creditConsumed(item, purchase_token);
          } else {
            // retry consuming if failed
            setTimeout(bind(this, function () {
              this.creditConsumed(item, purchase_token);
            }, 3000));
          }
      }));
    }
  };

  this.creditConsumed = function (item, token) {
    if (token) {
      this.onPurchase(item, null, token);
    }
    delete consumedItems[item];
    localStorage.setItem("billingConsumed", JSON.stringify(consumedItems));
  };

  this.onPurchase = function () {};
  this.onFailure = function () {};
});

exports = new Billing();
