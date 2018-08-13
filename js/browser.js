import event.Emitter as Emitter;

var Billing = Class(Emitter, function (supr) {
  this.purchase = function (item, access_token) {
    FB.ui({
      method: 'pay',
      action: 'purchaseiap',
      product_id: item
    }, bind(this, function (data) {
      this.afterPurchase(data, item, access_token);
    }));
  };

  this.afterPurchase = function (data, item, access_token) {
    if (!data || data.error_code) {
      this.onFailure(item);
    } else if (data.status === 'completed') {
      this.consumeItem(item, data.purchase_token, access_token);
    }
  };

  this.consumeItem = function (item, purchase_token, access_token) {
    localStorage.setItem("purchasedItem", {
      item: item,
      purchase_token: purchase_token,
      access_token: access_token
    });
    FB.api('/' + purchase_token + '/consume',
      'post', {
        access_token: access_token
      }, bind(this, function (response) {
        if (response && response.success) {
          this.creditConsumed(item, purchase_token);
        } else {
          // retry consuming if failed
          setTimeout(bind(this, function () {
            this.consumeItem(item, purchase_token, access_token);
          }, 3000));
        }
    }));
  };

  this.creditConsumed = function (item, token) {
    if (token) {
      this.onPurchase(item, null, token);
      localStorage.removeItem("purchasedItem");
    }
  };

  this.onPurchase = function () {};
  this.onFailure = function () {};
});

exports = new Billing();
