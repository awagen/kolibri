// ref: https://stackoverflow.com/questions/4810841/pretty-print-json-using-javascript
function syntaxHighlight(json) {
    json = baseJsonFormatting(json)
    json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
        let cls = 'number';
        if (/^"/.test(match)) {
            if (/:$/.test(match)) {
                cls = 'key';
            } else {
                cls = 'string';
            }
        } else if (/true|false/.test(match)) {
            cls = 'boolean';
        } else if (/null/.test(match)) {
            cls = 'null';
        }
        return '<span class="' + cls + '">' + match + '</span>';
    });
}

function baseJsonFormatting(json) {
    if (typeof json != 'string') {
        json = JSON.stringify(json, undefined, 2);
    }
    return json
}

function stringifyObj(obj){
    return JSON.stringify(obj, null, '\t')
}

function objectToJsonStringAndSyntaxHighlight(obj) {
    return syntaxHighlight(stringifyObj(obj))
}

export {syntaxHighlight, stringifyObj, objectToJsonStringAndSyntaxHighlight, baseJsonFormatting}