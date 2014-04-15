var Billing = Class(Emitter, function (supr) {
  this.purchase = function (item) {
    FB.ui({
      method: 'pay',
      action: 'purchaseitem',
      product : CONFIG.browser.products[item.type],
      quantity: item.quantity,
      request_id: item.reciept
    }, this.callback);
  };
  this.callback = function (data) {
    if(data.error_code){
      if(typeof this.onFailure === 'function'){
        logger.info("BILLING : onFailure of Billing plugin");
        this.onFailure(data);
      } else {
        logger.info("BILLING : onFailure of Billing plugin is not defined");
      }
    } else if(data.status === 'completed'){
      if(typeof this.onPurchase === 'function'){
        logger.info("BILLING : < sync > onPurchase of Billing plugin");
        this.onPurchase(data, false);
      } else {
        logger.info("BILLING : < sync > onPurchase of Billing plugin is not defined");
      }
    } else if(data.status === 'initiated'){
      if(typeof this.onPurchase === 'function'){
        logger.info("BILLING : < async > onPurchase of Billing plugin");
        this.onPurchase(data, true);
      } else {
        logger.info("BILLING : < async > onPurchase of Billing plugin is not defined");
      }
    }
  }
});

exports = new Billing