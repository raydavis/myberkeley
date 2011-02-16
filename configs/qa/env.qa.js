define(["/dev/configuration/config.js"], function(config) {
    config.isDev = false;
	
    // Tracking and statistics
    config.Tracking = {
        GoogleAnalytics: {
            WebPropertyID : "UA-20616179-3"
        }
    };
	
});
