    var captureFG = {

        captureVideoFG : function(successCallback, errorCallback, options) {
            var exec = require('cordova/exec');

            var win = function(pluginResult) {

                var mediaFile = {};
                mediaFile.name = pluginResult[0].name;
                mediaFile.fullPath = pluginResult[0].fullPath;
                mediaFile.type = pluginResult[0].type;
                mediaFile.lastModifiedDate = pluginResult[0].lastModifiedDate;
                mediaFile.size = pluginResult[0].size;

                successCallback(mediaFile);
            };
            exec(win, errorCallback, "CaptureFG", "action", [ options ]);
        }

    }
    module.exports = captureFG;