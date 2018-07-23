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
      if (typeof this.onFailure === 'function') {
        logger.info("BILLING : onFailure of Billing plugin");
        this.onFailure(item);
      } else {
        logger.info("BILLING : onFailure of Billing plugin is not defined");
      }
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
          if (typeof this.onPurchase === 'function') {
            this.onPurchase(item, null, purchase_token);
            logger.info("BILLING : onPurchase of Billing plugin");
          } else {
            logger.info("BILLING : onPurchase of Billing plugin is not defined");
          }
        }
    }));
  };
});

exports = new Billing();
