## Implementation of facebook payments

add following to > manifest.json
````json
...,
"browser":{
	...,
	"products": {
		"{{item_name}}": "opengraph url",
		...
	}
},
...
````


import billing addon first

````javascript
	import plugins.billing.billing as billing
````

To purchase an item [note product of facebook will be obtained from manifest.json file based on item.type]
````javascript
	billing.purchase(item)
	item = {
		type: type of item,
		quantity: no of items you want to buy,
		reciept : tracking data for facebook
	}
````
Confirm purchase using
````javascript
	billing.onPurchase(data, async){
		// your code here
	}
````
> @param 'async' tells you whether the payment is synchronus or asynchronus

> data contains
	> payment_id , currency, amount, quantity, signed_request and status

Handle failures using
````javascript
	billing.onFailure(data){
		// Your code here
	}
````

> data contains {error_code , error_message};
	> error_code : 1383013 = user cancelled request
