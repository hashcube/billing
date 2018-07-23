import event.Emitter as Emitter;

var Billing = Class(Emitter, function (supr) {
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
      this.consumeItem(item, data.purchase_token, access_token);
    }
  };

  this.consumeItem = function (item, purchase_token, access_token) {
    FB.api('/' + purchase_token + '/consume',
      'post', {
        access_token: access_token
      }, bind(this, function (response) {
        if (response && response.success) {
          this.onPurchase(item, null, purchase_token);
        } else {
          this.onFailure(item);
        }
    }));
  };

  this.onPurchase = function () {};
  this.onFailure = function () {};
});

exports = new Billing();
