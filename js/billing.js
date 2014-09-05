import device;

if(device.name = 'browser'){
	import .browser as pluginImpl;
} else {
	import .native as pluginImpl
}
exports = pluginImpl;
