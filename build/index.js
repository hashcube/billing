/**
 * Adds facebook sdk for browser build iff facebook plugin is not installed
 */
exports.onBeforeBuild = function (common, project, buildOpts, onFinish) {
   var isPresentFB = CONFIG.addons.indexOf('facebook');
  if (buildOpts.isSimulated && isPresentFB === -1) {
    buildOpts.headHTML.push(
        '<style>',
        '.FB_UI_Dialog { max-width: 100% !important; max-height: 90% !important }',
        '</style>'
      )
  }

  if (buildOpts.bodyHTML) {
    if(isPresentFB === -1){
      buildOpts.bodyHTML.push(
        '<div id="fb-root"></div>',
        // Load the SDK asynchronously
        '<script>',
          '(function(d){',
          'var js, id = "facebook-jssdk", ref = d.getElementsByTagName("script")[0];',
          'if (d.getElementById(id)) {return;}',
          'js = d.createElement("script"); js.id = id; js.async = true;',
          'js.src = "//connect.facebook.net/en_US/all.js";',
          'ref.parentNode.insertBefore(js, ref);',
          '}(document));',
        '</script>'
      );
    }
  }

  onFinish();
};