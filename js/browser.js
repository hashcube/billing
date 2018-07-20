import event.Emitter as Emitter;

var Billing = Class(Emitter, function (supr) {
  this.purchase = function (item) {
	FB.ui({
	  method: 'pay',
	  action: 'purchaseiap',
	  product_id: item,
	  quantity: item.quantity
	}, bind(this, function (data) {
	    this.callback(data, item);
	  }
	));
  };

  this.callback = function (data, item) {
    if (!data || data.error_code) {
	  if (typeof this.onFailure === 'function') {
	  	logger.info("BILLING : onFailure of Billing plugin");
	  	this.onFailure(item);
	  } else {
	  	logger.info("BILLING : onFailure of Billing plugin is not defined");
	  }
    } else if (data.status === 'completed') {
		if (typeof this.onPurchase === 'function') {
		  logger.info("BILLING : < sync > onPurchase of Billing plugin");
		  this.onPurchase(item, false);
		} else {
		  logger.info("BILLING : < sync > onPurchase of Billing plugin is not defined");
		}
    } else if (data.status === 'initiated') {
	  if (typeof this.onPurchase === 'function') {
	    logger.info("BILLING : < async > onPurchase of Billing plugin");
	  	this.onPurchase(item, true);
	  } else {
	  	logger.info("BILLING : < async > onPurchase of Billing plugin is not defined");
	  }
	}
  };
});

exports = new Billing();
