



function loadSnapAnim(params) {
    doSnapAjaxLoad(params.jsonfile, function(req) {
        if(req.readyState === 4 && req.status === 200) {
            json = JSON.parse(req.responseText);
            new SVGAnim(
                json,
                params.width,
                params.height,
                params.fps,
                { "elementId": params.elementId }
            );
        }
    });
}

function doSnapAjaxLoad(url, stateChangeCallback)
{
    let req = new XMLHttpRequest();
    req.open("GET", url, true);
    req.setRequestHeader("Content-type", "application/json");
    req.onreadystatechange = function() { stateChangeCallback(req) };
    req.send();
}
